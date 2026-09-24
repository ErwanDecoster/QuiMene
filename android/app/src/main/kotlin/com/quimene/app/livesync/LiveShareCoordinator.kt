package com.quimene.app.livesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.store.MatchEntity
import com.quimene.store.MatchRepository
import com.quimene.store.PlayerEntity
import com.quimene.sync.OnlineSession
import com.quimene.sync.OnlineSessionError
import com.quimene.sync.SessionEventRecord
import com.quimene.sync.SessionPresence
import com.quimene.sync.SupabaseSessionBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/** Une manche (ou un autre événement) ajoutée par un autre appareil à la partie rattachée, déjà
 * recopiée en local : [com.quimene.app.features.livematch.LiveMatchViewModel] recharge et
 * l'annonce. */
data class RemoteMatchUpdate(
    val matchID: UUID,
    val deviceName: String?,
    val isRoundCommit: Boolean,
)

/** Doc 16, phase C — un autre appareil a lancé la partie suivante ; sa copie locale existe déjà.
 * L'écran de la partie précédente y bascule. */
data class RemoteStartedMatch(
    val newMatchID: UUID,
    val previousMatchID: UUID?,
)

/**
 * Doc 16, phase C — miroir de `LiveShareCoordinator.swift` : côté créateur d'une session en ligne.
 * Le journal de chaque partie partagée vit sur le serveur et fait foi ; la partie locale en est le
 * miroir. Survit à l'écran de partie et au redémarrage de l'app ([PersistedOnlineSession]) : seul
 * « Arrêter le partage » termine la session (créateur uniquement).
 */
class LiveShareCoordinator(
    private val catalog: GameCatalog,
    private val matchRepository: MatchRepository,
    private val resolvePlayer: suspend (UUID) -> PlayerEntity?,
    val context: Context,
    private val resolveDeviceID: suspend () -> String,
    private val scope: CoroutineScope,
) {
    private val backend = SupabaseSessionBackend()
    private var attachedMatch: MatchEntity? = null
    private var deviceID: String? = null

    var link: SessionLink? by mutableStateOf(null)
        private set
    var attachedMatchID: UUID? by mutableStateOf(null)
        private set
    var allowsContributors: Boolean by mutableStateOf(true)
        private set

    val pairingCode: String? get() = link?.pairingCode
    val sessionID: UUID? get() = link?.sessionID

    /** Faux hors ligne : la saisie d'une partie partagée est alors bloquée (doc 16). */
    val isReachable: Boolean get() = link?.isReachable ?: true

    /** Les autres appareils présents dans la session. */
    val connectedPeers: List<SessionPresence>
        get() = link?.presence.orEmpty().filter { it.deviceID != deviceID }

    private val remoteMatchUpdatesFlow = MutableSharedFlow<RemoteMatchUpdate>(extraBufferCapacity = 8)
    val remoteMatchUpdates: SharedFlow<RemoteMatchUpdate> = remoteMatchUpdatesFlow
    private val remoteStartedMatchesFlow = MutableSharedFlow<RemoteStartedMatch>(extraBufferCapacity = 4)
    val remoteStartedMatches: SharedFlow<RemoteStartedMatch> = remoteStartedMatchesFlow

    /** Ouvre une session (nouveau code, retiré si déjà pris) et y publie [match] ; si une session
     * est déjà ouverte, y rattache simplement la partie. */
    suspend fun startSharing(
        match: MatchEntity,
        deviceName: String,
        allowsContributors: Boolean,
    ) {
        if (link != null) {
            attach(match)
            return
        }
        val deviceID = resolveDeviceID().also { this.deviceID = it }
        val sessionID = UUID.randomUUID()
        var code = OnlineSession.newPairingCode()
        for (attempt in 1..3) {
            try {
                backend.open(sessionID, code, deviceID, allowsContributors)
                break
            } catch (taken: OnlineSessionError.PairingCodeTaken) {
                if (attempt == 3) throw taken
                code = OnlineSession.newPairingCode()
            }
        }
        this.allowsContributors = allowsContributors
        PersistedOnlineSession(sessionID, code, PersistedOnlineSession.Role.Owner, deviceName).save(context)
        connect(sessionID, code, deviceName)
        attach(match)
    }

    /** Rattache une partie : si le serveur ne la connaît pas encore, publie son journal local
     * (mêmes identifiants : celui du `matchCreated` est celui de la partie) ; puis la copie
     * locale devient le miroir du journal serveur. */
    suspend fun attach(match: MatchEntity) {
        val link = link ?: return
        if (attachedMatchID == match.id) return
        attachedMatch = match
        attachedMatchID = match.id
        if (link.session.eventsForMatch(match.id).isEmpty()) {
            for (stamped in matchRepository.currentLog(match)) {
                val result = link.submit(stamped.event, match.id, stamped.id, stamped.occurredAt)
                if (result !is SessionLink.SubmitResult.Accepted) break
            }
        }
        mirrorAttachedMatch()
    }

    /** Au lancement : reprend la session que ce créateur avait ouverte, et sa partie courante. */
    suspend fun resumeIfNeeded() {
        if (link != null) return
        val persisted = PersistedOnlineSession.load(context, PersistedOnlineSession.Role.Owner) ?: return
        deviceID = resolveDeviceID()
        connect(persisted.sessionID, persisted.pairingCode, persisted.deviceName)
        val link = link ?: return
        val currentID = link.session.currentMatchID() ?: return
        attachedMatch = matchRepository.match(currentID) ?: return
        attachedMatchID = currentID
        mirrorAttachedMatch()
    }

    suspend fun onForeground() {
        link?.refresh()
    }

    private suspend fun connect(
        sessionID: UUID,
        code: String,
        deviceName: String,
    ) {
        val deviceID = deviceID ?: resolveDeviceID().also { this.deviceID = it }
        val session = OnlineSession(sessionID, code, deviceID, backend)
        val link = SessionLink(session, code, SessionPresence(deviceID, deviceName, isOwner = true), context, scope)
        link.onNewRecords = { handle(it) }
        this.link = link
        link.start()
    }

    /** Ajoute un événement à une partie de la session ; en cas de succès, la copie locale est
     * déjà à jour au retour. */
    suspend fun submit(
        event: MatchEvent,
        matchID: UUID,
    ): SessionLink.SubmitResult {
        val link = link ?: return SessionLink.SubmitResult.Closed
        val result = link.submit(event, matchID)
        mirrorAttachedMatch()
        return result
    }

    /** « Partie suivante » du créateur : nouvelle partie locale avec les mêmes joueurs (mêmes
     * fiches, mêmes avatars), publiée dans la session ; mêmes variantes si c'est le même jeu. */
    suspend fun startNextMatch(
        definition: GameDefinition,
        previous: MatchEntity,
    ): MatchEntity? {
        if (link == null) return null
        val seeds =
            matchRepository.participants(previous.id).sortedBy { it.seatIndex }.map {
                MatchRepository.ParticipantSeed(
                    player = it.playerId?.let { id -> resolvePlayer(id) },
                    nickname = it.nicknameSnapshot,
                    avatarKind = it.avatarKindSnapshot,
                    avatarValue = it.avatarValueSnapshot,
                    paletteID = it.paletteIDSnapshot,
                )
            }
        val variants =
            if (definition.id == previous.gameID) {
                runCatching {
                    Json.decodeFromString(VariantSelection.serializer(), previous.variantsData.decodeToString())
                }.getOrDefault(VariantSelection())
            } else {
                VariantSelection()
            }
        val match =
            matchRepository.createMatch(
                gameID = definition.id,
                rulesVersion = definition.rulesVersion,
                variants = variants,
                seeds = seeds,
                deviceID = deviceID ?: resolveDeviceID(),
            )
        attach(match)
        return match
    }

    suspend fun setAllowsContributors(allowed: Boolean) {
        val link = link ?: return
        allowsContributors = allowed
        runCatching { backend.open(link.sessionID, link.pairingCode, deviceID ?: resolveDeviceID(), allowed) }
    }

    /** Seul point d'arrêt d'une session (créateur uniquement). */
    suspend fun stopSharing() {
        link?.let { link ->
            runCatching { backend.close(link.sessionID, deviceID ?: resolveDeviceID()) }
            link.stop()
        }
        PersistedOnlineSession.clear(context, PersistedOnlineSession.Role.Owner)
        link = null
        attachedMatch = null
        attachedMatchID = null
        allowsContributors = true
    }

    private suspend fun handle(records: List<SessionEventRecord>) {
        adoptMatchesStartedElsewhere(records)
        mirrorAttachedMatch()
        val remote = records.filter { it.matchID == attachedMatchID && it.event.deviceID != deviceID }
        val last = remote.lastOrNull() ?: return
        remoteMatchUpdatesFlow.emit(
            RemoteMatchUpdate(
                matchID = last.matchID,
                deviceName = link?.deviceName(last.event.deviceID),
                isRoundCommit = remote.any { it.event.event is MatchEvent.RoundCommitted },
            ),
        )
    }

    /** Une partie lancée par un autre appareil (« Partie suivante » d'un participant) : en créer
     * la copie locale — les fiches et avatars de la partie précédente pour les mêmes joueurs, à la
     * même place — et la rattacher. */
    private suspend fun adoptMatchesStartedElsewhere(records: List<SessionEventRecord>) {
        val link = link ?: return
        for (record in records) {
            if (record.event.deviceID == deviceID || record.event.event !is MatchEvent.MatchCreated) continue
            if (matchRepository.match(record.matchID) != null) continue
            val previous = attachedMatch
            val previousBySeat =
                previous
                    ?.let { matchRepository.participants(it.id) }
                    .orEmpty()
                    .associateBy { "${it.seatIndex}|${it.nicknameSnapshot}" }
            val seeds =
                (record.event.event as MatchEvent.MatchCreated).participants.associate { participant ->
                    val source = previousBySeat["${participant.seatIndex}|${participant.displayName}"]
                    participant.id to
                        if (source != null) {
                            MatchRepository.ParticipantSeed(
                                player = source.playerId?.let { resolvePlayer(it) },
                                nickname = source.nicknameSnapshot,
                                avatarKind = source.avatarKindSnapshot,
                                avatarValue = source.avatarValueSnapshot,
                                paletteID = source.paletteIDSnapshot,
                            )
                        } else {
                            generatedSeed(participant.displayName)
                        }
                }
            val created =
                matchRepository.createMirroredMatch(
                    record.matchID,
                    link.session.eventsForMatch(record.matchID),
                    catalog,
                ) {
                    seeds.getValue(it.id)
                } ?: continue
            attachedMatch = created
            attachedMatchID = created.id
            remoteStartedMatchesFlow.emit(RemoteStartedMatch(created.id, previous?.id))
        }
    }

    /** Recopie le journal serveur de la partie rattachée dans sa copie locale — jamais une copie
     * incomplète (publication interrompue hors ligne : la copie locale est gardée). */
    private suspend fun mirrorAttachedMatch() {
        val link = link ?: return
        val match = attachedMatch?.let { matchRepository.match(it.id) } ?: return
        val serverLog = link.session.eventsForMatch(match.id)
        val localLog = matchRepository.currentLog(match)
        if (serverLog.isEmpty() || serverLog.size < localLog.size) return
        if (serverLog.map { it.id } == localLog.map { it.id }) return
        matchRepository.replaceLog(serverLog, match, catalog)
        attachedMatch = matchRepository.match(match.id)
    }

    companion object {
        fun generatedSeed(nickname: String): MatchRepository.ParticipantSeed {
            val avatar = Avatar.generated(nickname)
            return MatchRepository.ParticipantSeed(
                player = null,
                nickname = nickname,
                avatarKind = "emoji",
                avatarValue = (avatar.kind as? AvatarKind.Emoji)?.character.orEmpty(),
                paletteID = avatar.palette.index.toString(),
            )
        }
    }
}
