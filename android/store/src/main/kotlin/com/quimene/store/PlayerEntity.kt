package com.quimene.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `PlayerRecord.swift` — la fiche joueur, réutilisée d'une partie à l'autre. Toute
 * propriété a une valeur par défaut côté Swift pour satisfaire CloudKit ; Room n'a pas cette
 * contrainte, mais les défauts sont conservés ici pour rester un miroir fidèle des données
 * elles-mêmes (pas de la contrainte de plateforme qui les a motivés).
 */
@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val nickname: String = "",
    /** `"symbol"` | `"emoji"` | `"photo"` */
    val avatarKind: String = "symbol",
    /** Nom Material Symbol, ou emoji, ou `""` si `avatarKind == "photo"`. */
    val avatarValue: String = "",
    val avatarPhoto: ByteArray? = null,
    /** Identifiant de palette joueur, `"1"`…`"10"` (charte §1.5). */
    val paletteID: String = "1",
    val createdAt: Instant = Instant.now(),
    val isArchived: Boolean = false,
    val sortIndex: Int = 0,
    /** Doc 14 « Profils partagés » — identifiant permanent, jamais régénéré une fois posé. */
    val sharedProfileID: UUID? = null,
    /** `true` uniquement pour la fiche que cet appareil partage comme la sienne (une seule par
     * appareil, voir [com.quimene.store.PlayerRepository.sharedProfileID]). */
    val sharedProfileIsMine: Boolean = false,
    /** Pseudo tel que scanné au moment de la liaison — jamais mis à jour ensuite. */
    val sharedProfileLinkedName: String? = null,
    val sharedProfileLinkedAt: Instant? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is PlayerEntity &&
            id == other.id &&
            nickname == other.nickname &&
            avatarKind == other.avatarKind &&
            avatarValue == other.avatarValue &&
            (avatarPhoto?.contentEquals(other.avatarPhoto) ?: (other.avatarPhoto == null)) &&
            paletteID == other.paletteID &&
            createdAt == other.createdAt &&
            isArchived == other.isArchived &&
            sortIndex == other.sortIndex &&
            sharedProfileID == other.sharedProfileID &&
            sharedProfileIsMine == other.sharedProfileIsMine &&
            sharedProfileLinkedName == other.sharedProfileLinkedName &&
            sharedProfileLinkedAt == other.sharedProfileLinkedAt

    override fun hashCode(): Int = id.hashCode()
}
