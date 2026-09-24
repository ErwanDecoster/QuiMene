package com.quimene.app.livesync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quimene.app.features.livematch.LiveRoundEntryState
import com.quimene.domain.engine.MatchEngine
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.Round
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreDetail
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.ValidationResult
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.Standing
import com.quimene.sync.LiveSession
import com.quimene.sync.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Modèle **non-hôte** (contributeur/observateur) d'une partie rejointe — miroir de
 * `SharedMatchModel.swift`. Aucun [com.quimene.store.MatchEntity]/`MatchRepository` : l'état
 * est entièrement reconstruit par [MatchEngine.replay] à chaque événement, jamais persisté en
 * local (l'hôte reste la seule source de vérité durable, doc 09).
 *
 * N'étend pas `androidx.lifecycle.ViewModel` — possédé par [MatchConnectionCoordinator]
 * (durée de vie de la session, pas d'un `NavBackStackEntry`), exactement comme
 * `SharedMatchModel` est possédé par `MatchConnectionCoordinator.swift`, pas par une vue.
 */
class SharedMatchViewModel(
    private val session: LiveSession,
    val role: Role,
    private val catalog: GameCatalog,
    private val scope: CoroutineScope,
    private val onStopped: () -> Unit,
) : LiveRoundEntryState {
    private var log: List<StampedEvent> = emptyList()
    private var stateInternal by mutableStateOf<MatchState?>(null)
    val stateOrNull get() = stateInternal
    private val state: MatchState get() = requireNotNull(stateInternal) { "MatchState pas encore reçu de l'hôte" }

    /** `false` seulement pour un observateur — un contributeur peut toujours soumettre, même si
     * l'hôte peut la refuser ensuite (doc 09 : « autoriser les contributeurs » ne change que qui
     * *peut* proposer, jamais ce qu'un observateur voit). */
    val canPropose: Boolean get() = role == Role.Contributor

    var isHostConnected by mutableStateOf(true)
        private set
    var latestRejectionReason by mutableStateOf<String?>(null)
        private set

    override val definition: GameDefinition get() = catalog.definition(state.gameID, state.rulesVersion)
    override val participants: List<Participant> get() = state.participants.sortedBy { it.seatIndex }
    override val totals: Map<UUID, Int> get() = state.totals()
    override val rounds: List<Round> get() = state.rounds
    override val currentRoundNumber: Int get() = state.rounds.size + 1
    override val requiresCloserSelection: Boolean get() = definition.requiresCloserSelection
    override val currentStandings: List<Standing> get() =
        catalog
            .rules(
                state.gameID,
                state.rulesVersion,
            ).standings(state, definition)

    override var pendingScores by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set
    override var closedParticipantID by mutableStateOf<UUID?>(null)
    override var validationErrorMessage by mutableStateOf<String?>(null)
        private set
    override var roundExplanationMessage by mutableStateOf<String?>(null)
        private set

    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch { session.events.collect { stamped -> apply(stamped) } }
        jobs += scope.launch { session.rejections.collect { (eventID, reason) -> handleRejection(eventID, reason) } }
        jobs += scope.launch { session.hostLeft.collect { isHostConnected = false } }
        jobs +=
            scope.launch {
                session.matchChanged.collect { newLog ->
                    log = newLog
                    replay()
                }
            }
    }

    private fun apply(stamped: StampedEvent) {
        // Doc 04 « Event sourcing » — dédoublonnage par id, comme `SharedMatchModel.apply` côté
        // Apple : la confirmation d'une proposition optimiste porte le même id qu'elle, et l'hôte
        // renvoie tout son journal à un pair en retard (`LiveSession.resendLocked`).
        if (log.any { it.id == stamped.id }) return
        log = log + stamped
        val roundCountBefore = stateInternal?.rounds?.size ?: 0
        replay()
        val newState = stateInternal ?: return
        if (newState.rounds.size > roundCountBefore) {
            newState.rounds
                .lastOrNull()
                ?.entries
                ?.firstNotNullOfOrNull { it.explanation }
                ?.let(::showRoundExplanation)
        }
    }

    private fun handleRejection(
        eventID: UUID,
        reason: String,
    ) {
        latestRejectionReason = reason
        log = log.filterNot { it.id == eventID }
        replay()
    }

    private fun replay() {
        stateInternal = runCatching { MatchEngine().replay(log, catalog) }.getOrNull()
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

    /** Valide localement **avant** d'envoyer (comme `SharedMatchView.sendRound` côté Apple) :
     * évite le clignotement d'un refus immédiat de l'hôte pour une erreur détectable sans lui. */
    override fun commitCustomRound(
        inputs: List<ScoreInput>,
        onCommitted: () -> Unit,
    ) {
        if (!canPropose) return
        val draft = RoundDraft(index = state.nextRoundIndex, inputs = inputs)
        val rules = catalog.rules(state.gameID, state.rulesVersion)
        val validation = rules.validate(draft, state, definition)
        if (validation is ValidationResult.Invalid) {
            validationErrorMessage = validation.errors.firstOrNull()?.message
            return
        }
        validationErrorMessage = null
        latestRejectionReason = null

        scope.launch {
            try {
                session.propose(MatchEvent.RoundCommitted(draft))
                onCommitted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                validationErrorMessage = "La manche n'a pas pu être envoyée."
            }
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
        for (job in jobs) job.cancel()
        scope.launch {
            try {
                session.leave()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Best-effort — quitter localement prime sur un aller-retour réseau qui échoue.
            }
        }
        onStopped()
    }
}
