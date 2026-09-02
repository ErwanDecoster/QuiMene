package com.cacompte.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

/**
 * Miroir de `ScoreDetail.swift` — payload opaque, non consommé par aucun jeu à cette étape
 * (réservé à un usage futur : grille Yams, contrat Tarot). `Data` encode par défaut en base64
 * via `JSONEncoder` côté Swift (`dataEncodingStrategy` par défaut) — [Base64DataSerializer]
 * reproduit la même forme JSON.
 */
@Serializable
class ScoreDetail(
    @Serializable(with = Base64DataSerializer::class) val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ScoreDetail && payload.contentEquals(other.payload)

    override fun hashCode(): Int = payload.contentHashCode()
}

object Base64DataSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Data", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}
