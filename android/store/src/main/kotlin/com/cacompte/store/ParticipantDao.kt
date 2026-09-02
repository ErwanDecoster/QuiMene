package com.cacompte.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.util.UUID

/** Miroir des requêtes de `ParticipantRecord.swift`. */
@Dao
interface ParticipantDao {
    @Insert
    suspend fun insertAll(participants: List<ParticipantEntity>)

    @Update
    suspend fun update(participant: ParticipantEntity)

    @Update
    suspend fun updateAll(participants: List<ParticipantEntity>)

    @Query("SELECT * FROM participants WHERE matchId = :matchId ORDER BY seatIndex")
    suspend fun forMatch(matchId: UUID): List<ParticipantEntity>

    @Query("SELECT * FROM participants")
    suspend fun getAll(): List<ParticipantEntity>
}
