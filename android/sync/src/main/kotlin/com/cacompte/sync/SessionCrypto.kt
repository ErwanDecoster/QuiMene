package com.cacompte.sync

import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Doc 09 « Appairage et chiffrement » — remplace le chiffrement gratuit de MultipeerConnectivity,
 * perdu en passant à des sockets/GATT bruts (ADR-0014). Miroir de `SessionCrypto.swift`
 * (`CryptoKit`, framework système) : `javax.crypto` (JCE, dans le JDK/Android, pas une dépendance
 * ajoutée — ADR-0012) couvre l'AES-GCM directement, mais **pas de primitive HKDF prête à
 * l'emploi** (contrairement à `CryptoKit.HKDF<SHA256>`) — réimplémenté à la main sur
 * `Mac("HmacSHA256")` (RFC 5869) plutôt que d'importer une bibliothèque de crypto entière
 * (Tink/Bouncy Castle) pour un seul primitif.
 */
object SessionCrypto {
    class SealFailedException : Exception("Le chiffrement du message a échoué.")

    private const val AES_KEY_BYTES = 32 // 256 bits.
    private const val GCM_NONCE_BYTES = 12 // 96 bits — taille de nonce standard/seule supportée par CryptoKit.
    private const val GCM_TAG_BITS = 128

    /** Affiché en clair par l'hôte (et encodé dans un QR) ; ne transite jamais sur le réseau —
     * seule la clé qui en dérive y transite, jamais le code lui-même. */
    fun generatePairingCode(): String = "%06d".format(Random.nextInt(0, 1_000_000))

    /**
     * HKDF-SHA256 : le `sessionID` sert de sel, ce qui garantit une clé différente par session
     * même si deux hôtes choisissent le même code par coïncidence. Salé par la session — stable
     * tant qu'elle dure — et non par la partie courante : une session peut enchaîner plusieurs
     * parties (doc 09 « Fin de partie ») sans que la clé ne change, donc sans qu'un pair déjà
     * connecté ait besoin de se réappairer entre deux parties.
     *
     * **`sessionID.toString().uppercase()`, pas `.toString()`** : `UUID.uuidString` côté Swift
     * (utilisé comme sel) est toujours en MAJUSCULES (convention Foundation) — `UUID.toString()`
     * Kotlin est en minuscules. Une casse différente changerait le sel, donc la clé dérivée : les
     * deux plateformes ne pourraient jamais se déchiffrer l'une l'autre malgré un code identique.
     */
    fun deriveKey(
        pairingCode: String,
        sessionID: UUID,
    ): ByteArray {
        val inputKeyMaterial = pairingCode.toByteArray(Charsets.UTF_8)
        val salt = sessionID.toString().uppercase().toByteArray(Charsets.UTF_8)
        val info = "cacompte.livesession.v1".toByteArray(Charsets.UTF_8)
        return hkdfSha256(inputKeyMaterial, salt, info, AES_KEY_BYTES)
    }

    fun encrypt(
        data: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val nonce = ByteArray(GCM_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertextAndTag =
            try {
                cipher.doFinal(data)
            } catch (cause: Exception) {
                throw SealFailedException()
            }
        // Même disposition que `AES.GCM.SealedBox.combined` côté CryptoKit : nonce || ciphertext || tag.
        return nonce + ciphertextAndTag
    }

    fun decrypt(
        data: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(data.size > GCM_NONCE_BYTES) { "Message chiffré trop court." }
        val nonce = data.copyOfRange(0, GCM_NONCE_BYTES)
        val ciphertextAndTag = data.copyOfRange(GCM_NONCE_BYTES, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertextAndTag)
    }

    /** RFC 5869 — extract puis expand, sur `HmacSHA256` (longueur de hash 32 octets). */
    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputByteCount: Int,
    ): ByteArray {
        val hashLen = 32
        val pseudoRandomKey = hmacSha256(salt, inputKeyMaterial)

        var previousBlock = ByteArray(0)
        val output = ByteArray(outputByteCount)
        var written = 0
        var counter = 1
        while (written < outputByteCount) {
            val block = hmacSha256(pseudoRandomKey, previousBlock + info + byteArrayOf(counter.toByte()))
            val toCopy = minOf(hashLen, outputByteCount - written)
            block.copyInto(output, destinationOffset = written, startIndex = 0, endIndex = toCopy)
            written += toCopy
            previousBlock = block
            counter += 1
        }
        return output
    }

    private fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Une clé HMAC vide (sel absent) est valide en RFC 5869 mais rejetée par certaines
        // implémentations JCE si on lui passe un tableau de longueur 0 — non pertinent ici (le
        // sel est toujours non vide, `sessionID.toString()`), mais gardé explicite pour la
        // prochaine lecture plutôt que de dépendre d'un comportement JCE non documenté.
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256"))
        return mac.doFinal(message)
    }
}
