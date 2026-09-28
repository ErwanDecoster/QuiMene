package com.quimene.catalog.testing

import com.quimene.catalog.games.TarotHandDetail
import com.quimene.catalog.games.WizardBidDetail
import com.quimene.catalog.games.YamsCategoryDetail
import com.quimene.catalog.toScoreDetail
import com.quimene.domain.model.ScoreDetail

// Raccourcis de construction des détails de saisie, réservés aux tests (l'app construit
// directement `TarotHandDetail`/`WizardBidDetail`/`YamsCategoryDetail`).

fun tarotHandScoreDetail(
    contract: Int,
    bouts: Int,
    poignee: Int,
): ScoreDetail = TarotHandDetail(contract, bouts, poignee).toScoreDetail()

fun wizardBidScoreDetail(bid: Int): ScoreDetail = WizardBidDetail(bid).toScoreDetail()

fun yamsCategoryScoreDetail(categoryID: String): ScoreDetail = YamsCategoryDetail(categoryID).toScoreDetail()
