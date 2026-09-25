package com.quimene.sync

import com.quimene.domain.engine.MatchEngine
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.End
import com.quimene.domain.rules.EndCondition
import com.quimene.domain.rules.EndConditionType
import com.quimene.domain.rules.EntryKind
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import com.quimene.domain.rules.LocalizedText
import com.quimene.domain.rules.Players
import com.quimene.domain.rules.ScoreEntrySpec
import com.quimene.domain.rules.Scoring
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Mêmes règles que les fonctions SQL de `create_quimene_sessions` — miroir de
 * `InMemorySessionBackend` (Swift). */
internal class InMemorySessionBackend : OnlineSessionBackend {
    private val mutex = Mutex()
    private val events = mutableMapOf<UUID, MutableList<RawSessionEvent>>()

    override suspend fun open(
        sessionID: UUID,
        pairingCode: String,
        ownerDeviceID: String,
        allowsContributors: Boolean,
    ) {
        mutex.withLock { events.getOrPut(sessionID) { mutableListOf() } }
    }

    override suspend fun resolve(pairingCode: String): OnlineSessionInfo? = null

    override suspend fun append(
        sessionID: UUID,
        expectedSeq: Long,
        eventID: UUID,
        matchID: UUID,
        deviceID: String,
        ciphertext: String,
    ): Long =
        mutex.withLock {
            val log = events.getOrPut(sessionID) { mutableListOf() }
            log.firstOrNull { it.eventID == eventID }?.let { return@withLock it.seq }
            val next = log.size + 1L
            if (expectedSeq != next) throw OnlineSessionError.StaleSequence
            log += RawSessionEvent(next, eventID, matchID, deviceID, ciphertext)
            next
        }

    override suspend fun events(
        sessionID: UUID,
        afterSeq: Long,
    ): List<RawSessionEvent> =
        mutex.withLock {
            events[sessionID].orEmpty().filter { it.seq > afterSeq }.take(OnlineSession.PAGE_SIZE)
        }

    override suspend fun close(
        sessionID: UUID,
        ownerDeviceID: String,
    ) = Unit

    suspend fun injectGarbage(sessionID: UUID) {
        mutex.withLock {
            val log = events.getOrPut(sessionID) { mutableListOf() }
            log += RawSessionEvent(log.size + 1L, UUID.randomUUID(), UUID.randomUUID(), "intrus", "cGFzIGNoaWZmcsOp")
        }
    }
}

private class TestingRules(
    override val engineID: String = ENGINE_ID,
) : GameRules {
    companion object {
        const val ENGINE_ID = "test.online.v1"
    }
}

private val testingCatalog =
    GameCatalog(
        definitions =
            listOf(
                GameDefinition(
                    id = "dummy",
                    specVersion = 1,
                    rulesVersion = 1,
                    name = LocalizedText(fr = "Test"),
                    symbol = "circle",
                    players = Players(min = 2, max = 8),
                    scoring =
                        Scoring(
                            direction = Direction.LowestWins,
                            entry = ScoreEntrySpec(kind = EntryKind.Integer),
                        ),
                    engine = TestingRules.ENGINE_ID,
                    end = End(conditions = listOf(EndCondition(type = EndConditionType.RoundLimit, value = 1000))),
                ),
            ),
        engineTable = mapOf(TestingRules.ENGINE_ID to { TestingRules() }),
    )

/** Miroir de `OnlineSessionTests.swift` (doc 16, phase C). */
class OnlineSessionTest {
    private val participants =
        listOf(
            Participant(displayName = "Marion", seatIndex = 0),
            Participant(displayName = "Théo", seatIndex = 1),
        )

    private fun created() = MatchEvent.MatchCreated("dummy", 1, VariantSelection(), participants)

    private fun round(
        index: Int,
        value: Int,
    ) = MatchEvent.RoundCommitted(
        RoundDraft(index = index, inputs = participants.map { ScoreInput(participantID = it.id, rawValue = value) }),
    )

    @Test
    fun `two devices share the same log, ordered by the server`() =
        runTest {
            val backend = InMemorySessionBackend()
            val sessionID = UUID.randomUUID()
            val matchID = UUID.randomUUID()
            val erwan = OnlineSession(sessionID, "042817", "erwan", backend)
            val theo = OnlineSession(sessionID, "042817", "theo", backend)

            erwan.append(created(), matchID)
            theo.sync()
            theo.append(round(0, 7), matchID)
            val fresh = erwan.sync()

            fresh.map { it.event.deviceID } shouldBe listOf("theo")
            val events = erwan.eventsForMatch(matchID)
            events.map { it.lamport } shouldBe listOf(1uL, 2uL)
            theo.eventsForMatch(matchID) shouldBe events
        }

    @Test
    fun `an overtaken append is refused after catching up`() =
        runTest {
            val backend = InMemorySessionBackend()
            val sessionID = UUID.randomUUID()
            val matchID = UUID.randomUUID()
            val erwan = OnlineSession(sessionID, "042817", "erwan", backend)
            val theo = OnlineSession(sessionID, "042817", "theo", backend)

            erwan.append(created(), matchID)
            theo.sync()
            erwan.append(round(0, 10), matchID)

            shouldThrow<OnlineSessionError.StaleSequence> { theo.append(round(0, 1), matchID) }
            theo.lastSeq shouldBe 2
            val state = MatchEngine().replay(theo.eventsForMatch(matchID), testingCatalog)
            state.rounds.size shouldBe 1

            theo.append(round(state.nextRoundIndex, 1), matchID)
            theo.lastSeq shouldBe 3
        }

    @Test
    fun `an undecryptable event is skipped but counts in the numbering`() =
        runTest {
            val backend = InMemorySessionBackend()
            val sessionID = UUID.randomUUID()
            val matchID = UUID.randomUUID()
            OnlineSession(sessionID, "042817", "erwan", backend).append(created(), matchID)
            backend.injectGarbage(sessionID)

            val late = OnlineSession(sessionID, "042817", "late", backend)
            late.sync()
            late.records().size shouldBe 1
            late.lastSeq shouldBe 2

            val wrongCode = OnlineSession(sessionID, "000000", "x", backend)
            wrongCode.sync()
            wrongCode.records().shouldBeEmpty()
        }

    @Test
    fun `sync pages beyond a 500-event batch`() =
        runTest {
            val backend = InMemorySessionBackend()
            val sessionID = UUID.randomUUID()
            val matchID = UUID.randomUUID()
            val writer = OnlineSession(sessionID, "042817", "w", backend)
            writer.append(created(), matchID)
            repeat(1_100) { writer.append(MatchEvent.NoteAdded(it, "n"), matchID) }
            val reader = OnlineSession(sessionID, "042817", "r", backend)
            reader.sync().size shouldBe 1_101
        }

    @Test
    fun `a published match keeps the id of its local matchCreated`() =
        runTest {
            val backend = InMemorySessionBackend()
            val sessionID = UUID.randomUUID()
            val local = StampedEvent(lamport = 1uL, deviceID = "erwan", occurredAt = Instant.now(), event = created())
            OnlineSession(sessionID, "042817", "erwan", backend)
                .append(local.event, local.id, eventID = local.id, occurredAt = local.occurredAt)

            val theo = OnlineSession(sessionID, "042817", "theo", backend)
            theo.sync()
            MatchEngine().replay(theo.eventsForMatch(local.id), testingCatalog).matchID shouldBe local.id
            theo.currentMatchID() shouldBe local.id
        }

    /** `spec/session/sealed-events.json` est produit par le code Swift réel (`OnlineSession.seal`) :
     * Android doit déchiffrer chaque événement et retrouver exactement celui décrit en clair. */
    @Test
    fun `events sealed by iOS decrypt to the exact same events`() {
        val fixture =
            Json
                .parseToJsonElement(
                    requireNotNull(javaClass.classLoader.getResource("SessionResources/sealed-events.json")).readText(),
                ).jsonObject
        val code = fixture["pairingCode"]!!.jsonPrimitive.content
        val sessionID = UUID.fromString(fixture["sessionID"]!!.jsonPrimitive.content)
        val key = SessionCrypto.deriveKey(code, sessionID)

        for (row in fixture["events"]!!.jsonArray.map { it.jsonObject }) {
            val raw =
                RawSessionEvent(
                    seq = row["seq"]!!.jsonPrimitive.int.toLong(),
                    eventID = UUID.randomUUID(),
                    matchID = UUID.fromString(row["matchID"]!!.jsonPrimitive.content),
                    deviceID = "ios-device",
                    ciphertext = row["ciphertext"]!!.jsonPrimitive.content,
                )
            val opened = requireNotNull(OnlineSession.open(raw, key)) { "événement iOS indéchiffrable" }
            val expected =
                OnlineSession.json.decodeFromString(StampedEvent.serializer(), row["plaintext"]!!.jsonPrimitive.content)
            opened.event shouldBe expected
        }
    }
}
