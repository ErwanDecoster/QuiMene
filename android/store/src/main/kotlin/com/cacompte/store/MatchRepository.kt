package com.cacompte.store

import com.cacompte.domain.engine.MatchEngine
import com.cacompte.domain.engine.MatchEvent
import com.cacompte.domain.engine.StampedEvent
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.SharedMatchSummaryPayload
import com.cacompte.domain.model.VariantSelection
import com.cacompte.domain.rules.GameCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `MatchRepository.swift`. `eventLogData` reste **la source de vérité** : chaque
 * écriture réencode le journal complet et rejoue via [MatchEngine], plutôt que de faire
 * confiance à un total mis en cache (ADR-0005).
 */
class MatchRepository(
    private val matchDao: MatchDao,
    private val participantDao: ParticipantDao,
    private val playerDao: PlayerDao,
) {
    /** Miroir de `MatchRepository.ParticipantSeed`. */
    data class ParticipantSeed(
        val player: PlayerEntity?,
        val nickname: String,
        val avatarKind: String,
        val avatarValue: String,
        val paletteID: String,
        /** Doc 05 « Belote » — `null` pour tous les jeux individuels. */
        val teamID: String? = null,
    )

    suspend fun createMatch(
        gameID: String,
        rulesVersion: Int,
        variants: VariantSelection,
        seeds: List<ParticipantSeed>,
        deviceID: String = "local",
    ): MatchEntity {
        val participantEntities = mutableListOf<ParticipantEntity>()
        val domainParticipants = mutableListOf<Participant>()
        val matchID = UUID.randomUUID()

        for ((index, seed) in seeds.withIndex()) {
            val id = UUID.randomUUID()
            participantEntities +=
                ParticipantEntity(
                    id = id,
                    playerId = seed.player?.id,
                    nicknameSnapshot = seed.nickname,
                    avatarKindSnapshot = seed.avatarKind,
                    avatarValueSnapshot = seed.avatarValue,
                    paletteIDSnapshot = seed.paletteID,
                    seatIndex = index,
                    teamID = seed.teamID,
                    matchId = matchID,
                )
            domainParticipants +=
                Participant(id = id, displayName = seed.nickname, seatIndex = index, teamID = seed.teamID)
        }

        val createdEvent =
            StampedEvent(
                lamport = 0uL,
                deviceID = deviceID,
                occurredAt = Instant.now(),
                event =
                    MatchEvent.MatchCreated(
                        gameID = gameID,
                        rulesVersion = rulesVersion,
                        variants = variants,
                        participants = domainParticipants,
                    ),
            )

        val match =
            MatchEntity(
                id = matchID,
                gameID = gameID,
                rulesVersion = rulesVersion,
                variantsData = encodeJson(VariantSelection.serializer(), variants),
                deviceOrigin = deviceID,
                eventLogData = encodeEvents(listOf(createdEvent)),
            )
        matchDao.insert(match)
        participantDao.insertAll(participantEntities)
        return match
    }

    suspend fun loadState(
        match: MatchEntity,
        catalog: GameCatalog,
    ): MatchState {
        val events = decodeEvents(match.eventLogData)
        return MatchEngine().replay(events, catalog)
    }

    suspend fun commitRound(
        draft: RoundDraft,
        match: MatchEntity,
        catalog: GameCatalog,
        deviceID: String = "local",
    ): MatchState = appendEvent(MatchEvent.RoundCommitted(draft), match, catalog, deviceID)

    suspend fun undoLastRound(
        match: MatchEntity,
        catalog: GameCatalog,
        deviceID: String = "local",
    ): MatchState {
        val events = decodeEvents(match.eventLogData)
        val lastIndex =
            events
                .mapNotNull { stamped ->
                    (stamped.event as? MatchEvent.RoundCommitted)?.draft?.index
                }.maxOrNull() ?: return loadState(match, catalog)
        return appendEvent(MatchEvent.RoundRemoved(lastIndex), match, catalog, deviceID)
    }

    /** Doc 05 « Jeu libre » et tout jeu `manualStop` : `endCheck` ne détecte jamais cette fin
     * tout seul, elle vient toujours d'une action explicite du joueur. */
    suspend fun endMatchManually(
        match: MatchEntity,
        catalog: GameCatalog,
        deviceID: String = "local",
    ): MatchState = appendEvent(MatchEvent.MatchEndedManually, match, catalog, deviceID)

    /** Une partie abandonnée reçoit quand même un classement final et apparaît dans
     * l'historique — contrairement à une simple suppression, rien n'est perdu. */
    suspend fun abandonMatch(
        match: MatchEntity,
        catalog: GameCatalog,
        deviceID: String = "local",
    ): MatchState = appendEvent(MatchEvent.MatchAbandoned(Instant.now()), match, catalog, deviceID)

    suspend fun archive(match: MatchEntity) {
        matchDao.update(match.copy(isArchived = true))
    }

    suspend fun unarchive(match: MatchEntity) {
        matchDao.update(match.copy(isArchived = false))
    }

    /** Suppression définitive — cascade sur [ParticipantEntity] (clé étrangère `ON DELETE
     * CASCADE`) : ces lignes disparaissent avec la partie, contrairement à
     * [PlayerRepository.delete] qui laisse l'historique intact. */
    suspend fun delete(match: MatchEntity) {
        matchDao.delete(match)
    }

    /** Miroir réactif de [inProgressMatches]/[finishedMatches]/[archivedMatches] — indispensable
     * pour un écran d'onglet (Historique, catalogue de jeux) : la `ViewModel` d'un onglet vit
     * aussi longtemps que son entrée de pile de retour est conservée (`saveState`/`restoreState`
     * du sélecteur d'onglets), donc `init{}` ne se relance pas à chaque retour sur l'onglet — un
     * chargement ponctuel (`suspend fun`) y resterait figé sur l'instantané du premier passage,
     * même après qu'une partie se soit conclue ailleurs dans l'app. */
    fun observeAll(): Flow<List<MatchEntity>> = matchDao.observeAll()

    /** Miroir réactif de [participants] — nécessaire au filtre par joueur de l'historique
     * (`HistoryViewModel`), qui doit croiser toutes les parties avec tous les participants. */
    fun observeAllParticipants(): Flow<List<ParticipantEntity>> = participantDao.observeAll()

    suspend fun inProgressMatches(): List<MatchEntity> =
        matchDao
            .getAll()
            .filter { it.status == MatchStatus.InProgress || it.status == MatchStatus.FinalRound }
            .sortedByDescending { it.startedAt }

    suspend fun match(id: UUID): MatchEntity? = matchDao.get(id)

    /** Snapshots d'avatar/pseudo des participants (`:app` en a besoin pour l'affichage — le
     * [com.cacompte.domain.model.Participant] du domaine n'en porte pas, lui). */
    suspend fun participants(matchID: UUID): List<ParticipantEntity> = participantDao.forMatch(matchID)

    suspend fun hasAnyMatch(): Boolean = matchDao.count() > 0

    suspend fun finishedMatches(): List<MatchEntity> =
        matchDao
            .getAll()
            .filter { !it.isArchived && (it.status == MatchStatus.Ended || it.status == MatchStatus.Abandoned) }
            .sortedByDescending { it.startedAt }

    suspend fun archivedMatches(): List<MatchEntity> =
        matchDao.getAll().filter { it.isArchived }.sortedByDescending { it.startedAt }

    /** Joueurs de la dernière partie jouée à ce jeu, dans l'ordre des sièges — sert à
     * pré-sélectionner les habitués à la mise en place d'une nouvelle partie. Vide si ce jeu n'a
     * jamais été joué. */
    suspend fun mostRecentParticipants(gameID: String): List<PlayerEntity> {
        val lastMatch =
            matchDao
                .getAll()
                .filter {
                    it.gameID == gameID && (it.status == MatchStatus.Ended || it.status == MatchStatus.Abandoned)
                }.maxByOrNull { it.startedAt }
                ?: return emptyList()
        return participantDao
            .forMatch(lastMatch.id)
            .sortedBy { it.seatIndex }
            .mapNotNull { participant -> participant.playerId?.let { playerDao.get(it) } }
    }

    /** Nombre de parties jouées (terminées ou abandonnées), tous jeux confondus, par joueur. */
    suspend fun participationCounts(): Map<UUID, Int> {
        val relevantMatchIDs =
            matchDao
                .getAll()
                .filter { it.status == MatchStatus.Ended || it.status == MatchStatus.Abandoned }
                .map { it.id }
                .toSet()
        val counts = mutableMapOf<UUID, Int>()
        for (participant in participantDao.getAll()) {
            if (participant.matchId !in relevantMatchIDs) continue
            val playerId = participant.playerId ?: continue
            counts[playerId] = (counts[playerId] ?: 0) + 1
        }
        return counts
    }

    /** Doc 14, phase 2 — parties conclues avec au moins un participant lié, dont le résumé n'a
     * pas encore été confirmé poussé. */
    suspend fun matchesPendingSharedProfileSync(): List<MatchEntity> =
        matchDao.getAll().filter { it.pendingSharedProfileSync }

    suspend fun markSharedProfileSyncComplete(match: MatchEntity) {
        matchDao.update(match.copy(pendingSharedProfileSync = false))
    }

    /** Doc 14, phase 2 — matérialise le résumé reçu de l'installation d'un ami en une partie
     * locale minimale : un journal d'événements vide (jamais rejoué, voir
     * [MatchEntity.isImportedSummary]), seuls `finalRank`/`finalScore` portent le résultat. */
    suspend fun materializeSharedSummary(
        payload: SharedMatchSummaryPayload,
        matchID: UUID,
    ) {
        if (matchDao.get(matchID) != null) return

        val participants =
            payload.standings.mapIndexed { index, entry ->
                val linkedPlayer = entry.sharedProfileID?.let { playerDao.bySharedProfileID(it) }
                ParticipantEntity(
                    playerId = linkedPlayer?.id,
                    nicknameSnapshot = entry.nickname,
                    avatarKindSnapshot = entry.avatarKind,
                    avatarValueSnapshot = entry.avatarValue,
                    paletteIDSnapshot = entry.paletteID,
                    seatIndex = index,
                    finalRank = entry.rank,
                    finalScore = entry.score,
                    matchId = matchID,
                )
            }

        val match =
            MatchEntity(
                id = matchID,
                gameID = payload.gameID,
                rulesVersion = payload.rulesVersion,
                variantsData = ByteArray(0),
                startedAt = payload.playedAt,
                endedAt = payload.playedAt,
                status = MatchStatus.Ended,
                deviceOrigin = "shared-profile-import",
                eventLogData = encodeEvents(emptyList()),
                isImportedSummary = true,
            )
        matchDao.insert(match)
        participantDao.insertAll(participants)
    }

    /** Doc 09 — le journal complet d'une partie, tel que `LiveSession` (étape F) en a besoin
     * pour s'y resynchroniser ou pour accueillir un nouveau pair. */
    suspend fun currentLog(match: MatchEntity): List<StampedEvent> = decodeEvents(match.eventLogData)

    /** Doc 09 « hôte autoritaire » — persiste un événement déjà validé et horodaté par
     * `LiveSession` (une manche acceptée d'un contributeur distant). Idempotent par `id`. */
    suspend fun appendRemoteEvent(
        stamped: StampedEvent,
        match: MatchEntity,
        catalog: GameCatalog,
    ): MatchState {
        val events = currentLog(match)
        if (events.any { it.id == stamped.id }) {
            return MatchEngine().replay(events, catalog)
        }
        return persist(events + stamped, match, catalog)
    }

    private suspend fun appendEvent(
        event: MatchEvent,
        match: MatchEntity,
        catalog: GameCatalog,
        deviceID: String,
    ): MatchState {
        val events = currentLog(match)
        val nextLamport = (events.maxOfOrNull { it.lamport } ?: 0uL) + 1uL
        val stamped =
            StampedEvent(lamport = nextLamport, deviceID = deviceID, occurredAt = Instant.now(), event = event)
        return persist(events + stamped, match, catalog)
    }

    private suspend fun persist(
        events: List<StampedEvent>,
        match: MatchEntity,
        catalog: GameCatalog,
    ): MatchState {
        val state = MatchEngine().replay(events, catalog)
        val ended = state.status == MatchStatus.Ended || state.status == MatchStatus.Abandoned

        var updated =
            match.copy(
                eventLogData = encodeEvents(events),
                status = state.status,
                endReasonRaw = state.endReason?.name,
                endedAt = if (ended) Instant.now() else null,
            )

        if (ended) {
            applyFinalStandings(state, updated, catalog)
            // Réévalué à chaque conclusion, jamais figé à la création de la partie — un lien
            // partagé peut avoir été fait après coup entre deux manches. Un participant "lié"
            // au sens de ce drapeau est un participant dont le PlayerEntity a un
            // sharedProfileID posé — pas simplement un participant relié à une fiche locale.
            val anyLinked =
                participantDao.forMatch(match.id).any { participant ->
                    val playerId = participant.playerId ?: return@any false
                    playerDao.get(playerId)?.sharedProfileID != null
                }
            updated = updated.copy(pendingSharedProfileSync = anyLinked)
        }

        matchDao.update(updated)
        return state
    }

    private suspend fun applyFinalStandings(
        state: MatchState,
        match: MatchEntity,
        catalog: GameCatalog,
    ) {
        val definition = runCatching { catalog.definition(match.gameID, match.rulesVersion) }.getOrNull() ?: return
        val rules = runCatching { catalog.rules(match.gameID, match.rulesVersion) }.getOrNull() ?: return

        val standingsByID = rules.standings(state, definition).associateBy { it.participantID }
        val participants = participantDao.forMatch(match.id)
        val updated =
            participants.mapNotNull { participant ->
                val standing = standingsByID[participant.id] ?: return@mapNotNull null
                participant.copy(finalRank = standing.rank, finalScore = standing.score)
            }
        if (updated.isNotEmpty()) participantDao.updateAll(updated)
    }

    private fun <T> encodeJson(
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
    ): ByteArray = Json.encodeToString(serializer, value).encodeToByteArray()

    private fun encodeEvents(events: List<StampedEvent>): ByteArray =
        Json.encodeToString(ListSerializer(StampedEvent.serializer()), events).encodeToByteArray()

    private fun decodeEvents(data: ByteArray): List<StampedEvent> =
        if (data.isEmpty()) {
            emptyList()
        } else {
            Json.decodeFromString(ListSerializer(StampedEvent.serializer()), data.decodeToString())
        }
}
