package com.quimene.domain.rules

import com.quimene.domain.testing.TestGameRules
import com.quimene.domain.testing.testDefinition
import com.quimene.domain.testing.testMatchState
import com.quimene.domain.testing.testParticipant
import com.quimene.domain.testing.withRound
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Départage par défaut de [GameRules] — exercé via [TestGameRules] (aucune surcharge, comme
 * `GenericSumRules` en production) plutôt que via `:catalog` (mauvais sens de dépendance).
 */
class GameRulesTieBreakTest {
    @Test
    fun `shared tie-break keeps tied participants at the same rank`() {
        val definition = testDefinition(direction = Direction.HighestWins, tieBreak = listOf(TieBreakRule.Shared))
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 10, bob.id to 10)

        val standings = TestGameRules(definition.engine).standings(state, definition)

        val aliceStanding = standings.single { it.participantID == alice.id }
        val bobStanding = standings.single { it.participantID == bob.id }
        aliceStanding.rank shouldBe 1
        bobStanding.rank shouldBe 1
        aliceStanding.sharedWith shouldContainExactly listOf(bob.id)
        bobStanding.sharedWith shouldContainExactly listOf(alice.id)
    }

    @Test
    fun `bestSingleRound tie-break resolves a total tie before falling back to shared`() {
        val definition =
            testDefinition(
                direction = Direction.HighestWins,
                tieBreak = listOf(TieBreakRule.BestSingleRound, TieBreakRule.Shared),
            )
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        // Même total (10) mais un meilleur tour différent : Alice a joué 10 en une manche,
        // Bob a réparti 5/5 — Alice doit passer devant, pas de rang partagé.
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 10, bob.id to 5)
                .withRound(1, alice.id to 0, bob.id to 5)

        val standings = TestGameRules(definition.engine).standings(state, definition)

        val aliceStanding = standings.single { it.participantID == alice.id }
        val bobStanding = standings.single { it.participantID == bob.id }
        aliceStanding.rank shouldBe 1
        bobStanding.rank shouldBe 2
        aliceStanding.sharedWith shouldBe emptyList()
        bobStanding.sharedWith shouldBe emptyList()
    }

    @Test
    fun `standings order participants by total according to direction`() {
        val definition = testDefinition(direction = Direction.LowestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 20, bob.id to 5)

        val standings = TestGameRules(definition.engine).standings(state, definition)

        standings.single { it.participantID == bob.id }.rank shouldBe 1
        standings.single { it.participantID == alice.id }.rank shouldBe 2
    }
}
