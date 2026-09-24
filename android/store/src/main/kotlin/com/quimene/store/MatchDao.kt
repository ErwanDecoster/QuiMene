package com.quimene.store

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Miroir des requêtes de `MatchRepository.swift`. */
@Dao
interface MatchDao {
    @Insert
    suspend fun insert(match: MatchEntity)

    @Update
    suspend fun update(match: MatchEntity)

    @Delete
    suspend fun delete(match: MatchEntity)

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun get(id: UUID): MatchEntity?

    @Transaction
    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun getWithParticipants(id: UUID): MatchWithParticipants?

    @Query("SELECT * FROM matches ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches")
    suspend fun getAll(): List<MatchEntity>

    @Query("SELECT COUNT(*) FROM matches")
    suspend fun count(): Int
}
