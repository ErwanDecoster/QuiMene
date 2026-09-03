package com.cacompte.app.features.livematch.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.cacompte.app.features.livematch.LiveMatchViewModel
import com.cacompte.catalog.games.WizardBidDetail
import com.cacompte.catalog.toScoreDetail
import com.cacompte.domain.model.ScoreInput
import java.util.UUID

/** Miroir de `WizardRoundModel.swift` — une manche = une annonce (nombre de plis prédits) et un
 * résultat (plis réellement réalisés) par participant, soumis ensemble en une seule fois (le
 * moteur valide que la somme des résultats égale le numéro de la manche). */
class WizardRoundViewModel(
    private val liveMatch: LiveMatchViewModel,
) : ViewModel() {
    val roundNumber: Int get() = liveMatch.currentRoundNumber

    var bids by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set
    var results by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set

    val totalTricks: Int get() = liveMatch.participants.sumOf { result(it.id) }

    fun bid(participantID: UUID): Int = bids[participantID] ?: 0

    fun result(participantID: UUID): Int = results[participantID] ?: 0

    fun setBid(
        participantID: UUID,
        value: Int,
    ) {
        bids = bids + (participantID to value.coerceIn(0, roundNumber))
    }

    fun setResult(
        participantID: UUID,
        value: Int,
    ) {
        results = results + (participantID to value.coerceIn(0, roundNumber))
    }

    fun submit() {
        val inputs =
            liveMatch.participants.map { participant ->
                val detail = WizardBidDetail(bid(participant.id)).toScoreDetail()
                ScoreInput(participantID = participant.id, rawValue = result(participant.id), detail = detail)
            }
        liveMatch.commitCustomRound(inputs) {
            bids = emptyMap()
            results = emptyMap()
        }
    }
}
