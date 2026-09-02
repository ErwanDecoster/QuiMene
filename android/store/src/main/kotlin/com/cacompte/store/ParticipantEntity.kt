package com.cacompte.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Miroir de `ParticipantRecord.swift` — un joueur **dans une partie donnée**. Le *snapshot* est
 * essentiel : si la fiche joueur est renommée ou supprimée après coup, l'historique de cette
 * partie doit rester inchangé — d'où `playerId` nullifiable et les champs `*Snapshot` figés à la
 * création.
 *
 * `@Relation`/clés étrangères explicites : Room n'a pas de résolution implicite comme SwiftData,
 * ce qui a un avantage noté par le doc 11 — l'ordre des collections y est déterministe par
 * `ORDER BY sortIndex`/`seatIndex` en requête, alors que SwiftData impose des champs `index`
 * maintenus à la main.
 */
@Entity(
    tableName = "participants",
    foreignKeys = [
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playerId"), Index("matchId")],
)
data class ParticipantEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val playerId: UUID? = null,
    val nicknameSnapshot: String = "",
    val avatarKindSnapshot: String = "symbol",
    val avatarValueSnapshot: String = "",
    val paletteIDSnapshot: String = "1",
    val seatIndex: Int = 0,
    /** Doc 05 « Belote » — `null` pour tous les jeux individuels. */
    val teamID: String? = null,
    val finalRank: Int? = null,
    val finalScore: Int? = null,
    /** Toujours requis (pas de défaut) : contrairement à Swift où `match: MatchRecord?` est
     * optionnel par contrainte SwiftData/CloudKit, un participant Room appartient toujours à une
     * partie connue au moment de l'insertion. */
    val matchId: UUID,
)
