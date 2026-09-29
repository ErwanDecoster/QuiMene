package com.quimene.app.screenshots

import com.quimene.app.di.AppContainer
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.VariantSelection
import com.quimene.store.MatchRepository
import com.quimene.store.PlayerEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Miroir de `StoreScreenshots.swift` — `spec/screenshots/demo-data.json` enregistré comme si ces
 * parties avaient été jouées sur l'appareil, via les mêmes dépôts que l'app. */
@Serializable
internal data class DemoData(
    val players: List<Player>,
    val matches: List<Match>,
) {
    @Serializable
    data class Player(
        val id: String,
        val nickname: String,
        val emoji: String,
        val palette: String,
        val isMe: Boolean = false,
    )

    @Serializable
    data class Match(
        val game: String,
        val players: List<String>,
        val startedMinutesAgo: Long,
        val minutesPerRound: Long,
        /** `inProgress`, `auto` (fin détectée par les règles) ou `manual` (« Terminer la partie »). */
        val end: String,
        val rounds: List<Round>,
    ) {
        @Serializable
        data class Round(
            val scores: List<Int>,
            /** Place (`seatIndex`) du joueur qui ferme la manche — Skyjo seulement. */
            val closedBy: Int? = null,
        )
    }

    companion object {
        suspend fun seed(
            file: File,
            container: AppContainer,
        ) {
            val demo = Json.decodeFromString(serializer(), file.readText())
            val players = mutableMapOf<String, PlayerEntity>()
            for (player in demo.players) {
                val entity =
                    container.playerRepository.create(
                        nickname = player.nickname,
                        avatarKind = "emoji",
                        avatarValue = player.emoji,
                        paletteID = player.palette,
                    )
                if (player.isMe) container.playerRepository.sharedProfileID(entity)
                players[player.id] = container.playerRepository.allPlayers().first { it.id == entity.id }
            }

            val now = Instant.now()
            for (match in demo.matches) {
                seedMatch(match, players, now, container)
            }
        }

        private suspend fun seedMatch(
            match: Match,
            players: Map<String, PlayerEntity>,
            now: Instant,
            container: AppContainer,
        ) {
            val definition =
                container.catalog.allGames.firstOrNull { it.id == match.game }
                    ?: error("Jeu inconnu : ${match.game}")
            val participants =
                match.players.mapIndexed { seat, key ->
                    Participant(displayName = players.getValue(key).nickname, seatIndex = seat)
                }
            val start = now.minus(Duration.ofMinutes(match.startedMinutesAgo))
            val timeAfterRounds = { count: Int -> start.plus(Duration.ofMinutes(count * match.minutesPerRound)) }

            val events =
                mutableListOf(
                    StampedEvent(
                        lamport = 0uL,
                        deviceID = "local",
                        occurredAt = start,
                        event =
                            MatchEvent.MatchCreated(
                                gameID = definition.id,
                                rulesVersion = definition.rulesVersion,
                                variants = VariantSelection(definition.variants.associate { it.id to it.defaultValue }),
                                participants = participants,
                            ),
                    ),
                )
            match.rounds.forEachIndexed { index, round ->
                val inputs =
                    participants.map { participant ->
                        val closed = round.closedBy == participant.seatIndex
                        ScoreInput(
                            participantID = participant.id,
                            rawValue = round.scores[participant.seatIndex],
                            modifiers = if (closed) setOf(ModifierID.closedRound) else emptySet(),
                        )
                    }
                events +=
                    StampedEvent(
                        lamport = events.size.toULong(),
                        deviceID = "local",
                        occurredAt = timeAfterRounds(index + 1),
                        event = MatchEvent.RoundCommitted(RoundDraft(index = index, inputs = inputs)),
                    )
            }
            if (match.end == "manual") {
                events +=
                    StampedEvent(
                        lamport = events.size.toULong(),
                        deviceID = "local",
                        occurredAt = timeAfterRounds(match.rounds.size),
                        event = MatchEvent.MatchEndedManually,
                    )
            }

            val record =
                container.matchRepository.createMirroredMatch(
                    UUID.randomUUID(),
                    events,
                    container.catalog,
                ) { participant ->
                    val player = players.getValue(match.players[participant.seatIndex])
                    MatchRepository.ParticipantSeed(
                        player = player,
                        nickname = participant.displayName,
                        avatarKind = player.avatarKind,
                        avatarValue = player.avatarValue,
                        paletteID = player.paletteID,
                    )
                } ?: error("Journal sans matchCreated : ${match.game}")
            // Une erreur dans les scores du JSON (seuil jamais atteint…) se voit ici plutôt que sur
            // une capture qui montrerait une partie encore en cours.
            check((record.status == MatchStatus.Ended) == (match.end != "inProgress")) {
                "${match.game} : statut ${record.status}, attendu ${match.end}"
            }
            // Seul le profil de cet appareil est partagé : rien à déposer pour un ami.
            container.matchRepository.markSharedProfileSyncComplete(record)
        }
    }
}
