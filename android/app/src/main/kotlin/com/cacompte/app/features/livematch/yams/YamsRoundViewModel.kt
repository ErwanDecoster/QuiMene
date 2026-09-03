package com.cacompte.app.features.livematch.yams

import androidx.lifecycle.ViewModel
import com.cacompte.app.features.livematch.LiveMatchViewModel
import com.cacompte.catalog.decodeDetail
import com.cacompte.catalog.games.YamsCategoryDetail
import com.cacompte.catalog.toScoreDetail
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.model.ScoreInput
import java.util.UUID

/** Miroir de `YamsSheetModel.swift` — une « manche » remplit une seule catégorie pour un seul
 * participant ; le bonus de section haute est calculé par le moteur (`YamsRulesV1`), jamais
 * saisi. */
class YamsRoundViewModel(
    private val liveMatch: LiveMatchViewModel,
) : ViewModel() {
    /** Catégories déjà remplies par ce participant, par `categoryID`. */
    fun filledEntries(participantID: UUID): Map<String, ScoreEntry> {
        val result = mutableMapOf<String, ScoreEntry>()
        for (round in liveMatch.rounds) {
            for (entry in round.entries) {
                if (entry.participantID != participantID) continue
                val categoryID = entry.detail.decodeDetail<YamsCategoryDetail>()?.categoryID ?: continue
                result[categoryID] = entry
            }
        }
        return result
    }

    fun submit(
        participantID: UUID,
        categoryID: String,
        rawValue: Int,
    ) {
        val detail = YamsCategoryDetail(categoryID).toScoreDetail()
        liveMatch.commitCustomRound(
            listOf(ScoreInput(participantID = participantID, rawValue = rawValue, detail = detail)),
        )
    }
}
