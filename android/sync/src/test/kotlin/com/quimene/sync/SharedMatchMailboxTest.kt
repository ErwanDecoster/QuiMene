package com.quimene.sync

import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.Participant
import com.quimene.domain.model.SharedMatchPackage
import com.quimene.domain.model.VariantSelection
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Miroir de `SharedMatchMailboxTests.swift` (doc 16, phase E). */
class SharedMatchMailboxTest {
    private val profileID = UUID.fromString("33333333-3333-4333-8333-333333333333")

    private val pkg: SharedMatchPackage
        get() {
            val theo = Participant(UUID.fromString("11111111-1111-4111-8111-111111111111"), "Théo", 0)
            val matchID = UUID.fromString("CCCCCCCC-0000-4000-8000-000000000001")
            return SharedMatchPackage(
                matchID = matchID,
                participants = listOf(SharedMatchPackage.Participant(theo.id, profileID, "Théo", "emoji", "🦊", "4")),
                events =
                    listOf(
                        StampedEvent(
                            id = matchID,
                            lamport = 1uL,
                            deviceID = "ios-device",
                            occurredAt = Instant.ofEpochSecond(978_307_200L + 800_000_000L),
                            event = MatchEvent.MatchCreated("skyjo", 1, VariantSelection(), listOf(theo)),
                        ),
                    ),
            )
        }

    @Test
    fun `sealed for one profile, readable by it alone`() {
        val sealed = MailboxCrypto.seal(pkg, profileID)
        MailboxCrypto.open(sealed, profileID) shouldBe pkg
        MailboxCrypto.open(sealed, UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `the mailbox address does not reveal the profile`() {
        val key = MailboxCrypto.lookupKey(profileID)
        key.length shouldBe 64
        key.all { it in "0123456789abcdef" } shouldBe true
        key.contains("33333333") shouldBe false
    }

    /** `spec/session/mailbox-package.json` est produit par le code Swift réel : Android doit
     * retrouver la même adresse et le même paquet. */
    @Test
    fun `mailbox sealed by iOS opens to the exact same package, at the same address`() {
        val fixture =
            Json
                .parseToJsonElement(
                    requireNotNull(
                        javaClass.classLoader.getResource("SessionResources/mailbox-package.json"),
                    ).readText(),
                ).jsonObject
        val id = UUID.fromString(fixture["profileID"]!!.jsonPrimitive.content)
        MailboxCrypto.lookupKey(id) shouldBe fixture["mailboxKey"]!!.jsonPrimitive.content
        val opened = requireNotNull(MailboxCrypto.open(fixture["ciphertext"]!!.jsonPrimitive.content, id))
        opened shouldBe
            OnlineSession.json.decodeFromString(
                SharedMatchPackage.serializer(),
                fixture["plaintext"]!!.jsonPrimitive.content,
            )
        opened shouldBe pkg
    }
}
