package com.quimene.sync

import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.Participant
import com.quimene.domain.model.VariantSelection
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.util.UUID

/** Miroir de `SessionIdentityTests.swift` (doc 16, phase D). */
class SessionIdentityTest {
    private val owner = "owner-device"
    private val marion = SeatRef(0, "Marion")
    private val theo = SeatRef(1, "Théo")

    private fun card(
        name: String,
        id: UUID = UUID.randomUUID(),
    ) = ProfileCard(id, name, "emoji", "🦊", "3")

    private fun records(vararg events: SessionIdentityEvent) =
        events.mapIndexed { index, event -> SessionIdentityRecord(index + 1L, event) }

    @Test
    fun `first claim wins`() {
        val first = card("Théo")
        val second = card("Intrus")
        val identities =
            SessionIdentities(
                records(SessionIdentityEvent.claim(theo, first, "a"), SessionIdentityEvent.claim(theo, second, "b")),
                owner,
            )
        identities.occupant(theo) shouldBe first.id
        identities.seatOf(second.id) shouldBe null
    }

    @Test
    fun `a seat linked to another profile by the owner cannot be claimed`() {
        val friend = UUID.randomUUID()
        val identities =
            SessionIdentities(
                records(
                    SessionIdentityEvent.roster(card("Erwan"), listOf(LinkedSeat(theo, friend)), owner),
                    SessionIdentityEvent.claim(theo, card("Théo"), "b"),
                ),
                owner,
            )
        identities.occupant(theo) shouldBe friend
        identities.seatOf(friend) shouldBe theo
        identities.activeClaims.shouldBeEmpty()
    }

    @Test
    fun `a roster from a participant is ignored`() {
        val identities =
            SessionIdentities(
                records(SessionIdentityEvent.roster(card("Faux"), listOf(LinkedSeat(theo, UUID.randomUUID())), "b")),
                owner,
            )
        identities.owner shouldBe null
        identities.occupant(theo) shouldBe null
    }

    @Test
    fun `revocation by the owner or the author, not by a third party`() {
        val theoCard = card("Théo")
        val claim = SessionIdentityEvent.claim(theo, theoCard, "theo-phone")
        SessionIdentities(records(claim, SessionIdentityEvent.revoke(claim.id, "autre")), owner)
            .occupant(theo) shouldBe theoCard.id
        SessionIdentities(records(claim, SessionIdentityEvent.revoke(claim.id, owner)), owner)
            .occupant(theo) shouldBe null
        SessionIdentities(records(claim, SessionIdentityEvent.revoke(claim.id, "theo-phone")), owner)
            .occupant(theo) shouldBe null
    }

    @Test
    fun `a profile holds one seat, the newer claim replaces the older`() {
        val theoCard = card("Théo")
        val identities =
            SessionIdentities(
                records(
                    SessionIdentityEvent.claim(marion, theoCard, "a"),
                    SessionIdentityEvent.claim(theo, theoCard, "a"),
                ),
                owner,
            )
        identities.seatOf(theoCard.id) shouldBe theo
        identities.occupant(marion) shouldBe null
    }

    @Test
    fun `identities share the log without disturbing match replay`() =
        runTest {
            val backend = InMemorySessionBackend()
            val sessionID = UUID.randomUUID()
            val matchID = UUID.randomUUID()
            val erwan = OnlineSession(sessionID, "042817", owner, backend)
            val theoPhone = OnlineSession(sessionID, "042817", "theo", backend)

            erwan.append(
                MatchEvent.MatchCreated(
                    "dummy",
                    1,
                    VariantSelection(),
                    listOf(Participant(displayName = "Marion", seatIndex = 0)),
                ),
                matchID,
                eventID = matchID,
            )
            theoPhone.sync()
            theoPhone.appendIdentity(SessionIdentityEvent.claim(marion, card("Marion"), "theo"), matchID)

            erwan.sync()
            erwan.records().size shouldBe 1
            erwan.identities().size shouldBe 1
            erwan.lastSeq shouldBe 2
            erwan.currentMatchID() shouldBe matchID
        }

    /** `spec/session/identity-events.json` est produit par le code Swift réel : Android doit
     * déchiffrer chaque événement et retrouver exactement celui décrit en clair. */
    @Test
    fun `identity events sealed by iOS decrypt to the exact same events`() {
        val fixture =
            Json
                .parseToJsonElement(
                    requireNotNull(
                        javaClass.classLoader.getResource("SessionResources/identity-events.json"),
                    ).readText(),
                ).jsonObject
        val key =
            SessionCrypto.deriveKey(
                fixture["pairingCode"]!!.jsonPrimitive.content,
                UUID.fromString(fixture["sessionID"]!!.jsonPrimitive.content),
            )
        val rows = fixture["events"]!!.jsonArray.map { it.jsonObject }
        rows.size shouldBe 3
        for (row in rows) {
            val raw =
                RawSessionEvent(
                    seq = row["seq"]!!.jsonPrimitive.int.toLong(),
                    eventID = UUID.randomUUID(),
                    matchID = UUID.randomUUID(),
                    deviceID = "ios-device",
                    ciphertext = row["ciphertext"]!!.jsonPrimitive.content,
                )
            OnlineSession.open(raw, key) shouldBe null
            val opened = requireNotNull(OnlineSession.openIdentity(raw, key)) { "identité iOS indéchiffrable" }
            val expected =
                OnlineSession.json.decodeFromString(
                    SessionIdentityEnvelope.serializer(),
                    row["plaintext"]!!.jsonPrimitive.content,
                )
            opened.event shouldBe expected.identity
        }
        val kinds =
            rows.map {
                OnlineSession
                    .openIdentity(
                        RawSessionEvent(
                            0,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "",
                            it["ciphertext"]!!.jsonPrimitive.content,
                        ),
                        key,
                    )!!
                    .event.kind
            }
        kinds shouldBe
            listOf(SessionIdentityEvent.Kind.Claim, SessionIdentityEvent.Kind.Roster, SessionIdentityEvent.Kind.Revoke)
    }
}
