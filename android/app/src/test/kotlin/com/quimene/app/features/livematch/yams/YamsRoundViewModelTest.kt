package com.quimene.app.features.livematch.yams

import com.quimene.app.RoomTestBase
import com.quimene.app.features.livematch.LiveMatchViewModel
import com.quimene.catalog.GameCatalogEmbedded
import com.quimene.domain.model.VariantSelection
import com.quimene.store.MatchRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Vérifie le câblage entre [YamsRoundViewModel] et le moteur déjà testé (`:catalog`,
 * `YamsRulesV1Test`) — construction du `YamsCategoryDetail`, suivi des catégories déjà remplies,
 * pas la formule de score elle-même. */
class YamsRoundViewModelTest : RoomTestBase() {
    private val catalog = GameCatalogEmbedded.embedded
    private val definition = catalog.definition("yams", 1)
    private val rules = catalog.rules("yams", 1)
    private val repository by lazy { MatchRepository(db.matchDao(), db.participantDao(), db.playerDao()) }

    private fun liveMatchViewModel(): LiveMatchViewModel {
        val match =
            runBlocking {
                repository.createMatch(
                    gameID = definition.id,
                    rulesVersion = definition.rulesVersion,
                    variants = VariantSelection(),
                    seeds = listOf(MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1")),
                )
            }
        return LiveMatchViewModel(match, definition, rules, catalog, repository, "local")
    }

    @Test
    fun `filling the fours category with 3 dice scores 12`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = YamsRoundViewModel(liveMatch)
        val alice = liveMatch.participants.single()

        viewModel.submit(alice.id, "fours", rawValue = 3)

        liveMatch.validationErrorMessage shouldBe null
        liveMatch.totals[alice.id] shouldBe 12
        viewModel.filledEntries(alice.id)["fours"]?.computedValue shouldBe 12
    }

    @Test
    fun `filling the same category twice is rejected`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = YamsRoundViewModel(liveMatch)
        val alice = liveMatch.participants.single()
        viewModel.submit(alice.id, "fours", rawValue = 3)

        viewModel.submit(alice.id, "fours", rawValue = 2)

        liveMatch.validationErrorMessage.shouldNotBeNull()
        viewModel.filledEntries(alice.id)["fours"]?.computedValue shouldBe 12 // inchangé
    }

    @Test
    fun `a fixed category records the flat value only when achieved`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = YamsRoundViewModel(liveMatch)
        val alice = liveMatch.participants.single()

        viewModel.submit(alice.id, "yams", rawValue = 1)

        liveMatch.totals[alice.id] shouldBe 50
    }
}
