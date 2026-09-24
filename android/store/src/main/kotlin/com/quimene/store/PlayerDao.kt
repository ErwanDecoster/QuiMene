package com.quimene.store

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Miroir des requêtes de `PlayerRepository.swift` — `Flow<...>` pour les lectures observées par
 * l'UI, équivalent du rafraîchissement automatique `@Query` de SwiftData (doc 11, étape D). */
@Dao
interface PlayerDao {
    @Insert
    suspend fun insert(player: PlayerEntity)

    @Update
    suspend fun update(player: PlayerEntity)

    @Update
    suspend fun updateAll(players: List<PlayerEntity>)

    @Delete
    suspend fun delete(player: PlayerEntity)

    @Query("SELECT * FROM players ORDER BY sortIndex")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id = :id LIMIT 1")
    suspend fun get(id: UUID): PlayerEntity?

    @Query("SELECT * FROM players")
    suspend fun getAll(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE isArchived = 0")
    suspend fun activePlayers(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE sharedProfileIsMine = 1 LIMIT 1")
    suspend fun myOwnSharedPlayer(): PlayerEntity?

    @Query("SELECT * FROM players WHERE sharedProfileID IS NOT NULL")
    suspend fun withSharedProfileID(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE sharedProfileID = :id LIMIT 1")
    suspend fun bySharedProfileID(id: UUID): PlayerEntity?
}
