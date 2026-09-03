package com.cacompte.app.features.livematch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.model.ModifierID
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.Round
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreDetail
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.domain.rules.Standing
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Miroir de `LiveMatchModel.swift` — porte l'état de la manche en cours, rien n'est écrit tant
 * qu'elle n'est pas validée. **La partie partagée en direct (doc 09 : partage, pairs connectés,
 * Live Activity) n'est pas portée** : `startSharing`/`stopSharing`/`refreshFromRemote` dépendent
 * de `:sync` (étape F). Ce ViewModel couvre uniquement le jeu solo/local, hôte de sa propre
 * partie.
 */
class LiveMatchViewModel(
    private var match: MatchEntity,
    val definition: GameDefinition,
    private val rules: GameRules,
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
    private val deviceID: String,
) : ViewModel() {
    private val stateFlow = MutableStateFlow<MatchState?>(null)
    val stateOrNull get() = stateFlow.value

    var pendingScores by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set
    var closedParticipantID by mutableStateOf<UUID?>(null)
    var activeSeatIndex by mutableStateOf(0)
        private set
    var validationErrorMessage by mutableStateOf<String?>(null)
        private set
    var roundExplanationMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { stateFlow.value = repository.loadState(match, catalog) }
    }

    private val state: MatchState get() = requireNotNull(stateFlow.value) { "MatchState pas encore chargé" }

    val participants: List<Participant> get() = state.participants.sortedBy { it.seatIndex }
    val totals: Map<UUID, Int> get() = state.totals()
    val currentParticipant: Participant? get() = participants.getOrNull(activeSeatIndex)
    val requiresCloserSelection: Boolean get() = definition.requiresCloserSelection

    /** Manches déjà validées, dans l'ordre — brut, sans interprétation : chaque écran de saisie
     * dédié (Tarot/Wizard/Yams) décode lui-même le `ScoreDetail` propre à son jeu (`:catalog`
     * expose les types `*Detail` publiquement), ce ViewModel générique reste agnostique du jeu. */
    val rounds: List<Round> get() = state.rounds
    val currentRoundNumber: Int get() = state.rounds.size + 1

    /** Classement courant, recalculé à chaque manche validée — sert aussi bien à
     * [finalStandings] qu'à trier/annoter la liste de saisie en direct. */
    val currentStandings: List<Standing> get() = rules.standings(state, definition)
    val finalStandings: List<Standing> get() = currentStandings

    val isConcluded: Boolean get() = state.status == MatchStatus.Ended || state.status == MatchStatus.Abandoned

    /** Doc utilisateur — quel que soit le jeu, on doit pouvoir arrêter une partie quand on veut,
     * pas seulement ceux qui déclarent `manualStop` : une seule manche jouée suffit à produire un
     * classement qui a du sens. */
    val canEndManually: Boolean get() = state.rounds.isNotEmpty()

    fun endManually() = mutate { repository.endMatchManually(match, catalog, deviceID) }

    /** Abandon volontaire — classée dans l'historique avec le classement atteint jusque-là,
     * contrairement à une suppression qui ferait tout perdre. */
    fun abandon() = mutate { repository.abandonMatch(match, catalog, deviceID) }

    fun setScore(
        participantID: UUID,
        value: Int,
    ) {
        pendingScores = pendingScores + (participantID to value)
        validationErrorMessage = null
    }

    fun clearScore(participantID: UUID) {
        pendingScores = pendingScores - participantID
    }

    /** Doc utilisateur — les scores ne sont jamais annoncés dans l'ordre des sièges : chaque
     * champ se remplit par un tap direct. `activeSeatIndex` ne sert qu'à mettre en valeur le
     * champ actuellement focus. */
    fun focus(participantID: UUID) {
        val index = participants.indexOfFirst { it.id == participantID }
        if (index >= 0) activeSeatIndex = index
    }

    fun commitRound(detailByParticipant: Map<UUID, ScoreDetail> = emptyMap()) {
        val inputs =
            participants.map { participant ->
                val modifiers =
                    if (participant.id == closedParticipantID) {
                        setOf(ModifierID.closedRound)
                    } else {
                        emptySet()
                    }
                ScoreInput(
                    participantID = participant.id,
                    rawValue = pendingScores[participant.id] ?: 0,
                    detail = detailByParticipant[participant.id],
                    modifiers = modifiers,
                )
            }
        commitCustomRound(inputs) {
            pendingScores = emptyMap()
            closedParticipantID = null
            activeSeatIndex = 0
        }
    }

    /** Même chemin que [commitRound], mais pour les écrans de saisie dédiés (Belote/Tarot/Wizard/
     * Yams), dont les [ScoreInput] ne suivent pas le schéma générique « une entrée par
     * participant, un seul modificateur possible » — ex. Belote/Tarot n'en soumettent qu'un ou
     * deux (le moteur redistribue), avec plusieurs modificateurs par entrée (`isTaker`, `capot`,
     * …). [onCommitted] laisse chaque appelant réinitialiser son propre état de saisie (brouillon
     * de donne/manche) une fois la validation et l'écriture réussies — jamais avant, pour ne pas
     * effacer une saisie que le moteur vient de rejeter. */
    fun commitCustomRound(
        inputs: List<ScoreInput>,
        onCommitted: () -> Unit = {},
    ) {
        val draft = RoundDraft(index = state.rounds.size, inputs = inputs)

        val validation = rules.validate(draft, state, definition)
        if (validation is ValidationResult.Invalid) {
            validationErrorMessage = validation.errors.firstOrNull()?.message
            return
        }

        viewModelScope.launch {
            val newState =
                try {
                    repository.commitRound(draft, match, catalog, deviceID)
                } catch (cancellation: CancellationException) {
                    throw cancellation // ne jamais avaler l'annulation structurée d'une coroutine.
                } catch (error: Exception) {
                    validationErrorMessage = "La manche n'a pas pu être enregistrée."
                    return@launch
                }
            match = requireNotNull(repository.match(match.id))
            stateFlow.value = newState
            validationErrorMessage = null
            onCommitted()
            newState.rounds
                .lastOrNull()
                ?.entries
                ?.firstNotNullOfOrNull { it.explanation }
                ?.let(::showRoundExplanation)
        }
    }

    private fun showRoundExplanation(message: String) {
        viewModelScope.launch {
            roundExplanationMessage = message
            delay(4_000)
            if (roundExplanationMessage == message) roundExplanationMessage = null
        }
    }

    fun undoLastRound() = mutate { repository.undoLastRound(match, catalog, deviceID) }

    private fun mutate(block: suspend () -> MatchState) {
        viewModelScope.launch {
            stateFlow.value = block()
            match = requireNotNull(repository.match(match.id))
        }
    }
}
