package com.quimene.app.features.livematch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.app.R
import com.quimene.app.livesync.LiveShareCoordinator
import com.quimene.app.livesync.SessionLink
import com.quimene.app.livesync.SharedMatchViewModel
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.Round
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreDetail
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.ValidationResult
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import com.quimene.domain.rules.Standing
import com.quimene.store.MatchEntity
import com.quimene.store.MatchRepository
import com.quimene.sync.SessionPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `LiveMatchModel.swift` — porte l'état de la manche en cours, rien n'est écrit tant
 * qu'elle n'est pas validée. **Hôte** de sa propre partie : écrit toujours directement en local
 * ([MatchRepository]) — sauf si elle est partagée en ligne (doc 16, phase C) : chaque événement
 * passe alors d'abord par le journal de la session, et la copie locale n'en est que le miroir. Implémente [LiveRoundEntryState] : les 4 écrans de saisie dédiés
 * (Belote/Tarot/Wizard/Yams) et [GenericRoundEntry] ne le savent jamais distinctement d'un
 * `SharedMatchViewModel` contributeur, exactement comme `ScoreBoardView.swift` côté Apple.
 * Le partage en direct (pairage, pairs connectés) vit dans [LiveShareCoordinator], injecté
 * plutôt que construit ici — une seule session par appareil, pas une par écran.
 */
class LiveMatchViewModel(
    private var match: MatchEntity,
    override val definition: GameDefinition,
    private val rules: GameRules,
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
    private val deviceID: String,
    val shareCoordinator: LiveShareCoordinator? = null,
) : ViewModel(),
    LiveRoundEntryState {
    // `mutableStateOf`, pas `MutableStateFlow` — un `Flow` lu directement par `.value` (jamais
    // collecté) n'est observable par aucune composable : `LiveMatchScreen` ne recomposait pas de
    // façon fiable quand une manche se validait ou qu'une partie se concluait (l'utilisateur
    // restait sur l'écran de saisie jusqu'à un changement d'onglet forçant une recomposition
    // fraîche). Même correctif déjà en place côté `SharedMatchViewModel.stateInternal`.
    private var stateInternal by mutableStateOf<MatchState?>(null)
    val stateOrNull get() = stateInternal

    override var pendingScores by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set
    override var closedParticipantID by mutableStateOf<UUID?>(null)
    var activeSeatIndex by mutableStateOf(0)
        private set
    override var validationErrorMessage by mutableStateOf<String?>(null)
        private set
    override var roundExplanationMessage by mutableStateOf<String?>(null)
        private set

    var remoteActivityMessage by mutableStateOf<String?>(null)
        private set

    /** Un envoi au journal de la session est en cours : « Terminé » est désactivé. */
    var isSubmitting by mutableStateOf(false)
        private set

    val matchID: UUID get() = match.id

    override var profileBadges by mutableStateOf<Map<UUID, ProfileBadge>>(emptyMap())
        private set
    val gameID: String get() = match.gameID

    val isSharing: Boolean get() = shareCoordinator?.attachedMatchID == match.id
    val pairingCode: String? get() = if (isSharing) shareCoordinator?.pairingCode else null
    val allowsContributors: Boolean get() = shareCoordinator?.allowsContributors ?: true

    /** Doc 16 — partie partagée hors ligne : la saisie est bloquée. */
    val isOfflineShared: Boolean get() = isSharing && shareCoordinator?.isReachable == false
    val connectedPeers: List<SessionPresence> get() =
        if (isSharing) {
            shareCoordinator
                ?.connectedPeers
                .orEmpty()
        } else {
            emptyList()
        }

    init {
        viewModelScope.launch {
            stateInternal = repository.loadState(match, catalog)
            profileBadges =
                repository.linkedParticipants(match.id).mapValues { (_, isMine) ->
                    if (isMine) ProfileBadge.Me else ProfileBadge.Friend
                }
        }
        shareCoordinator?.let { coordinator ->
            viewModelScope.launch {
                coordinator.remoteMatchUpdates.collect { update ->
                    if (update.matchID != match.id) return@collect
                    match = requireNotNull(repository.match(match.id))
                    stateInternal = repository.loadState(match, catalog)
                    update.deviceName?.let { name -> if (update.isRoundCommit) announceRemoteActivity(name) }
                }
            }
        }
    }

    private fun announceRemoteActivity(deviceName: String) {
        viewModelScope.launch {
            val message = "$deviceName a ajouté une manche."
            remoteActivityMessage = message
            delay(4_000)
            if (remoteActivityMessage == message) remoteActivityMessage = null
        }
    }

    /** Démarre (ou continue) le partage en direct de cette partie — mirror de
     * `LiveMatchModel.startSharing`. Suspend plutôt que fire-and-forget : [ShareSessionDialog]
     * attend l'issue pour afficher une erreur éventuelle sans avaler `CancellationException`. */
    suspend fun startSharing(
        deviceName: String,
        allowsContributors: Boolean,
    ) {
        shareCoordinator?.startSharing(match, deviceName, allowsContributors)
    }

    private val state: MatchState get() = requireNotNull(stateInternal) { "MatchState pas encore chargé" }

    override val participants: List<Participant> get() = state.participants.sortedBy { it.seatIndex }
    override val totals: Map<UUID, Int> get() = state.totals()
    val currentParticipant: Participant? get() = participants.getOrNull(activeSeatIndex)
    override val requiresCloserSelection: Boolean get() = definition.requiresCloserSelection

    /** Manches déjà validées, dans l'ordre — brut, sans interprétation : chaque écran de saisie
     * dédié (Tarot/Wizard/Yams) décode lui-même le `ScoreDetail` propre à son jeu (`:catalog`
     * expose les types `*Detail` publiquement), ce ViewModel générique reste agnostique du jeu. */
    override val rounds: List<Round> get() = state.rounds
    override val currentRoundNumber: Int get() = state.rounds.size + 1

    /** Classement courant, recalculé à chaque manche validée — sert aussi bien à
     * [finalStandings] qu'à trier/annoter la liste de saisie en direct. */
    override val currentStandings: List<Standing> get() = rules.standings(state, definition)
    val finalStandings: List<Standing> get() = currentStandings

    val isConcluded: Boolean get() = state.status == MatchStatus.Ended || state.status == MatchStatus.Abandoned

    /** Doc utilisateur — quel que soit le jeu, on doit pouvoir arrêter une partie quand on veut,
     * pas seulement ceux qui déclarent `manualStop` : une seule manche jouée suffit à produire un
     * classement qui a du sens. */
    val canEndManually: Boolean get() = state.rounds.isNotEmpty()

    fun endManually() {
        if (isSharing) {
            viewModelScope.launch { submitShared(MatchEvent.MatchEndedManually) }
            return
        }
        mutate { repository.endMatchManually(match, catalog, deviceID) }
    }

    /** Abandon volontaire — classée dans l'historique avec le classement atteint jusque-là,
     * contrairement à une suppression qui ferait tout perdre. */
    fun abandon() {
        if (isSharing) {
            viewModelScope.launch { submitShared(MatchEvent.MatchAbandoned(Instant.now())) }
            return
        }
        mutate { repository.abandonMatch(match, catalog, deviceID) }
    }

    override fun setScore(
        participantID: UUID,
        value: Int,
    ) {
        pendingScores = pendingScores + (participantID to value)
        validationErrorMessage = null
    }

    override fun clearScore(participantID: UUID) {
        pendingScores = pendingScores - participantID
    }

    /** Doc utilisateur — les scores ne sont jamais annoncés dans l'ordre des sièges : chaque
     * champ se remplit par un tap direct. `activeSeatIndex` ne sert qu'à mettre en valeur le
     * champ actuellement focus. */
    override fun focus(participantID: UUID) {
        val index = participants.indexOfFirst { it.id == participantID }
        if (index >= 0) activeSeatIndex = index
    }

    override fun commitRound(detailByParticipant: Map<UUID, ScoreDetail>) {
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
    override fun commitCustomRound(
        inputs: List<ScoreInput>,
        onCommitted: () -> Unit,
    ) {
        val draft = RoundDraft(index = state.nextRoundIndex, inputs = inputs)

        val validation = rules.validate(draft, state, definition)
        if (validation is ValidationResult.Invalid) {
            validationErrorMessage = validation.errors.firstOrNull()?.message
            return
        }

        if (isSharing) {
            if (isSubmitting) return
            validationErrorMessage = null
            viewModelScope.launch {
                if (!submitShared(MatchEvent.RoundCommitted(draft))) return@launch
                onCommitted()
                state.rounds
                    .lastOrNull()
                    ?.entries
                    ?.firstNotNullOfOrNull { it.explanation }
                    ?.let(::showRoundExplanation)
            }
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
            stateInternal = newState
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

    fun undoLastRound() {
        if (isSharing) {
            val lastIndex = state.rounds.maxOfOrNull { it.index } ?: return
            viewModelScope.launch { submitShared(MatchEvent.RoundRemoved(lastIndex)) }
            return
        }
        mutate { repository.undoLastRound(match, catalog, deviceID) }
    }

    private fun mutate(block: suspend () -> MatchState) {
        viewModelScope.launch {
            stateInternal = block()
            match = requireNotNull(repository.match(match.id))
        }
    }

    /** Doc 16, phase C — envoie un événement au journal de la session, puis recharge la copie
     * locale (déjà mise en miroir par [LiveShareCoordinator]). Pas de nouvel essai automatique si
     * un autre appareil a saisi entre-temps : la saisie reste en place, avec un message. Miroir de
     * `LiveMatchModel.submitShared`. */
    private suspend fun submitShared(event: MatchEvent): Boolean {
        val coordinator = shareCoordinator ?: return false
        isSubmitting = true
        try {
            val result = coordinator.submit(event, match.id)
            repository.match(match.id)?.let {
                match = it
                stateInternal = repository.loadState(it, catalog)
            }
            val context = coordinator.context
            validationErrorMessage =
                when (result) {
                    is SessionLink.SubmitResult.Accepted -> null
                    is SessionLink.SubmitResult.Overtaken ->
                        SharedMatchViewModel.overtakenMessage(context, result.byDeviceName)
                    SessionLink.SubmitResult.Offline ->
                        context.getString(R.string.hors_connexion_la_saisie_reprendra_au_retour_du_reseau)
                    SessionLink.SubmitResult.Closed -> context.getString(R.string.la_session_partagee_a_ete_arretee)
                }
            return result is SessionLink.SubmitResult.Accepted
        } finally {
            isSubmitting = false
        }
    }

    /** Doc 16, phase C — « Partie suivante » du créateur, avec les mêmes joueurs ; renvoie la
     * nouvelle partie, à ouvrir. */
    suspend fun startNextMatch(next: GameDefinition): UUID? {
        val coordinator = shareCoordinator ?: return null
        isSubmitting = true
        try {
            return coordinator.startNextMatch(next, match)?.id
        } finally {
            isSubmitting = false
        }
    }
}
