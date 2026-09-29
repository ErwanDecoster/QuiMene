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
    /** `"local"` pour une partie jouée sur cet appareil, `"received"` pour une partie jouée sur
     * un autre appareil puis enregistrée ici (doc 16, phase E). */
    val deviceOrigin: String = "local",
    val eventLogData: ByteArray = ByteArray(0),
    /** Doc 14 « Historique partagé » — `true` dès la conclusion si au moins un participant est lié
     * à un ami, jusqu'à ce que la partie ait été déposée dans sa boîte aux lettres. */
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

    /** Doc 16, phase E — jouée sur un autre appareil, puis enregistrée ici (copie de participant,
     * boîte aux lettres, ou ancien résumé du doc 14) : l'Historique le signale. */
    val isReceived: Boolean get() = deviceOrigin == RECEIVED_ORIGIN || isImportedSummary

    companion object {
        /** Valeur de [deviceOrigin] d'une partie qui ne vient pas de cet appareil. */
        const val RECEIVED_ORIGIN = "received"
    }
}
