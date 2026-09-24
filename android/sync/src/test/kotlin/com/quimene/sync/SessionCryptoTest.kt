package com.quimene.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

/** Miroir de `SessionCryptoTests.swift`. */
class SessionCryptoTest {
    @Test
    fun `a message encrypted then decrypted with the same key round-trips`() {
        val sessionID = UUID.randomUUID()
        val key = SessionCrypto.deriveKey("042817", sessionID)
        val plaintext = "bonjour".toByteArray(Charsets.UTF_8)

        val ciphertext = SessionCrypto.encrypt(plaintext, key)
        val decrypted = SessionCrypto.decrypt(ciphertext, key)

        // `ByteArray` : comparaison de contenu explicite, `==`/`shouldBe` sur un tableau compare
        // par référence en Kotlin (piège déjà rencontré côté `ScoreDetail`, étape B).
        decrypted.contentEquals(plaintext) shouldBe true
        ciphertext.contentEquals(plaintext) shouldBe false // le texte chiffré ne doit jamais coïncider avec le clair.
    }

    @Test
    fun `two derivations with the same code and sessionID give the same key`() {
        val sessionID = UUID.randomUUID()
        val keyA = SessionCrypto.deriveKey("042817", sessionID)
        val keyB = SessionCrypto.deriveKey("042817", sessionID)

        keyA.contentEquals(keyB) shouldBe true
    }

    @Test
    fun `a different sessionID derives a different key, even with the same code`() {
        val keyA = SessionCrypto.deriveKey("042817", UUID.randomUUID())
        val keyB = SessionCrypto.deriveKey("042817", UUID.randomUUID())

        keyA.contentEquals(keyB) shouldBe false
    }

    @Test
    fun `decrypting with the wrong key fails rather than returning corrupted data`() {
        val sessionID = UUID.randomUUID()
        val rightKey = SessionCrypto.deriveKey("042817", sessionID)
        val wrongKey = SessionCrypto.deriveKey("999999", sessionID)
        val ciphertext = SessionCrypto.encrypt("secret".toByteArray(Charsets.UTF_8), rightKey)

        shouldThrow<Exception> { SessionCrypto.decrypt(ciphertext, wrongKey) }
    }
}
