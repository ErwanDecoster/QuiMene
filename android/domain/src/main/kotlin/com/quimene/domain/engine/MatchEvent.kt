package com.quimene.domain.engine

import com.quimene.domain.model.InstantSerializer
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.UUIDSerializer
import com.quimene.domain.model.VariantSelection
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `MatchEvent.swift` — **7 cas**, pas 6 comme l'énumère par erreur le texte de
 * docs/11-portage-android.md (étape B) : le code Swift source fait foi. `Codable` côté Swift ;
 * sérialisé ici via [MatchEventSerializer], qui reproduit exactement la forme du `Codable`
 * synthétisé par Swift (`{"<cas>": {…}}`, `_0` pour un paramètre non nommé) — vérifié bit-à-bit
 * contre les golden files du protocole applicatif (dossier `spec/wire`, doc 09, étape F), pas le
 * polymorphisme à discriminant par défaut de kotlinx.serialization (`{"type": "roundCommitted",
 * ...}`) qu'aurait produit un simple `@Serializable` sur cette interface.
 */
@Serializable(with = MatchEventSerializer::class)
sealed interface MatchEvent {
    @Serializable
    data class MatchCreated(
        val gameID: String,
        val rulesVersion: Int,
        val variants: VariantSelection,
        val participants: List<Participant>,
    ) : MatchEvent

    @Serializable
    data class RoundCommitted(
        val draft: RoundDraft,
    ) : MatchEvent

    @Serializable
    data class RoundAmended(
        val index: Int,
        val draft: RoundDraft,
    ) : MatchEvent

    @Serializable
    data class RoundRemoved(
        val index: Int,
    ) : MatchEvent

    @Serializable
    data class MatchAbandoned(
        @Serializable(with = InstantSerializer::class) val at: Instant,
    ) : MatchEvent

    @Serializable
    data object MatchEndedManually : MatchEvent

    @Serializable
    data class NoteAdded(
        val roundIndex: Int,
        val text: String,
    ) : MatchEvent
}

/** Miroir de `StampedEvent.swift`. `lamport: UInt64` côté Swift → `ULong` Kotlin (support natif
 * kotlinx.serialization pour les types non signés). `occurredAt` est **documentaire, jamais
 * utilisé pour l'ordre** — seul `(lamport, deviceID)` ordonne le rejeu (`MatchEngine.replay`). */
@Serializable
data class StampedEvent(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val lamport: ULong,
    val deviceID: String,
    @Serializable(with = InstantSerializer::class)
    val occurredAt: Instant,
    val event: MatchEvent,
)
