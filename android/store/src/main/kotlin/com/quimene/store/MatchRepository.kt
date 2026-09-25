package com.quimene.store

import com.quimene.domain.engine.MatchEngine
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.SharedMatchPackage
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.GameCatalog
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
        // La dernière manche *présente*, pas la plus grande jamais validée dans le journal : une
        // manche déjà annulée y figure toujours, et une deuxième annulation de suite la visait à
        // nouveau au lieu de retirer la précédente (sans effet visible).
        val lastIndex =
            loadState(match, catalog).rounds.maxOfOrNull { it.index } ?: return loadState(match, catalog)
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
     * [com.quimene.domain.model.Participant] du domaine n'en porte pas, lui). */
    suspend fun participants(matchID: UUID): List<ParticipantEntity> = participantDao.forMatch(matchID)

    /** Doc 16 — les participants dont la fiche est liée à un profil : `true` pour mon profil,
     * `false` pour un ami. */
    suspend fun linkedParticipants(matchID: UUID): Map<UUID, Boolean> =
        participantDao
            .forMatch(matchID)
            .mapNotNull { participant ->
                val player = participant.playerId?.let { playerDao.get(it) } ?: return@mapNotNull null
                if (player.sharedProfileID == null) null else participant.id to player.sharedProfileIsMine
            }.toMap()

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

    /** Doc 16, phase E — enregistre une partie reçue d'un ami (boîte aux lettres), complète : même
     * journal, mêmes participants, rejouée comme une partie jouée ici. Chaque joueur lié à un profil
     * connu de cet appareil (le mien, ou un ami) est relié à sa fiche ; les autres gardent seulement
     * leurs snapshots. Sans effet si la partie est déjà connue. Miroir de `importSharedMatch`. */
    suspend fun importSharedMatch(
        pkg: SharedMatchPackage,
        catalog: GameCatalog,
    ): MatchEntity? {
        matchDao.get(pkg.matchID)?.let { return it }
        val byID = pkg.participants.associateBy { it.participantID }
        val players =
            pkg.participants.mapNotNull { it.sharedProfileID }.associateWith {
                playerDao.bySharedProfileID(
                    it,
                )
            }
        return createMirroredMatch(pkg.matchID, pkg.events, catalog, isReceived = true) { participant ->
            val entry = byID[participant.id]
            ParticipantSeed(
                player = entry?.sharedProfileID?.let { players[it] },
                nickname = entry?.nickname ?: participant.displayName,
                avatarKind = entry?.avatarKind ?: "emoji",
                avatarValue = entry?.avatarValue.orEmpty(),
                paletteID = entry?.paletteID ?: "1",
            )
        }
    }

    /** Doc 16 — le journal complet d'une partie, tel que `LiveShareCoordinator` le publie dans
     * une session en ligne. */
    suspend fun currentLog(match: MatchEntity): List<StampedEvent> = decodeEvents(match.eventLogData)

    /** Doc 16, phase C — une partie partagée en ligne : le journal de la session (serveur) fait
     * foi, la copie locale du créateur en est le miroir. Remplace le journal local en entier. */
    suspend fun replaceLog(
        events: List<StampedEvent>,
        match: MatchEntity,
        catalog: GameCatalog,
    ): MatchState = persist(events, match, catalog)

    /** Doc 16, phase C — copie locale d'une partie lancée par un autre appareil de la session
     * (« Partie suivante » d'un participant) — miroir de `createMirroredMatch` (Swift). Les
     * participants gardent l'identifiant de son `matchCreated` ; [seed] donne, pour chacun, la
     * fiche et l'avatar à retenir. `null` si le journal ne commence pas par un `matchCreated`. */
    suspend fun createMirroredMatch(
        id: UUID,
        events: List<StampedEvent>,
        catalog: GameCatalog,
        isReceived: Boolean = false,
        seed: (Participant) -> ParticipantSeed,
    ): MatchEntity? {
        val first = events.firstOrNull() ?: return null
        val created = first.event as? MatchEvent.MatchCreated ?: return null
        val participantEntities =
            created.participants.map { participant ->
                val source = seed(participant)
                ParticipantEntity(
                    id = participant.id,
                    playerId = source.player?.id,
                    nicknameSnapshot = source.nickname,
                    avatarKindSnapshot = source.avatarKind,
                    avatarValueSnapshot = source.avatarValue,
                    paletteIDSnapshot = source.paletteID,
                    seatIndex = participant.seatIndex,
                    teamID = participant.teamID,
                    matchId = id,
                )
            }
        val match =
            MatchEntity(
                id = id,
                gameID = created.gameID,
                rulesVersion = created.rulesVersion,
                variantsData = encodeJson(VariantSelection.serializer(), created.variants),
                startedAt = first.occurredAt,
                deviceOrigin = if (isReceived) MatchEntity.RECEIVED_ORIGIN else first.deviceID,
                eventLogData = encodeEvents(events),
            )
        matchDao.insert(match)
        participantDao.insertAll(participantEntities)
        persist(events, match, catalog)
        return matchDao.get(id)
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
                // L'heure du dernier événement, pas celle de l'écriture : une copie faite plus tard
                // (partie suivie depuis un autre appareil, reçue d'un ami) garde la vraie heure.
                endedAt = if (ended) events.lastOrNull()?.occurredAt ?: Instant.now() else null,
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
