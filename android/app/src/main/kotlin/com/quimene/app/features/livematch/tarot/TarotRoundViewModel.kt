package com.quimene.app.features.livematch.tarot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.quimene.app.R
import com.quimene.app.features.livematch.LiveRoundEntryState
import com.quimene.catalog.games.TarotHandDetail
import com.quimene.catalog.toScoreDetail
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.ScoreInput
import java.util.UUID

/** Miroir de `TarotRoundModel.swift` — une donne = le preneur seul (3/4 joueurs), ou preneur +
 * partenaire « roi appelé » (5 joueurs) ; les défenseurs ne saisissent jamais rien
 * ([LiveRoundEntryState.commitCustomRound] laisse le moteur calculer et distribuer leur part). */
class TarotRoundViewModel(
    private val liveMatch: LiveRoundEntryState,
) : ViewModel() {
    val needsPartner: Boolean get() = liveMatch.participants.size == 5

    var isPassed by mutableStateOf(false)
        private set
    var takerID by mutableStateOf(liveMatch.participants.firstOrNull()?.id)
        private set
    var partnerID by mutableStateOf<UUID?>(null)
        private set
    var points by mutableStateOf(46)
        private set
    var contract by mutableStateOf(0)
        private set
    var bouts by mutableStateOf(0)
        private set
    var poignee by mutableStateOf(0)
        private set
    var petitAuBout by mutableStateOf(false)
        private set
    var chelemAnnounced by mutableStateOf(false)
        private set
    var chelemAchieved by mutableStateOf(false)
        private set

    fun updatePassed(value: Boolean) {
        isPassed = value
    }

    fun selectTaker(id: UUID) {
        takerID = id
        if (partnerID == id) partnerID = null
    }

    fun selectPartner(id: UUID?) {
        partnerID = id
    }

    fun updatePoints(value: Int) {
        points = value.coerceIn(0, 91)
    }

    fun selectContract(value: Int) {
        contract = value
    }

    fun updateBouts(value: Int) {
        bouts = value.coerceIn(0, 3)
    }

    fun selectPoignee(value: Int) {
        poignee = value
    }

    fun updatePetitAuBout(value: Boolean) {
        petitAuBout = value
    }

    fun updateChelemAnnounced(value: Boolean) {
        chelemAnnounced = value
    }

    fun updateChelemAchieved(value: Boolean) {
        chelemAchieved = value
    }

    fun submit() {
        val taker = takerID ?: return

        val inputs =
            if (isPassed) {
                listOf(ScoreInput(participantID = taker, rawValue = 0, modifiers = setOf(ModifierID("passed"))))
            } else {
                val modifiers =
                    buildSet {
                        add(ModifierID("isTaker"))
                        if (petitAuBout) add(ModifierID("petitAuBout"))
                        if (chelemAnnounced) add(ModifierID("chelemAnnounced"))
                        if (chelemAchieved) add(ModifierID("chelemAchieved"))
                    }
                val detail = TarotHandDetail(contract = contract, bouts = bouts, poignee = poignee).toScoreDetail()
                val takerInput =
                    ScoreInput(participantID = taker, rawValue = points, detail = detail, modifiers = modifiers)
                val partner = partnerID
                if (needsPartner && partner != null && partner != taker) {
                    listOf(
                        takerInput,
                        ScoreInput(participantID = partner, rawValue = 0, modifiers = setOf(ModifierID("isPartner"))),
                    )
                } else {
                    listOf(takerInput)
                }
            }

        liveMatch.commitCustomRound(inputs) {
            isPassed = false
            points = 46
            contract = 0
            bouts = 0
            poignee = 0
            petitAuBout = false
            chelemAnnounced = false
            chelemAchieved = false
            partnerID = null
        }
    }

    companion object {
        /** Miroir des libellés `TarotRoundView.swift` — des identifiants de ressource, pas des
         * `String` : cette `companion object` n'est pas dans un contexte composable, `stringResource()`
         * ne peut être appelé que là où [TarotRoundScreen] affiche effectivement chaque libellé. */
        val contractLabels =
            listOf(R.string.petite, R.string.garde, R.string.garde_sans_le_chien, R.string.garde_contre_le_chien)
        val poigneeLabels = listOf(R.string.aucune, R.string.simple, R.string.double_label, R.string.triple)
    }
}
