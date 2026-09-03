package com.cacompte.app.features.livematch.wizard

import com.cacompte.app.RoomTestBase
import com.cacompte.app.features.livematch.LiveMatchViewModel
import com.cacompte.catalog.GameCatalogEmbedded
import com.cacompte.domain.model.VariantSelection
import com.cacompte.store.MatchRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Vérifie le câblage entre [WizardRoundViewModel] et le moteur déjà testé (`:catalog`,
 * `WizardRulesV1Test`) — construction du `WizardBidDetail` par participant, et la validation
 * croisée (somme des plis réalisés = numéro de la manche). */
class WizardRoundViewModelTest : RoomTestBase() {
    private val catalog = GameCatalogEmbedded.embedded
    private val definition = catalog.definition("wizard", 1)
    private val rules = catalog.rules("wizard", 1)
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
                            MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1"),
                            MatchRepository.ParticipantSeed(null, "Bob", "emoji", "🐻", "2"),
                            MatchRepository.ParticipantSeed(null, "Carol", "emoji", "🐼", "3"),
                        ),
                )
            }
        return LiveMatchViewModel(match, definition, rules, catalog, repository, "local")
    }

    @Test
    fun `an exact bid on round 1 scores 20 plus 10 times the bid`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = WizardRoundViewModel(liveMatch)
        val alice = liveMatch.participants[0]
        val bob = liveMatch.participants[1]
        val carol = liveMatch.participants[2]

        // Round 1 : un seul pli à distribuer au total.
        viewModel.setBid(alice.id, 1)
        viewModel.setResult(alice.id, 1)
        viewModel.setBid(bob.id, 0)
        viewModel.setResult(bob.id, 0)
        viewModel.setBid(carol.id, 0)
        viewModel.setResult(carol.id, 0)
        viewModel.submit()

        liveMatch.validationErrorMessage shouldBe null
        liveMatch.totals[alice.id] shouldBe 30 // 20 + 10*1
        liveMatch.totals[bob.id] shouldBe 20 // 20 + 10*0
    }

    @Test
    fun `a missed bid scores minus 10 times the difference`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = WizardRoundViewModel(liveMatch)
        val alice = liveMatch.participants[0]
        val bob = liveMatch.participants[1]
        val carol = liveMatch.participants[2]

        viewModel.setBid(alice.id, 1)
        viewModel.setResult(alice.id, 0)
        viewModel.setBid(bob.id, 0)
        viewModel.setResult(bob.id, 1)
        viewModel.setBid(carol.id, 0)
        viewModel.setResult(carol.id, 0)
        viewModel.submit()

        liveMatch.totals[alice.id] shouldBe -10 // annonce 1, réalisé 0
    }

    @Test
    fun `a total of tricks that doesn't match the round number is rejected`() {
        val liveMatch = liveMatchViewModel()
        val viewModel = WizardRoundViewModel(liveMatch)
        val alice = liveMatch.participants[0]
        val bob = liveMatch.participants[1]
        val carol = liveMatch.participants[2]

        // Round 1 exige un total de 1 pli — en soumettre 0 doit être rejeté.
        viewModel.setBid(alice.id, 0)
        viewModel.setResult(alice.id, 0)
        viewModel.setBid(bob.id, 0)
        viewModel.setResult(bob.id, 0)
        viewModel.setBid(carol.id, 0)
        viewModel.setResult(carol.id, 0)
        viewModel.submit()

        liveMatch.validationErrorMessage.shouldNotBeNull()
        liveMatch.totals.values.all { it == 0 } shouldBe true
    }
}
