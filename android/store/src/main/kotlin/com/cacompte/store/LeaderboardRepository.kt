package com.cacompte.store

import com.cacompte.domain.model.MatchStatus
import java.util.UUID

/**
 * Miroir de `LeaderboardRepository.swift` — classement des joueurs sur un jeu donné, à travers
 * toutes leurs parties terminées. Même politique que [ProfileRepository] : calcul à la demande,
 * rien de pré-agrégé (« un agrégat stocké est un agrégat qui finira désynchronisé »).
 *
 * Filtrage/jointure en mémoire plutôt qu'une requête SQL composée : même choix que la source
 * Swift, qui évite pour la même raison un `#Predicate` chaînant la relation optionnelle
 * `player` — le volume attendu (doc 06) reste compatible avec un chargement complet.
 */
class LeaderboardRepository(
    private val matchDao: MatchDao,
    private val participantDao: ParticipantDao,
    private val playerDao: PlayerDao,
) {
    suspend fun leaderboard(gameID: String): List<LeaderboardEntry> {
        val matchesByID = matchDao.getAll().associateBy { it.id }
        val playersByID = playerDao.getAll().associateBy { it.id }
        val allParticipants = participantDao.getAll()
        // Compté une fois sur la liste déjà chargée plutôt qu'une requête par participation
        // (évite un aller-retour base par manche dans la boucle d'agrégation ci-dessous).
        val participantCountByMatch = allParticipants.groupingBy { it.matchId }.eachCount()

        val participations =
            allParticipants.filter { participant ->
                val match = matchesByID[participant.matchId] ?: return@filter false
                participant.playerId != null && match.gameID == gameID && match.status == MatchStatus.Ended
            }

        data class Aggregate(
            val player: PlayerEntity,
            var played: Int = 0,
            var wins: Int = 0,
            var normalizedRankSum: Double = 0.0,
            var normalizedRankCount: Int = 0,
        )

        val byPlayer = mutableMapOf<UUID, Aggregate>()
        for (participation in participations) {
            val player = playersByID[participation.playerId] ?: continue
            val aggregate = byPlayer.getOrPut(player.id) { Aggregate(player) }
            aggregate.played += 1
            if (participation.finalRank == 1) aggregate.wins += 1
            val rank = participation.finalRank
            if (rank != null) {
                val playerCount = participantCountByMatch[participation.matchId] ?: 0
                if (playerCount > 1) {
                    aggregate.normalizedRankSum += (playerCount - rank).toDouble() / (playerCount - 1)
                    aggregate.normalizedRankCount += 1
                }
            }
        }

        return byPlayer.values
            .map { aggregate ->
                LeaderboardEntry(
                    playerID = aggregate.player.id,
                    name = aggregate.player.nickname,
                    avatarKind = aggregate.player.avatarKind,
                    avatarValue = aggregate.player.avatarValue,
                    avatarPhoto = aggregate.player.avatarPhoto,
                    paletteID = aggregate.player.paletteID,
                    played = aggregate.played,
                    wins = aggregate.wins,
                    winRate = if (aggregate.played > 0) aggregate.wins.toDouble() / aggregate.played else 0.0,
                    averageNormalizedRank =
                        if (aggregate.normalizedRankCount > 0) {
                            aggregate.normalizedRankSum / aggregate.normalizedRankCount
                        } else {
                            0.0
                        },
                )
            }
            // Pas d'ordre implicite : à égalité parfaite, le tri retombe sur le nombre de
            // parties puis le nom, pour ne jamais dépendre d'un ordre de map non garanti.
            .sortedWith(
                compareByDescending<LeaderboardEntry> { it.winRate }
                    .thenByDescending { it.averageNormalizedRank }
                    .thenByDescending { it.played }
                    .thenBy { it.name },
            )
    }
}
