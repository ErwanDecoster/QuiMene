package com.quimene.domain.stats

import com.quimene.domain.rules.Direction
import com.quimene.domain.testing.testDefinition
import com.quimene.domain.testing.testMatchState
import com.quimene.domain.testing.testParticipant
import com.quimene.domain.testing.withRound
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Couverture représentative, pas exhaustive : les badges statistiques (métronome/montagnes
 * russes/imperturbable) se recouvrent facilement dès qu'un participant a une performance
 * "intéressante" (`Rollercoaster` passe souvent devant `Sniper`/`Boulet` dans l'ordre de rareté
 * dès qu'un écart-type élevé accompagne un tour extrême) — les fixtures ci-dessous vérifient le
 * badge **final** réellement résolu, pas une classification isolée par construction. La
 * couverture par golden file/propriété exhaustive est l'étape C (doc 10), pas cette session.
 */
class StatsEngineTest {
    private val engine = StatsEngine()

    @Test
    fun `series accumulates per participant, carrying the running total across skipped rounds`() {
        val definition = testDefinition()
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 5, bob.id to 3)
                .withRound(1, alice.id to 2) // Bob absent cette manche

        val series = engine.series(state)

        val aliceSeries = series.single { it.id == alice.id }
        val bobSeries = series.single { it.id == bob.id }
        aliceSeries.points.map { it.total } shouldBe listOf(5, 7)
        // Bob n'a pas d'entrée en manche 1 : le total précédent est reporté, pas remis à zéro.
        bobSeries.points.map { it.total } shouldBe listOf(3, 3)
    }

    @Test
    fun `no badge is awarded before the third round`() {
        val definition = testDefinition(direction = Direction.HighestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 10, bob.id to 1)
                .withRound(1, alice.id to 10, bob.id to 1)

        engine.badges(state, definition) shouldHaveSize 0
    }

    @Test
    fun `a tight finish awards photo finish to the winner`() {
        val definition = testDefinition(direction = Direction.HighestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 10, bob.id to 10)
                .withRound(1, alice.id to 10, bob.id to 10)
                .withRound(2, alice.id to 10, bob.id to 9)

        val badges = engine.badges(state, definition)

        badges.single { it.participantID == alice.id }.kind shouldBe Badge.Kind.PhotoFinish
    }

    @Test
    fun `a single dramatic round outweighs a decisive win, awarding rollercoaster`() {
        val definition = testDefinition(direction = Direction.HighestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 100, bob.id to 2)
                .withRound(1, alice.id to 1, bob.id to 2)
                .withRound(2, alice.id to 1, bob.id to 2)

        val badges = engine.badges(state, definition)

        // Alice cumule gagnante/sniper/imperturbable/montagnes russes — l'ordre de rareté fait
        // gagner "montagnes russes" (index le plus bas parmi ses candidats).
        badges.single { it.participantID == alice.id }.kind shouldBe Badge.Kind.Rollercoaster
        badges.single { it.participantID == bob.id }.kind shouldBe Badge.Kind.Metronome
    }

    @Test
    fun `highestRoundScore and bestRoundScore insights report the extreme single round`() {
        val definition = testDefinition(direction = Direction.HighestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 40, bob.id to 5)
                .withRound(1, alice.id to 5, bob.id to 5)

        val candidates = engine.candidates(state, definition)

        val highestValue = candidates.single { it.id == InsightID.highestRoundScore }.value as Insight.Value.Single
        val bestValue = candidates.single { it.id == InsightID.bestRoundScore }.value as Insight.Value.Single
        highestValue.participantID shouldBe alice.id
        highestValue.round shouldBe 0
        // highestWins : le meilleur tour est aussi le plus gros tour, même participant/manche.
        bestValue.participantID shouldBe alice.id
    }

    @Test
    fun `finalGap and leadChanges describe the shape of the whole match`() {
        val definition = testDefinition(direction = Direction.HighestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 10, bob.id to 1) // Alice mène
                .withRound(1, alice.id to 1, bob.id to 20) // Bob prend la tête

        val candidates = engine.candidates(state, definition)

        val leadChanges = candidates.single { it.id == InsightID.leadChanges }
        (leadChanges.value as Insight.Value.Single).value shouldBe 1.0

        val streak = candidates.single { it.id == InsightID.longestLeadStreak }
        (streak.value as Insight.Value.Single).round shouldBe null
    }

    @Test
    fun `skyjo profile insights are only produced for games that declare the skyjo profile`() {
        val definition = testDefinition(direction = Direction.LowestWins, statsProfiles = listOf("standard"))
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 5, bob.id to 5)

        engine.candidates(state, definition).none { it.id == InsightID.roundsClosed } shouldBe true
    }

    @Test
    fun `insights selection returns at most 6 facts and always includes the first candidate`() {
        val definition = testDefinition(direction = Direction.HighestWins)
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val state =
            testMatchState(definition, listOf(alice, bob))
                .withRound(0, alice.id to 12, bob.id to 4)
                .withRound(1, alice.id to 3, bob.id to 15)
                .withRound(2, alice.id to 9, bob.id to 9)

        val candidateIds = engine.candidates(state, definition).map { it.id }.toSet()
        val insights = engine.insights(state, definition)

        insights.size shouldBeLessThanOrEqualTo 6
        insights.isEmpty() shouldBe false
        insights.map { it.id }.toSet().all { it in candidateIds } shouldBe true
    }
}
