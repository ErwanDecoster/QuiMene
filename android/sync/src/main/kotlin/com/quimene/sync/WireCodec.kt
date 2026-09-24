package com.quimene.sync

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * `WireMessage` encodé en JSON puis chiffré (doc 09) — la seule forme qui transite sur un
 * [TransportSession], quel que soit le transport actif. Miroir de `WireCodec.swift`
 * (`JSONEncoder()`/`JSONDecoder()` sans configuration particulière).
 *
 * `encodeDefaults = true` + `explicitNulls = false` : reproduit exactement le `Codable`
 * synthétisé par Swift, qui encode toujours un champ non optionnel (même une collection vide,
 * comme `ScoreInput.modifiers`) mais omet entièrement la clé d'un `Optional` à `nil` (comme
 * `RoundDraft.note`) plutôt que d'écrire `null` — un réglage kotlinx.serialization ne peut pas
 * dépendre de si un champ "a une valeur par défaut" pour décider, contrairement à la combinaison
 * de ces deux réglages précis.
 */
object WireCodec {
    val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    fun encode(
        message: WireMessage,
        key: ByteArray,
    ): ByteArray {
        val plaintext = json.encodeToString(message).encodeToByteArray()
        return SessionCrypto.encrypt(plaintext, key)
    }

    fun decode(
        data: ByteArray,
        key: ByteArray,
    ): WireMessage {
        val plaintext = SessionCrypto.decrypt(data, key)
        return json.decodeFromString(plaintext.decodeToString())
    }
}
