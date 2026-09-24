package com.quimene.app.features.livematch.belote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.quimene.app.features.livematch.LiveRoundEntryState
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.ScoreInput

/**
 * Miroir de `BeloteRoundModel.swift` — une donne = une entrée par équipe (2 toujours), le moteur
 * (`BeloteRulesV1`) redistribue à chaque coéquipier. Enveloppe [LiveRoundEntryState] (hôte ou
 * contributeur, voir cette interface) plutôt que de recharger la partie soi-même : seul l'état
 * de brouillon de la donne en cours est propre à ce ViewModel.
 */
class BeloteRoundViewModel(
    private val liveMatch: LiveRoundEntryState,
) : ViewModel() {
    /** Équipes du match, dans l'ordre des sièges — toujours 2 pour Belote (4 joueurs, doc 05). */
    val teams: List<String>
        get() = liveMatch.participants.mapNotNull { it.teamID }.distinct()

    var takerTeamID by mutableStateOf(teams.firstOrNull().orEmpty())
        private set
    var takerPoints by mutableStateOf(82)
        private set
    var isCapot by mutableStateOf(false)
        private set
    var beloteRebeloteTeamID by mutableStateOf<String?>(null)
        private set

    val defenderTeamID: String? get() = teams.firstOrNull { it != takerTeamID }

    fun selectTaker(teamID: String) {
        takerTeamID = teamID
    }

    fun updateTakerPoints(points: Int) {
        takerPoints = points.coerceIn(0, 162)
    }

    fun updateCapot(value: Boolean) {
        isCapot = value
    }

    fun selectBeloteRebelote(teamID: String?) {
        beloteRebeloteTeamID = teamID
    }

    fun submit() {
        val defender = defenderTeamID ?: return
        val takerRepresentative = liveMatch.participants.firstOrNull { it.teamID == takerTeamID } ?: return
        val defenderRepresentative = liveMatch.participants.firstOrNull { it.teamID == defender } ?: return

        val takerModifiers = mutableSetOf(ModifierID("isTaker"))
        val defenderModifiers = mutableSetOf<ModifierID>()
        if (isCapot) takerModifiers += ModifierID("capot")
        if (beloteRebeloteTeamID == takerTeamID) takerModifiers += ModifierID("beloteRebelote")
        if (beloteRebeloteTeamID == defender) defenderModifiers += ModifierID("beloteRebelote")

        val inputs =
            listOf(
                ScoreInput(participantID = takerRepresentative.id, rawValue = takerPoints, modifiers = takerModifiers),
                ScoreInput(
                    participantID = defenderRepresentative.id,
                    rawValue = 162 - takerPoints,
                    modifiers = defenderModifiers,
                ),
            )
        liveMatch.commitCustomRound(inputs) {
            takerPoints = 82
            isCapot = false
            beloteRebeloteTeamID = null
        }
    }
}
