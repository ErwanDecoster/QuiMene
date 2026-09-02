package com.cacompte.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * Représente `Date` (charte de discipline Swift : `UUID`, `Date`, `Data`, `Codable` seulement
 * dans `Domain`) en ISO-8601 (`Instant.toString()`/`Instant.parse()`). **À vérifier à l'étape C**
 * contre le format réel de `spec/golden (fichiers .json)` (`committedAt`) — cette étape ne décode aucun
 * golden file, seulement `spec/games (fichiers .json)` qui ne contient aucune date.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
