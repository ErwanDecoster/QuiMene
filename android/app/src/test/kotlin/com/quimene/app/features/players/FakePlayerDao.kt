package com.quimene.app.features.players

import com.quimene.store.PlayerDao
import com.quimene.store.PlayerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** [PlayerDao] minimal pour les tests qui exercent uniquement l'état d'un ViewModel sans jamais
 * appeler le repository — pas de base Room à instancier pour ces cas-là. */
internal class FakePlayerDao : PlayerDao {
    override suspend fun insert(player: PlayerEntity) = error("not used by this test")

    override suspend fun update(player: PlayerEntity) = error("not used by this test")

    override suspend fun updateAll(players: List<PlayerEntity>) = error("not used by this test")

    override suspend fun delete(player: PlayerEntity) = error("not used by this test")

    override fun observeAll(): Flow<List<PlayerEntity>> = flowOf(emptyList())

    override suspend fun get(id: UUID): PlayerEntity? = null

    override suspend fun getAll(): List<PlayerEntity> = emptyList()

    override suspend fun activePlayers(): List<PlayerEntity> = emptyList()

    override suspend fun myOwnSharedPlayer(): PlayerEntity? = null

    override suspend fun withSharedProfileID(): List<PlayerEntity> = emptyList()

    override suspend fun bySharedProfileID(id: UUID): PlayerEntity? = null
}
