package com.quimene.domain.engine

import com.quimene.domain.model.VariantSelection
import com.quimene.domain.testing.roundDraft
import com.quimene.domain.testing.testCatalog
import com.quimene.domain.testing.testDefinition
import com.quimene.domain.testing.testParticipant
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MatchEngineTest {
    @Test
    fun `replay requires the log to start with matchCreated`() {
        val definition = testDefinition()
        val catalog = testCatalog(definition)
        val engine = MatchEngine()

        val log =
            listOf(
                StampedEvent(
                    lamport = 1u,
                    deviceID = "device-a",
                    occurredAt = Instant.EPOCH,
                    event = MatchEvent.MatchEndedManually,
                ),
            )

        shouldThrow<MatchEngineError.MissingMatchCreated> { engine.replay(log, catalog) }
    }

    @Test
    fun `replay deduplicates events by id, keeping the first occurrence`() {
        val definition = testDefinition()
        val catalog = testCatalog(definition)
        val engine = MatchEngine()
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val sharedId = UUID.randomUUID()

        val created =
            StampedEvent(
                id = sharedId,
                lamport = 0u,
                deviceID = "device-a",
                occurredAt = Instant.EPOCH,
                event =
                    MatchEvent.MatchCreated(
                        gameID = definition.id,
                        rulesVersion = definition.rulesVersion,
                        variants = VariantSelection(),
                        participants = listOf(alice, bob),
                    ),
            )
        // Même id que `created` mais un événement différent : ne doit jamais être appliqué,
        // seule la première occurrence par id compte (idempotence du rejeu).
        val duplicateOfCreated = created.copy(event = MatchEvent.MatchEndedManually)

        val state = engine.replay(listOf(created, duplicateOfCreated), catalog)

        state.status.name shouldBe "InProgress"
    }

    @Test
    fun `replay orders events by lamport then deviceID, independent of log order`() {
        val definition = testDefinition()
        val catalog = testCatalog(definition)
        val engine = MatchEngine()
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)

        val created =
            StampedEvent(
                lamport = 0u,
                deviceID = "device-a",
                occurredAt = Instant.EPOCH,
                event =
                    MatchEvent.MatchCreated(
                        gameID = definition.id,
                        rulesVersion = definition.rulesVersion,
                        variants = VariantSelection(),
                        participants = listOf(alice, bob),
                    ),
            )
        val roundZero =
            StampedEvent(
                lamport = 1u,
                deviceID = "device-b",
                occurredAt = Instant.EPOCH,
                event = MatchEvent.RoundCommitted(roundDraft(0, alice.id to 10, bob.id to 5)),
            )
        val roundOne =
            StampedEvent(
                lamport = 2u,
                deviceID = "device-a",
                occurredAt = Instant.EPOCH,
                event = MatchEvent.RoundCommitted(roundDraft(1, alice.id to 3, bob.id to 7)),
            )

        // Log volontairement dans le désordre — le rejeu doit produire le même résultat quel
        // que soit l'ordre d'arrivée (commutativité, ADR-0005).
        val outOfOrderState = engine.replay(listOf(roundOne, created, roundZero), catalog)
        val inOrderState = engine.replay(listOf(created, roundZero, roundOne), catalog)

        outOfOrderState.totals() shouldBe inOrderState.totals()
        outOfOrderState.totals()[alice.id] shouldBe 13
        outOfOrderState.totals()[bob.id] shouldBe 12
    }

    @Test
    fun `reduce applies a single event using the injected clock`() {
        val definition = testDefinition()
        val alice = testParticipant("Alice", 0)
        val bob = testParticipant("Bob", 1)
        val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")
        val engine = MatchEngine(now = { fixedInstant })
        val rules =
            com.quimene.domain.testing
                .TestGameRules(definition.engine)

        val initial =
            com.quimene.domain.model.MatchState.create(
                matchID = UUID.randomUUID(),
                gameID = definition.id,
                rulesVersion = definition.rulesVersion,
                variants = VariantSelection(),
                participants = listOf(alice, bob),
            )

        val updated =
            engine.reduce(
                initial,
                MatchEvent.RoundCommitted(roundDraft(0, alice.id to 4, bob.id to 6)),
                rules,
                definition,
            )

        updated.rounds.single().committedAt shouldBe fixedInstant
        updated.totals()[alice.id] shouldBe 4
    }
}
