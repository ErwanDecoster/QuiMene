package com.cacompte.app.features.livematch

import com.cacompte.app.RoomTestBase
import com.cacompte.app.testing.testCatalog
import com.cacompte.app.testing.testGameDefinition
import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.model.VariantSelection
import com.cacompte.domain.rules.GameRules
import com.cacompte.store.MatchRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

/** Miroir de la logique observée dans `LiveMatchModel.swift` — état de la manche en cours,
 * validation avant écriture, remise à zéro des champs après un commit. `:store` teste déjà
 * `MatchRepository` en profondeur (rejeu, undo, abandon) ; ces tests couvrent uniquement ce que
 * le ViewModel ajoute par-dessus (saisie en attente, focus, messages d'erreur/d'explication). */
class LiveMatchViewModelTest : RoomTestBase() {
    private val definition = testGameDefinition()
    private val catalog = testCatalog(definition)
    private val rules: GameRules = TestRules
    private val repository by lazy { MatchRepository(db.matchDao(), db.participantDao(), db.playerDao()) }

    private object TestRules : GameRules {
        override val engineID: String = "test.v1"
    }

    private fun viewModel(): LiveMatchViewModelHarness {
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
                        ),
                )
            }
        val vm = LiveMatchViewModel(match, definition, rules, catalog, repository, "local")
        val aliceID = vm.participants.first { it.displayName == "Alice" }.id
        val bobID = vm.participants.first { it.displayName == "Bob" }.id
        return LiveMatchViewModelHarness(vm, aliceID, bobID)
    }

    private data class LiveMatchViewModelHarness(
        val vm: LiveMatchViewModel,
        val aliceID: UUID,
        val bobID: UUID,
    )

    @Test
    fun `loading the match populates participants and totals from the persisted log`() {
        val (vm) = viewModel()

        vm.participants.map { it.displayName } shouldBe listOf("Alice", "Bob")
        vm.totals.values.all { it == 0 } shouldBe true
        vm.isConcluded shouldBe false
    }

    @Test
    fun `setScore then commitRound persists the round and clears pending state`() {
        val (vm, aliceID, bobID) = viewModel()
        vm.setScore(aliceID, 10)
        vm.setScore(bobID, 5)

        vm.commitRound()

        vm.totals[aliceID] shouldBe 10
        vm.totals[bobID] shouldBe 5
        vm.pendingScores shouldBe emptyMap()
    }

    @Test
    fun `clearScore removes a single pending value without touching the others`() {
        val (vm, aliceID, bobID) = viewModel()
        vm.setScore(aliceID, 10)
        vm.setScore(bobID, 5)

        vm.clearScore(aliceID)

        vm.pendingScores.containsKey(aliceID) shouldBe false
        vm.pendingScores[bobID] shouldBe 5
    }

    @Test
    fun `focus moves the active seat index to the tapped participant`() {
        val (vm, _, bobID) = viewModel()

        vm.focus(bobID)

        vm.activeSeatIndex shouldBe 1
        vm.currentParticipant?.id shouldBe bobID
    }

    @Test
    fun `unscored participants default to zero when a round is committed`() {
        val (vm, aliceID, bobID) = viewModel()
        vm.setScore(aliceID, 10) // Bob n'a rien saisi.

        vm.commitRound()

        vm.totals[aliceID] shouldBe 10
        vm.totals[bobID] shouldBe 0
    }

    @Test
    fun `undoLastRound removes the round and restores the previous totals`() {
        val (vm, aliceID) = viewModel()
        vm.setScore(aliceID, 10)
        vm.commitRound()
        vm.totals[aliceID] shouldBe 10

        vm.undoLastRound()

        vm.totals[aliceID] shouldBe 0
    }

    @Test
    fun `canEndManually is false until at least one round has been committed`() {
        val (vm, aliceID) = viewModel()
        vm.canEndManually shouldBe false

        vm.setScore(aliceID, 10)
        vm.commitRound()

        vm.canEndManually shouldBe true
    }

    @Test
    fun `endManually concludes the match`() {
        val (vm, aliceID) = viewModel()
        vm.setScore(aliceID, 10)
        vm.commitRound()

        vm.endManually()

        vm.isConcluded shouldBe true
        vm.stateOrNull?.status shouldBe MatchStatus.Ended
    }

    @Test
    fun `abandon concludes the match with an abandoned status`() {
        val (vm) = viewModel()

        vm.abandon()

        vm.isConcluded shouldBe true
        vm.stateOrNull?.status shouldBe MatchStatus.Abandoned
    }

    @Test
    fun `roundExplanationMessage stays null when no round entry carries an explanation`() {
        val (vm, aliceID) = viewModel()
        vm.setScore(aliceID, 10)

        vm.commitRound()

        // Le moteur générique de test ne produit jamais d'explication de manche.
        vm.roundExplanationMessage.shouldBeNull()
    }

    @Test
    fun `validationErrorMessage is cleared as soon as a score is set again`() {
        val strictDefinition =
            testGameDefinition().let {
                it.copy(scoring = it.scoring.copy(entry = it.scoring.entry.copy(min = 0, max = 5)))
            }
        val strictCatalog = testCatalog(strictDefinition)
        val match =
            runBlocking {
                repository.createMatch(
                    gameID = strictDefinition.id,
                    rulesVersion = strictDefinition.rulesVersion,
                    variants = VariantSelection(),
                    seeds = listOf(MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1")),
                )
            }
        val vm = LiveMatchViewModel(match, strictDefinition, TestRules, strictCatalog, repository, "local")
        val aliceID = vm.participants.single().id

        vm.setScore(aliceID, 99) // au-delà du maximum autorisé (5).
        vm.commitRound()
        vm.validationErrorMessage.shouldNotBeNull()

        vm.setScore(aliceID, 3)
        vm.validationErrorMessage.shouldBeNull()
    }
}
