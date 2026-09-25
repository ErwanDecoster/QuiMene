package com.quimene.sync

import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreDetail
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.SharedMatchPackage
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.model.VariantValue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Doc 16, phase G — compatibilité croisée dans l'autre sens : `spec/session/android-*.json` sont
 * produits par le code Kotlin réel (`QUIMENE_WRITE_SPEC=1 ./gradlew :sync:test`), relus ici et par
 * `AndroidFixtureTests` (Swift), qui reconstruit indépendamment les mêmes valeurs et doit les
 * retrouver à l'identique. Couvre tout ce qu'Android écrit et qu'un iPhone lit : événements de
 * partie (toutes les sortes), identités, boîte aux lettres, mise à jour d'écran verrouillé.
 */
class AndroidFixturesTest {
    private val code = "042817"
    private val sessionID = UUID.fromString("5A1E2B3C-4D5E-4F60-8172-8394A5B6C7D8")
    private val matchID = UUID.fromString("DDDDDDDD-0000-4000-8000-000000000001")
    private val marion = Participant(UUID.fromString("11111111-1111-4111-8111-111111111111"), "Marion", 0, teamID = "A")
    private val theo = Participant(UUID.fromString("22222222-2222-4222-8222-222222222222"), "Théo", 1)
    private val profileID = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val device = "android-device"

    private fun at(seconds: Long): Instant = Instant.ofEpochSecond(978_307_200L + 800_000_000L + seconds)

    private fun id(n: Int) = UUID.fromString("DDDDDDDD-0000-4000-8000-%012d".format(n))

    private val events: List<StampedEvent>
        get() {
            val round =
                RoundDraft(
                    index = 0,
                    inputs =
                        listOf(
                            ScoreInput(marion.id, 12, modifiers = setOf(ModifierID.closedRound)),
                            ScoreInput(theo.id, -3, detail = ScoreDetail(byteArrayOf(1, 2, 3))),
                        ),
                    note = "Première manche",
                )
            val variants =
                VariantSelection(
                    mapOf(
                        "threshold" to VariantValue.IntValue(100),
                        "doublePenalty" to VariantValue.BoolValue(true),
                        "mode" to VariantValue.StringValue("classic"),
                    ),
                )
            val kinds =
                listOf(
                    MatchEvent.MatchCreated("skyjo", 1, variants, listOf(marion, theo)),
                    MatchEvent.RoundCommitted(round),
                    MatchEvent.RoundAmended(0, round.copy(note = null)),
                    MatchEvent.NoteAdded(0, "Belle manche"),
                    MatchEvent.RoundRemoved(0),
                    MatchEvent.MatchEndedManually,
                    MatchEvent.MatchAbandoned(at(400)),
                )
            return kinds.mapIndexed { index, event ->
                StampedEvent(
                    id = if (index == 0) matchID else id(index + 1),
                    lamport = (index + 1).toULong(),
                    deviceID = device,
                    occurredAt = at(index * 60L),
                    event = event,
                )
            }
        }

    private val identities: List<SessionIdentityEvent>
        get() {
            val claim =
                SessionIdentityEvent(
                    id = id(101),
                    deviceID = device,
                    occurredAt = at(0),
                    kind = SessionIdentityEvent.Kind.Claim,
                    seat = SeatRef(1, "Théo"),
                    profile = ProfileCard(profileID, "Théo", "emoji", "🦊", "4"),
                )
            return listOf(
                claim,
                SessionIdentityEvent(
                    id = id(102),
                    deviceID = device,
                    occurredAt = at(30),
                    kind = SessionIdentityEvent.Kind.Roster,
                    profile =
                        ProfileCard(
                            UUID.fromString("44444444-4444-4444-8444-444444444444"),
                            "Erwan",
                            "emoji",
                            "🐻",
                            "1",
                        ),
                    linkedSeats =
                        listOf(
                            LinkedSeat(SeatRef(0, "Marion"), UUID.fromString("44444444-4444-4444-8444-444444444444")),
                        ),
                ),
                SessionIdentityEvent(
                    id = id(103),
                    deviceID = device,
                    occurredAt = at(60),
                    kind = SessionIdentityEvent.Kind.Revoke,
                    revokedClaimID = claim.id,
                ),
            )
        }

    private val pkg: SharedMatchPackage
        get() =
            SharedMatchPackage(
                matchID = matchID,
                participants =
                    listOf(
                        SharedMatchPackage.Participant(marion.id, null, "Marion", "emoji", "🐼", "2"),
                        SharedMatchPackage.Participant(theo.id, profileID, "Théo", "emoji", "🦊", "4"),
                    ),
                events = events.take(2),
            )

    private val lockScreen =
        LiveActivityContent(
            matchID = matchID,
            gameName = "Skyjo",
            gameSymbol = "square.grid.3x3.fill",
            roundNumber = 1,
            standings =
                listOf(
                    LiveActivityContent.Standing(theo.id, "Théo", -3),
                    LiveActivityContent.Standing(marion.id, "Marion", 12),
                ),
        )

    private fun specFile(name: String) = File(System.getProperty("user.dir"), "../../spec/session/$name")

    private fun writeIfAsked(
        name: String,
        document: JsonObject,
    ) {
        if (System.getenv("QUIMENE_WRITE_SPEC") != "1") return
        specFile(name).writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), document) + "\n")
    }

    private fun read(name: String): JsonObject =
        Json
            .parseToJsonElement(
                requireNotNull(javaClass.classLoader.getResource("SessionResources/$name")) {
                    "$name absent : QUIMENE_WRITE_SPEC=1 ./gradlew :sync:test"
                }.readText(),
            ).jsonObject

    @Test
    fun `android sealed match events`() {
        val key = SessionCrypto.deriveKey(code, sessionID)
        writeIfAsked(
            "android-sealed-events.json",
            JsonObject(
                mapOf(
                    "pairingCode" to JsonPrimitive(code),
                    "sessionID" to JsonPrimitive(sessionID.toString().uppercase()),
                    "events" to
                        JsonArray(
                            events.mapIndexed { index, event ->
                                JsonObject(
                                    mapOf(
                                        "seq" to JsonPrimitive(index + 1),
                                        "matchID" to JsonPrimitive(matchID.toString().uppercase()),
                                        "plaintext" to
                                            JsonPrimitive(
                                                OnlineSession.json.encodeToString(StampedEvent.serializer(), event),
                                            ),
                                        "ciphertext" to JsonPrimitive(OnlineSession.seal(event, key)),
                                    ),
                                )
                            },
                        ),
                ),
            ),
        )
        val rows = read("android-sealed-events.json")["events"]!!.jsonArray.map { it.jsonObject }
        rows.map { row ->
            OnlineSession
                .open(
                    RawSessionEvent(
                        row["seq"]!!.jsonPrimitive.int.toLong(),
                        UUID.randomUUID(),
                        matchID,
                        device,
                        row["ciphertext"]!!.jsonPrimitive.content,
                    ),
                    key,
                )!!
                .event
        } shouldBe events
    }

    @Test
    fun `android sealed identity events`() {
        val key = SessionCrypto.deriveKey(code, sessionID)
        writeIfAsked(
            "android-identity-events.json",
            JsonObject(
                mapOf(
                    "pairingCode" to JsonPrimitive(code),
                    "sessionID" to JsonPrimitive(sessionID.toString().uppercase()),
                    "events" to
                        JsonArray(
                            identities.mapIndexed { index, event ->
                                JsonObject(
                                    mapOf(
                                        "seq" to JsonPrimitive(index + 1),
                                        "ciphertext" to JsonPrimitive(OnlineSession.seal(event, key)),
                                    ),
                                )
                            },
                        ),
                ),
            ),
        )
        val rows = read("android-identity-events.json")["events"]!!.jsonArray.map { it.jsonObject }
        rows.map { row ->
            OnlineSession
                .openIdentity(
                    RawSessionEvent(0, UUID.randomUUID(), matchID, device, row["ciphertext"]!!.jsonPrimitive.content),
                    key,
                )!!
                .event
        } shouldBe identities
    }

    @Test
    fun `android mailbox package`() {
        writeIfAsked(
            "android-mailbox-package.json",
            JsonObject(
                mapOf(
                    "profileID" to JsonPrimitive(profileID.toString().uppercase()),
                    "mailboxKey" to JsonPrimitive(MailboxCrypto.lookupKey(profileID)),
                    "ciphertext" to JsonPrimitive(MailboxCrypto.seal(pkg, profileID)),
                ),
            ),
        )
        val document = read("android-mailbox-package.json")
        document["mailboxKey"]!!.jsonPrimitive.content shouldBe MailboxCrypto.lookupKey(profileID)
        MailboxCrypto.open(document["ciphertext"]!!.jsonPrimitive.content, profileID) shouldBe pkg
    }

    @Test
    fun `android lock screen push`() {
        val body = LiveActivityPushClient.body(LiveActivityPushClient.sessionKey(sessionID), ended = false, lockScreen)
        writeIfAsked("android-live-activity-push.json", Json.parseToJsonElement(body).jsonObject)
        read("android-live-activity-push.json") shouldBe Json.parseToJsonElement(body).jsonObject
    }
}
