package com.cacompte.store

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** Miroir de `PlayerRepositoryError.swift` — une seule fiche par appareil peut être « la
 * sienne » ; tenter d'en partager une seconde est un vrai refus, pas un cas ignoré. */
sealed class PlayerRepositoryError(
    message: String,
) : Exception(message) {
    data class AlreadySharingAnotherProfile(
        val nickname: String,
    ) : PlayerRepositoryError("Une autre fiche ($nickname) est déjà partagée depuis cet appareil.")

    data object CannotShareALinkedProfile :
        PlayerRepositoryError("Cette fiche suit déjà un ami — elle ne peut pas être partagée comme la vôtre.")
}

/**
 * Miroir de `PlayerRepository.swift`. Écritures `suspend` (Room impose les I/O hors thread
 * principal, contrairement à SwiftData où `mainContext` reste synchrone) plutôt que le
 * `@MainActor` synchrone de la source.
 */
class PlayerRepository(
    private val dao: PlayerDao,
) {
    fun observeAll(): Flow<List<PlayerEntity>> = dao.observeAll()

    suspend fun create(
        nickname: String,
        avatarKind: String,
        avatarValue: String,
        avatarPhoto: ByteArray? = null,
        paletteID: String? = null,
    ): PlayerEntity {
        val player =
            PlayerEntity(
                nickname = nickname,
                avatarKind = avatarKind,
                avatarValue = avatarValue,
                avatarPhoto = avatarPhoto,
                paletteID = paletteID ?: nextAvailablePaletteID(),
                sortIndex = nextSortIndex(),
            )
        dao.insert(player)
        return player
    }

    suspend fun save(player: PlayerEntity) {
        dao.update(player)
    }

    suspend fun archive(player: PlayerEntity) {
        dao.update(player.copy(isArchived = true))
    }

    suspend fun unarchive(player: PlayerEntity) {
        dao.update(player.copy(isArchived = false))
    }

    /** Suppression définitive — irréversible, contrairement à [archive]. La règle `SET_NULL` sur
     * `ParticipantEntity.playerId` garde l'historique lisible : seul le lien vers la fiche
     * disparaît, les snapshots restent inchangés. */
    suspend fun delete(player: PlayerEntity) {
        dao.delete(player)
    }

    /** Doc 14, phase 4 — génère l'identifiant partageable de cette fiche s'il n'existe pas
     * encore, et la désigne comme *la* fiche de cet appareil. Jamais régénéré une fois posé. */
    suspend fun sharedProfileID(player: PlayerEntity): UUID {
        val existing = player.sharedProfileID
        if (existing != null) {
            if (!player.sharedProfileIsMine) throw PlayerRepositoryError.CannotShareALinkedProfile
            return existing
        }
        val existingMine = myOwnSharedPlayer()
        if (existingMine != null && existingMine.id != player.id) {
            throw PlayerRepositoryError.AlreadySharingAnotherProfile(existingMine.nickname)
        }
        val id = UUID.randomUUID()
        dao.update(player.copy(sharedProfileID = id, sharedProfileIsMine = true))
        return id
    }

    suspend fun myOwnSharedPlayer(): PlayerEntity? = dao.myOwnSharedPlayer()

    /** Lie cette fiche à l'identifiant scanné depuis l'appareil d'un ami (doc 14) —
     * `sharedProfileIsMine` reste `false` : lier, contrairement à partager, ne désigne jamais
     * cette fiche comme celle de l'utilisateur de cet appareil. */
    suspend fun linkSharedProfile(
        id: UUID,
        name: String,
        player: PlayerEntity,
    ) {
        dao.update(
            player.copy(
                sharedProfileID = id,
                sharedProfileIsMine = false,
                sharedProfileLinkedName = name,
                sharedProfileLinkedAt = Instant.now(),
            ),
        )
    }

    suspend fun unlinkSharedProfile(player: PlayerEntity) {
        dao.update(
            player.copy(
                sharedProfileID = null,
                sharedProfileIsMine = false,
                sharedProfileLinkedName = null,
                sharedProfileLinkedAt = null,
            ),
        )
    }

    /** Doc 14, phase 2 — toutes les fiches liées sur cet appareil. */
    suspend fun allSharedProfileIDs(): List<UUID> = dao.withSharedProfileID().mapNotNull { it.sharedProfileID }

    suspend fun player(sharedProfileID: UUID): PlayerEntity? = dao.bySharedProfileID(sharedProfileID)

    /** Ordre manuel de la liste des joueurs (`sortIndex`) — Room ne préserve pas non plus
     * l'ordre d'une collection sans tri explicite. */
    suspend fun reorder(players: List<PlayerEntity>) {
        dao.updateAll(players.mapIndexed { index, player -> player.copy(sortIndex = index) })
    }

    /** Charte §1.5 : première couleur libre parmi les joueurs actifs à la création. Si les dix
     * sont prises, reboucle plutôt que d'échouer. */
    suspend fun nextAvailablePaletteID(): String {
        val active = dao.activePlayers()
        val used = active.mapNotNull { it.paletteID.toIntOrNull() }.toSet()
        for (index in 1..10) {
            if (index !in used) return index.toString()
        }
        return ((active.size % 10) + 1).toString()
    }

    private suspend fun nextSortIndex(): Int = (dao.getAll().maxOfOrNull { it.sortIndex } ?: -1) + 1
}
