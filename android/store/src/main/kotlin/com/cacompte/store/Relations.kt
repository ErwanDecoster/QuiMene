package com.cacompte.store

import androidx.room.Embedded
import androidx.room.Relation

/** Miroir de `MatchRecord.participants` (relation SwiftData résolue implicitement). */
data class MatchWithParticipants(
    @Embedded val match: MatchEntity,
    @Relation(parentColumn = "id", entityColumn = "matchId")
    val participants: List<ParticipantEntity>,
)

/** Miroir de `PlayerRecord.participations` (relation SwiftData résolue implicitement). */
data class PlayerWithParticipations(
    @Embedded val player: PlayerEntity,
    @Relation(parentColumn = "id", entityColumn = "playerId")
    val participations: List<ParticipantEntity>,
)
