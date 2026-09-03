package com.cacompte.app.features.livematch

import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.Round
import com.cacompte.domain.model.ScoreDetail
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.Standing
import java.util.UUID

/**
 * Surface commune aux 5 formes de saisie de manche (générique + les 4 dédiées Belote/Tarot/
 * Wizard/Yams) — aucune ne connaît la différence entre un hôte ([LiveMatchViewModel], qui écrit
 * directement en local) et un contributeur (`SharedMatchViewModel`, qui soumet via
 * `LiveSession.propose` pour validation par l'hôte distant). Miroir de la façon dont
 * `ScoreBoardView.swift` est partagée telle quelle entre `LiveMatchModel` (hôte) et
 * `SharedMatchModel` (contributeur/observateur) côté Apple.
 */
interface LiveRoundEntryState {
    val definition: GameDefinition
    val participants: List<Participant>
    val totals: Map<UUID, Int>
    val rounds: List<Round>
    val currentRoundNumber: Int
    val requiresCloserSelection: Boolean

    /** Classement courant, recalculé à chaque manche validée — sert à faire figurer un rang en
     * tête de chaque ligne de saisie (doc utilisateur), pas seulement à l'écran de résultats. */
    val currentStandings: List<Standing>

    val pendingScores: Map<UUID, Int>
    var closedParticipantID: UUID?

    val validationErrorMessage: String?
    val roundExplanationMessage: String?

    fun setScore(
        participantID: UUID,
        value: Int,
    )

    fun clearScore(participantID: UUID)

    fun focus(participantID: UUID)

    fun commitRound(detailByParticipant: Map<UUID, ScoreDetail> = emptyMap())

    fun commitCustomRound(
        inputs: List<ScoreInput>,
        onCommitted: () -> Unit = {},
    )
}
