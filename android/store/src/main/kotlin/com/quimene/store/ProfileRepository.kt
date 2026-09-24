package com.quimene.store

import com.quimene.domain.model.MatchStatus
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.GameCatalog
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Miroir de `ProfileRepository.swift` — parcourt tout l'historique d'un joueur. Calcul à la
 * demande, aucune pré-agrégation persistée (doc 06).
 */
class ProfileRepository(
    private val matchDao: MatchDao,
    private val participantDao: ParticipantDao,
) {
    suspend fun stats(
        player: PlayerEntity,
        catalog: GameCatalog,
    ): ProfileStats {
        val matchesByID = matchDao.getAll().associateBy { it.id }
        val allParticipants = participantDao.getAll()
        val participantsByMatch = allParticipants.groupBy { it.matchId }

        val playerID = player.id
        val participations =
            allParticipants.filter { participant ->
                participant.playerId == playerID && matchesByID[participant.matchId]?.status == MatchStatus.Ended
            }

        if (participations.isEmpty()) return ProfileStats.empty

        val played = participations.size
        val wins = participations.count { it.finalRank == 1 }
        val winRate = wins.toDouble() / played

        val ranks = participations.mapNotNull { it.finalRank }
        val averageRank = if (ranks.isEmpty()) 0.0 else ranks.sum().toDouble() / ranks.size

        val normalizedRanks =
            participations.mapNotNull { participant ->
                val rank = participant.finalRank ?: return@mapNotNull null
                val playerCount = participantsByMatch[participant.matchId]?.size ?: 0
                if (playerCount <= 1) return@mapNotNull null
                (playerCount - rank).toDouble() / (playerCount - 1)
            }
        val averageNormalizedRank = if (normalizedRanks.isEmpty()) 0.0 else normalizedRanks.sum() / normalizedRanks.size

        val (currentStreak, bestStreak) = winStreaks(participations, matchesByID)

        return ProfileStats(
            played = played,
            wins = wins,
            winRate = winRate,
            averageRank = averageRank,
            averageNormalizedRank = averageNormalizedRank,
            byGame = gameBreakdowns(participations, matchesByID, catalog),
            nemesis = computeNemesis(playerID, participations, matchesByID, participantsByMatch),
            currentWinStreak = currentStreak,
            bestWinStreak = bestStreak,
            activity = monthlyActivity(participations, matchesByID),
        )
    }

    private fun gameBreakdowns(
        participations: List<ParticipantEntity>,
        matchesByID: Map<UUID, MatchEntity>,
        catalog: GameCatalog,
    ): List<ProfileStats.GameBreakdown> {
        val byGameID = participations.groupBy { matchesByID[it.matchId]?.gameID ?: "" }
        return byGameID
            .mapNotNull { (gameID, entries) ->
                if (gameID.isEmpty()) return@mapNotNull null
                val rulesVersion = matchesByID[entries.first().matchId]?.rulesVersion ?: 1
                val definition = runCatching { catalog.definition(gameID, rulesVersion) }.getOrNull()
                val name = definition?.name?.localized ?: gameID
                val direction = definition?.scoring?.direction ?: Direction.LowestWins

                val played = entries.size
                val wins = entries.count { it.finalRank == 1 }
                val winRate = if (played > 0) wins.toDouble() / played else 0.0

                val scored =
                    entries.mapNotNull { entry ->
                        val score = entry.finalScore ?: return@mapNotNull null
                        val date = matchesByID[entry.matchId]?.startedAt ?: return@mapNotNull null
                        ProfileStats.ScoreRecord(score, date)
                    }
                val best =
                    if (direction ==
                        Direction.HighestWins
                    ) {
                        scored.maxByOrNull { it.value }
                    } else {
                        scored.minByOrNull { it.value }
                    }
                val worst =
                    if (direction ==
                        Direction.HighestWins
                    ) {
                        scored.minByOrNull { it.value }
                    } else {
                        scored.maxByOrNull { it.value }
                    }

                ProfileStats.GameBreakdown(
                    gameID = gameID,
                    gameName = name,
                    played = played,
                    wins = wins,
                    winRate = winRate,
                    bestScore = best,
                    worstScore = worst,
                )
            }.sortedByDescending { it.played }
    }

    /** Doc 06 : adversaire croisé au moins 5 fois avec le plus faible taux de victoire. */
    private fun computeNemesis(
        playerID: UUID,
        participations: List<ParticipantEntity>,
        matchesByID: Map<UUID, MatchEntity>,
        participantsByMatch: Map<UUID, List<ParticipantEntity>>,
    ): ProfileStats.Nemesis? {
        data class Aggregate(
            var name: String,
            var together: Int = 0,
            var wins: Int = 0,
        )
        val byOpponent = mutableMapOf<UUID, Aggregate>()

        for (participation in participations) {
            val match = matchesByID[participation.matchId] ?: continue
            val won = participation.finalRank == 1
            for (opponent in participantsByMatch[match.id].orEmpty()) {
                val opponentPlayerID = opponent.playerId ?: continue
                if (opponentPlayerID == playerID) continue
                val aggregate = byOpponent.getOrPut(opponentPlayerID) { Aggregate(opponent.nicknameSnapshot) }
                aggregate.together += 1
                if (won) aggregate.wins += 1
                aggregate.name = opponent.nicknameSnapshot
            }
        }

        return byOpponent
            .filter { it.value.together >= 5 }
            .map { (id, aggregate) ->
                ProfileStats.Nemesis(
                    playerID = id,
                    name = aggregate.name,
                    matchesTogether = aggregate.together,
                    winRateWithThemPresent = aggregate.wins.toDouble() / aggregate.together,
                )
            }.minByOrNull { it.winRateWithThemPresent }
    }

    private fun winStreaks(
        participations: List<ParticipantEntity>,
        matchesByID: Map<UUID, MatchEntity>,
    ): Pair<Int, Int> {
        val ordered = participations.sortedBy { matchesByID[it.matchId]?.startedAt ?: Instant.MIN }
        var best = 0
        var running = 0
        for (participation in ordered) {
            if (participation.finalRank == 1) {
                running += 1
                best = maxOf(best, running)
            } else {
                running = 0
            }
        }
        return running to best
    }

    private fun monthlyActivity(
        participations: List<ParticipantEntity>,
        matchesByID: Map<UUID, MatchEntity>,
    ): List<ProfileStats.MonthActivity> {
        val zone = ZoneId.systemDefault()
        val counts = mutableMapOf<String, Int>()
        for (participation in participations) {
            val startedAt = matchesByID[participation.matchId]?.startedAt ?: continue
            val key = YearMonth.from(startedAt.atZone(zone)).toString() // "yyyy-MM"
            counts[key] = (counts[key] ?: 0) + 1
        }

        val now = YearMonth.now(zone)
        return (11 downTo 0).map { offset ->
            val month = now.minusMonths(offset.toLong())
            ProfileStats.MonthActivity(monthKey = month.toString(), count = counts[month.toString()] ?: 0)
        }
    }
}
