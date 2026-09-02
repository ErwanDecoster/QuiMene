package com.cacompte.domain.engine

import com.cacompte.domain.model.InstantSerializer
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.UUIDSerializer
import com.cacompte.domain.model.VariantSelection
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `MatchEvent.swift` — **7 cas**, pas 6 comme l'énumère par erreur le texte de
 * docs/11-portage-android.md (étape B) : le code Swift source fait foi. `Codable` côté Swift ;
 * marqué `@Serializable` ici pour la même fidélité structurelle, mais la forme JSON exacte de
 * kotlinx.serialization (polymorphisme à discriminant) n'a pas été vérifiée bit-à-bit contre
 * l'encodage Swift réel de `MatchRecord.eventLogData` — aucun golden file ni fichier `spec/` ne
 * sérialise `MatchEvent` (les golden files ont leur propre format ad hoc, décodé par le code de
 * test, pas par `MatchEvent.Codable`). À vérifier explicitement à l'étape où l'export/import
 * `.cacompte` ou la persistance de journal sont réellement implémentés (D/F), pas avant.
 */
@Serializable
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
