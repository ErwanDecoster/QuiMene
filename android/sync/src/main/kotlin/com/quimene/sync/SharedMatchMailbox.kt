package com.quimene.sync

import com.quimene.domain.model.SharedMatchPackage
import com.quimene.domain.model.UUIDSerializer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * Doc 16, phase E — miroir de `SharedMatchMailbox.swift` : historique partagé. Une partie terminée,
 * complète ([SharedMatchPackage]), déposée dans la boîte aux lettres de chaque ami lié qui y a joué.
 * Remplace les résumés du doc 14.
 *
 * Chiffrement de bout en bout : la boîte d'un profil se désigne par une empreinte de son
 * identifiant partageable ([lookupKey]), son contenu est scellé avec une clé qui en est dérivée
 * ([key]). Le serveur ne voit ni l'identifiant, ni les joueurs, ni les scores. Format commun
 * vérifié par `spec/session/mailbox-package.json`, produit par le code Swift.
 *
 * **`profileID.toString().uppercase()`** partout : `UUID.uuidString` Swift est en majuscules, une
 * autre casse donnerait une autre adresse et une autre clé.
 */
object MailboxCrypto {
    /** Adresse de la boîte d'un profil côté serveur : une empreinte, jamais l'identifiant. */
    fun lookupKey(profileID: UUID): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("quimene.mailbox.lookup:${profileID.toString().uppercase()}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Clé de la boîte : HKDF-SHA256 de l'identifiant. */
    fun key(profileID: UUID): ByteArray =
        SessionCrypto.hkdfSha256(
            profileID.toString().uppercase().toByteArray(Charsets.UTF_8),
            "quimene.mailbox".toByteArray(Charsets.UTF_8),
            "quimene.mailbox.v1".toByteArray(Charsets.UTF_8),
            32,
        )

    fun seal(
        pkg: SharedMatchPackage,
        profileID: UUID,
    ): String {
        val json = OnlineSession.json.encodeToString(SharedMatchPackage.serializer(), pkg).encodeToByteArray()
        return Base64.getEncoder().encodeToString(SessionCrypto.encrypt(json, key(profileID)))
    }

    fun open(
        ciphertext: String,
        profileID: UUID,
    ): SharedMatchPackage? =
        runCatching {
            val json = SessionCrypto.decrypt(Base64.getDecoder().decode(ciphertext), key(profileID))
            OnlineSession.json.decodeFromString(SharedMatchPackage.serializer(), json.decodeToString())
        }.getOrNull()
}

/** Une partie déposée dans une boîte, encore scellée. */
@Serializable
data class MailboxItem(
    @SerialName("mailbox_key") val mailboxKey: String,
    @SerialName("match_id") @Serializable(with = UUIDSerializer::class) val matchID: UUID,
    val ciphertext: String,
)

/** Fonctions SQL de la migration `create_quimene_match_mailbox`. Dépôt idempotent par
 * `(mailbox_key, match_id)` : un envoi retenté ne duplique jamais une partie. */
class MatchMailboxTransport {
    private val client get() = SessionSupabase.client

    suspend fun deposit(items: List<MailboxItem>) {
        if (items.isEmpty()) return
        client.postgrest.rpc(
            "quimene_mailbox_deposit",
            buildJsonObject {
                put(
                    "p_items",
                    buildJsonArray {
                        for (item in items) {
                            add(
                                buildJsonObject {
                                    put("mailbox_key", item.mailboxKey)
                                    put("match_id", item.matchID.toString())
                                    put("ciphertext", item.ciphertext)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    suspend fun fetch(mailboxKey: String): List<MailboxItem> =
        client.postgrest
            .rpc("quimene_mailbox_fetch", buildJsonObject { put("p_mailbox_key", mailboxKey) })
            .decodeList<MailboxItem>()

    suspend fun remove(
        mailboxKey: String,
        matchID: UUID,
    ) {
        client.postgrest.rpc(
            "quimene_mailbox_remove",
            buildJsonObject {
                put("p_mailbox_key", mailboxKey)
                put("p_match_id", matchID.toString())
            },
        )
    }
}
