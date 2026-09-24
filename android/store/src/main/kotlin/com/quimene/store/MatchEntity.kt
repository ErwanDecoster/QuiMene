package com.quimene.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quimene.domain.model.MatchStatus
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `MatchRecord.swift` — une partie, du premier tap à l'archivage. `eventLogData` est
 * **la source de vérité** (ADR-0005, event sourcing) ; la reprise après relance rejoue ce
 * journal, elle ne lit jamais un total mis en cache.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val gameID: String = "",
    val rulesVersion: Int = 1,
    val variantsData: ByteArray = ByteArray(0),
    val startedAt: Instant = Instant.now(),
    val endedAt: Instant? = null,
    val status: MatchStatus = MatchStatus.InProgress,
    val endReasonRaw: String? = null,
    /** Masque la partie de l'onglet Historique sans y toucher — les statistiques de profil
     * continuent de l'inclure, au même titre que l'archivage d'un [PlayerEntity]. */
    val isArchived: Boolean = false,
    /** Identifiant d'appareil créateur — utile en sync (étape F), placeholder en attendant. */
    val deviceOrigin: String = "local",
    val eventLogData: ByteArray = ByteArray(0),
    /** Doc 14, phase 2 — `true` dès la conclusion si au moins un participant est lié à
     * l'installation d'un ami, jusqu'à ce que le résumé lui soit poussé avec succès. */
    val pendingSharedProfileSync: Boolean = false,
    /** Cette partie n'a pas été jouée sur cet appareil : c'est un résumé reçu de l'installation
     * d'un ami. Pas de journal d'événements exploitable — seuls `finalRank`/`finalScore` des
     * participants portent le résultat. */
    val isImportedSummary: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is MatchEntity &&
            id == other.id &&
            gameID == other.gameID &&
            rulesVersion == other.rulesVersion &&
            variantsData.contentEquals(other.variantsData) &&
            startedAt == other.startedAt &&
            endedAt == other.endedAt &&
            status == other.status &&
            endReasonRaw == other.endReasonRaw &&
            isArchived == other.isArchived &&
            deviceOrigin == other.deviceOrigin &&
            eventLogData.contentEquals(other.eventLogData) &&
            pendingSharedProfileSync == other.pendingSharedProfileSync &&
            isImportedSummary == other.isImportedSummary

    override fun hashCode(): Int = id.hashCode()
}
