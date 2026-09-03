package com.cacompte.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Représente `Date` (charte de discipline Swift : `UUID`, `Date`, `Data`, `Codable` seulement dans
 * `Domain`) — **pas en ISO-8601** : `JSONEncoder()`/`JSONDecoder()` côté Swift (`WireCodec.swift`,
 * `MatchRepository.swift`) n'y configurent aucune `dateEncodingStrategy` personnalisée, donc la
 * stratégie par défaut de Foundation s'applique (`.deferredToDate`) — un simple nombre à virgule
 * flottante, `timeIntervalSinceReferenceDate` (secondes écoulées depuis le 1er janvier 2001
 * 00:00:00 UTC, la date de référence Foundation — **pas** l'epoch Unix de 1970). Vérifié contre
 * les golden files du protocole applicatif (`occurredAt`, dossier `spec/wire`, étape F) — les
 * golden files de l'étape C (dossier `spec/golden`) n'exercent jamais ce sérialiseur (aucune date
 * dans leur format ad hoc), donc rien à ce niveau ne dépendait du format ISO-8601 initialement
 * choisi ici sans vérification.
 */
object InstantSerializer : KSerializer<Instant> {
    /** `Date(timeIntervalSinceReferenceDate: 0)` en secondes depuis l'epoch Unix. */
    private const val REFERENCE_DATE_EPOCH_SECONDS = 978_307_200L

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        val seconds = (value.epochSecond - REFERENCE_DATE_EPOCH_SECONDS) + value.nano / 1_000_000_000.0
        encoder.encodeDouble(seconds)
    }

    override fun deserialize(decoder: Decoder): Instant {
        val seconds = decoder.decodeDouble()
        val wholeSeconds = floor(seconds).toLong()
        val nanos = ((seconds - wholeSeconds) * 1_000_000_000.0).roundToLong()
        return Instant.ofEpochSecond(wholeSeconds + REFERENCE_DATE_EPOCH_SECONDS, nanos)
    }
}
