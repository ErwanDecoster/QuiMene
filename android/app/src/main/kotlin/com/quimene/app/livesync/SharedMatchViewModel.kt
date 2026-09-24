package com.quimene.app.livesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quimene.app.R
import com.quimene.app.features.livematch.LiveRoundEntryState
import com.quimene.app.features.results.MatchSummaryViewModel
import com.quimene.app.features.results.matchSummaryState
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.domain.engine.MatchEngine
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
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.Standing
import com.quimene.store.ParticipantEntity
import com.quimene.sync.Role
import com.quimene.sync.SessionEventRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Doc 16, phase C — miroir de `SharedMatchModel.swift` : la partie suivie par un participant.
 * Aucune copie en base : l'état est rejoué depuis le journal de la session ([SessionLink]), qui
 * fait foi pour tous. La partie courante est celle du `matchCreated` le plus récent : quand
 * quelqu'un en lance une nouvelle, l'écran la suit de lui-même.
 */
class SharedMatchViewModel(
    val link: SessionLink,
    val role: Role,
    private val catalog: GameCatalog,
    private val scope: CoroutineScope,
    private val onStopped: () -> Unit,
) : LiveRoundEntryState {
    private var stateInternal by mutableStateOf<MatchState?>(null)
    val stateOrNull get() = stateInternal
    private val state: MatchState get() = requireNotNull(stateInternal) { "Partie pas encore reçue" }
    private var currentMatchID: UUID? = null

    /** Seul un contributeur saisit, et seulement tant que la session est ouverte. */
    val canPropose: Boolean get() = role == Role.Contributor && !link.isClosed

    /** Joignable et session ouverte ; hors ligne, le tableau reste le dernier reçu (doc 16). */
    val isHostConnected: Boolean get() = link.isReachable && !link.isClosed
    val isSessionClosed: Boolean get() = link.isClosed
    val isConcluded: Boolean
        get() = stateInternal?.status == MatchStatus.Ended || stateInternal?.status == MatchStatus.Abandoned

    var latestRejectionReason by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    override val definition: GameDefinition get() = catalog.definition(state.gameID, state.rulesVersion)
    override val participants: List<Participant> get() = state.participants.sortedBy { it.seatIndex }
    override val totals: Map<UUID, Int> get() = state.totals()
    override val rounds: List<Round> get() = state.rounds
    override val currentRoundNumber: Int get() = state.rounds.size + 1
    override val requiresCloserSelection: Boolean get() = definition.requiresCloserSelection
    override val currentStandings: List<Standing>
        get() = catalog.rules(state.gameID, state.rulesVersion).standings(state, definition)

    override var pendingScores by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set
    override var closedParticipantID by mutableStateOf<UUID?>(null)
    override var validationErrorMessage by mutableStateOf<String?>(null)
        private set
    override var roundExplanationMessage by mutableStateOf<String?>(null)
        private set

    init {
        link.onNewRecords = { reload(it) }
    }

    /** Écran de résultats, avec des fiches en mémoire (avatar dérivé du pseudo). */
    fun summaryState(): MatchSummaryViewModel.UiState? {
        val current = stateInternal ?: return null
        val definition = catalog.definition(current.gameID, current.rulesVersion)
        val entities =
            current.participants.associate { participant ->
                val avatar = Avatar.generated(participant.displayName)
                participant.id to
                    ParticipantEntity(
                        id = participant.id,
                        playerId = null,
                        nicknameSnapshot = participant.displayName,
                        avatarKindSnapshot = "emoji",
                        avatarValueSnapshot = (avatar.kind as? AvatarKind.Emoji)?.character.orEmpty(),
                        paletteIDSnapshot = avatar.palette.index.toString(),
                        seatIndex = participant.seatIndex,
                        teamID = participant.teamID,
                        matchId = current.matchID,
                    )
            }
        return matchSummaryState(current, definition, catalog.rules(current.gameID, current.rulesVersion), entities)
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

    override fun focus(participantID: UUID) = Unit

    override fun commitRound(detailByParticipant: Map<UUID, ScoreDetail>) {
        val inputs =
            participants.map { participant ->
                val modifiers = if (participant.id == closedParticipantID) setOf(ModifierID.closedRound) else emptySet()
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
        }
    }

    /** Valide localement, puis envoie au journal de la session. La saisie n'est effacée
     * ([onCommitted]) qu'une fois acceptée ; devancée ou hors ligne, elle reste en place. */
    override fun commitCustomRound(
        inputs: List<ScoreInput>,
        onCommitted: () -> Unit,
    ) {
        val matchID = currentMatchID
        if (!canPropose || isSubmitting || matchID == null) return
        val draft = RoundDraft(index = state.nextRoundIndex, inputs = inputs)
        val validation = catalog.rules(state.gameID, state.rulesVersion).validate(draft, state, definition)
        if (validation is ValidationResult.Invalid) {
            validationErrorMessage = validation.errors.firstOrNull()?.message
            return
        }
        validationErrorMessage = null
        latestRejectionReason = null
        scope.launch {
            if (submit(MatchEvent.RoundCommitted(draft), matchID)) onCommitted()
        }
    }

    /** Doc 16, phase C — « Partie suivante » lancée par ce participant : mêmes joueurs aux mêmes
     * places (nouveaux identifiants : un identifiant de participant ne sert qu'à une partie), mêmes
     * variantes si c'est le même jeu. */
    fun startNextMatch(next: GameDefinition) {
        val current = stateInternal ?: return
        if (!canPropose || isSubmitting) return
        val newParticipants =
            current.participants
                .sortedBy { it.seatIndex }
                .map { Participant(displayName = it.displayName, seatIndex = it.seatIndex) }
        val variants = if (next.id == current.gameID) current.variants else VariantSelection()
        val matchID = UUID.randomUUID()
        scope.launch {
            submit(
                MatchEvent.MatchCreated(next.id, next.rulesVersion, variants, newParticipants),
                matchID,
                eventID = matchID,
                overtakenMessage =
                    link.context.getString(
                        R.string.une_partie_vient_d_etre_lancee_sur_un_autre_appareil,
                    ),
            )
        }
    }

    private suspend fun submit(
        event: MatchEvent,
        matchID: UUID,
        eventID: UUID = UUID.randomUUID(),
        overtakenMessage: String? = null,
    ): Boolean {
        isSubmitting = true
        try {
            return when (val result = link.submit(event, matchID, eventID)) {
                is SessionLink.SubmitResult.Accepted -> {
                    reload(emptyList())
                    true
                }
                is SessionLink.SubmitResult.Overtaken -> {
                    latestRejectionReason = overtakenMessage ?: overtakenMessage(link.context, result.byDeviceName)
                    false
                }
                SessionLink.SubmitResult.Offline -> {
                    latestRejectionReason =
                        link.context.getString(R.string.hors_connexion_la_saisie_reprendra_au_retour_du_reseau)
                    false
                }
                SessionLink.SubmitResult.Closed -> {
                    latestRejectionReason = link.context.getString(R.string.le_createur_a_arrete_la_session)
                    false
                }
            }
        } finally {
            isSubmitting = false
        }
    }

    private suspend fun reload(fresh: List<SessionEventRecord>) {
        val matchID = link.session.currentMatchID() ?: return
        val replayed =
            runCatching { MatchEngine().replay(link.session.eventsForMatch(matchID), catalog) }.getOrNull() ?: return
        val isNewMatch = matchID != currentMatchID
        val previousRoundCount = if (isNewMatch) 0 else stateInternal?.rounds?.size ?: 0
        currentMatchID = matchID
        stateInternal = replayed
        if (isNewMatch) {
            latestRejectionReason = null
            pendingScores = emptyMap()
            closedParticipantID = null
        }
        if (replayed.rounds.size > previousRoundCount) {
            replayed.rounds
                .lastOrNull()
                ?.entries
                ?.firstNotNullOfOrNull { it.explanation }
                ?.let(::showRoundExplanation)
        }
    }

    private fun showRoundExplanation(message: String) {
        scope.launch {
            roundExplanationMessage = message
            delay(4_000)
            if (roundExplanationMessage == message) roundExplanationMessage = null
        }
    }

    fun stop() {
        scope.launch { link.stop() }
        onStopped()
    }

    companion object {
        fun overtakenMessage(
            context: Context,
            deviceName: String?,
        ): String =
            if (deviceName != null) {
                context.getString(R.string.value1_vient_de_valider_une_manche_verifie_avant_de_valider, deviceName)
            } else {
                context.getString(R.string.une_autre_manche_vient_d_etre_validee_verifie_avant_de)
            }
    }
}
