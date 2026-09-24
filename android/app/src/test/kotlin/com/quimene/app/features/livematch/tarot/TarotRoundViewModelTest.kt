package com.quimene.app.features.livematch.tarot

import com.quimene.app.RoomTestBase
import com.quimene.app.features.livematch.LiveMatchViewModel
import com.quimene.catalog.GameCatalogEmbedded
import com.quimene.domain.model.VariantSelection
import com.quimene.store.MatchRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Vérifie le câblage entre [TarotRoundViewModel] et le moteur déjà testé (`:catalog`,
 * `TarotRulesV1Test`) — construction du `TarotHandDetail`, des modificateurs, et redistribution
 * aux défenseurs, pas la formule de score elle-même. */
class TarotRoundViewModelTest : RoomTestBase() {
    private val catalog = GameCatalogEmbedded.embedded
    private val definition = catalog.definition("tarot", 1)
    private val rules = catalog.rules("tarot", 1)
    private val repository by lazy { MatchRepository(db.matchDao(), db.participantDao(), db.playerDao()) }

    private fun liveMatchViewModel(playerCount: Int): LiveMatchViewModel {
        val names = listOf("Alice", "Bob", "Carol", "Dave", "Eve").take(playerCount)
        val seeds = names.map { name -> MatchRepository.ParticipantSeed(null, name, "emoji", "🦊", "1") }
        val match =
            runBlocking {
                repository.createMatch(
                    gameID = definition.id,
                    rulesVersion = definition.rulesVersion,
                    variants = VariantSelection(),
                    seeds = seeds,
                )
            }
        return LiveMatchViewModel(match, definition, rules, catalog, repository, "local")
    }

    @Test
    fun `a successful hand is zero-sum across all participants`() {
        val liveMatch = liveMatchViewModel(4)
        val viewModel = TarotRoundViewModel(liveMatch)
        val taker = liveMatch.participants.first()

        viewModel.selectTaker(taker.id)
        viewModel.updatePoints(60) // au-dessus des 56 requis à 0 bout.
        viewModel.submit()

        liveMatch.totals.values.sum() shouldBe 0
        liveMatch.totals[taker.id]!! shouldBe (liveMatch.totals.values.max())
    }

    @Test
    fun `a passed hand records zero for every participant`() {
        val liveMatch = liveMatchViewModel(4)
        val viewModel = TarotRoundViewModel(liveMatch)

        viewModel.updatePassed(true)
        viewModel.submit()

        liveMatch.totals.values.all { it == 0 } shouldBe true
    }

    @Test
    fun `a 5-player hand requires a partner and pays them separately from the other defenders`() {
        val liveMatch = liveMatchViewModel(5)
        val viewModel = TarotRoundViewModel(liveMatch)
        val taker = liveMatch.participants[0]
        val partner = liveMatch.participants[1]
        val otherDefender = liveMatch.participants[2]

        viewModel.selectTaker(taker.id)
        viewModel.selectPartner(partner.id)
        viewModel.updatePoints(60)
        viewModel.submit()

        liveMatch.validationErrorMessage shouldBe null
        // Le partenaire touche le score signé non multiplié, les autres défenseurs son opposé —
        // des montants différents dès que le multiplicateur du preneur (×2 à 5 joueurs) s'applique.
        (liveMatch.totals[partner.id] != liveMatch.totals[otherDefender.id]) shouldBe true
    }

    @Test
    fun `submitting resets the draft back to its defaults`() {
        val liveMatch = liveMatchViewModel(4)
        val viewModel = TarotRoundViewModel(liveMatch)
        viewModel.selectTaker(liveMatch.participants.first().id)
        viewModel.updatePoints(70)
        viewModel.updateChelemAchieved(true)

        viewModel.submit()

        viewModel.points shouldBe 46
        viewModel.chelemAchieved shouldBe false
    }
}
