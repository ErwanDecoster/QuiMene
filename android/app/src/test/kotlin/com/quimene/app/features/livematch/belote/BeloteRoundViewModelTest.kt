package com.quimene.app.features.livematch.belote

import com.quimene.app.RoomTestBase
import com.quimene.app.features.livematch.LiveMatchViewModel
import com.quimene.catalog.GameCatalogEmbedded
import com.quimene.domain.model.VariantSelection
import com.quimene.store.MatchRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Vérifie le câblage entre [BeloteRoundViewModel] et le moteur déjà testé (`:catalog`,
 * `BeloteRulesV1Test`) — pas la logique de calcul elle-même, seulement que l'écran construit les
 * bonnes entrées. */
class BeloteRoundViewModelTest : RoomTestBase() {
    private val catalog = GameCatalogEmbedded.embedded
    private val definition = catalog.definition("belote", 1)
    private val rules = catalog.rules("belote", 1)
    private val repository by lazy { MatchRepository(db.matchDao(), db.participantDao(), db.playerDao()) }

    private fun liveMatchViewModel(): LiveMatchViewModel {
        val match =
            runBlocking {
                repository.createMatch(
                    gameID = definition.id,
                    rulesVersion = definition.rulesVersion,
                    variants = VariantSelection(),
                    seeds =
                        listOf(
                            MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1", teamID = "A"),
                            MatchRepository.ParticipantSeed(null, "Bob", "emoji", "🐻", "2", teamID = "B"),
                            MatchRepository.ParticipantSeed(null, "Carol", "emoji", "🐼", "3", teamID = "A"),
                            MatchRepository.ParticipantSeed(null, "Dave", "emoji", "🐨", "4", teamID = "B"),
                        ),
                )
            }
        return LiveMatchViewModel(match, definition, rules, catalog, repository, "local")
    }

    @Test
    fun `submitting a taker round splits points 162 between the two teams`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = BeloteRoundViewModel(liveMatch)
        val teamA = liveMatch.participants.first { it.teamID == "A" }
        val teamB = liveMatch.participants.first { it.teamID == "B" }

        viewModel.selectTaker("A")
        viewModel.updateTakerPoints(100)
        viewModel.submit()

        liveMatch.totals[teamA.id] shouldBe 100
        liveMatch.totals[teamB.id] shouldBe 62
    }

    @Test
    fun `a capot round awards 250 to the achieving team and 0 to the other`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = BeloteRoundViewModel(liveMatch)
        val teamA = liveMatch.participants.first { it.teamID == "A" }
        val teamB = liveMatch.participants.first { it.teamID == "B" }

        viewModel.selectTaker("A")
        viewModel.updateCapot(true)
        viewModel.submit()

        liveMatch.totals[teamA.id] shouldBe 250
        liveMatch.totals[teamB.id] shouldBe 0
    }

    @Test
    fun `belote-rebelote adds 20 points to whichever team announced it`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = BeloteRoundViewModel(liveMatch)
        val teamB = liveMatch.participants.first { it.teamID == "B" }

        viewModel.selectTaker("A")
        viewModel.updateTakerPoints(100)
        viewModel.selectBeloteRebelote("B")
        viewModel.submit()

        liveMatch.totals[teamB.id] shouldBe 82 // 162 - 100 + 20
    }

    @Test
    fun `submitting resets the draft points back to the default`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = BeloteRoundViewModel(liveMatch)

        viewModel.selectTaker("A")
        viewModel.updateTakerPoints(140)
        viewModel.submit()

        viewModel.takerPoints shouldBe 82
        viewModel.isCapot shouldBe false
    }
}
