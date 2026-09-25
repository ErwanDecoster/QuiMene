package com.quimene.app.livesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.store.MatchEntity
import com.quimene.store.MatchRepository
import com.quimene.store.PlayerEntity
import com.quimene.store.PlayerRepository
import com.quimene.sync.LinkedSeat
import com.quimene.sync.OnlineSession
import com.quimene.sync.OnlineSessionError
import com.quimene.sync.ProfileCard
import com.quimene.sync.SeatRef
import com.quimene.sync.SessionEventRecord
import com.quimene.sync.SessionIdentityEvent
import com.quimene.sync.SessionPresence
import com.quimene.sync.SupabaseSessionBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
    private val playerRepository: PlayerRepository,
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

    /** Doc 16, phase D — « Théo s'est associé à la fiche Théo », avec annulation. */
    data class ClaimNotice(
        val id: UUID,
        val profile: ProfileCard,
        val ficheName: String,
    )

    var claimNotices: List<ClaimNotice> by mutableStateOf(emptyList())
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
     * est déjà ouverte, y rattache simplement la partie. Exécuté dans la portée applicative :
     * l'appelant (la feuille de partage) peut être annulé — feuille fermée, ou recomposée dès que
     * la partie est rattachée — sans interrompre la publication du journal. */
    suspend fun startSharing(
        match: MatchEntity,
        deviceName: String,
        allowsContributors: Boolean,
    ) = scope.async { startSharingDetached(match, deviceName, allowsContributors) }.await()

    private suspend fun startSharingDetached(
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

    /** Rattache une partie : publie ce que le serveur n'a pas encore de son journal local, puis la
     * copie locale devient le miroir du journal serveur. */
    private suspend fun attach(match: MatchEntity) {
        if (link == null) return
        attachedMatch = match
        attachedMatchID = match.id
        publishPendingEvents()
        mirrorAttachedMatch()
        handleClaims()
    }

    /** Publie la fin du journal local que le serveur n'a pas encore (mêmes identifiants : celui du
     * `matchCreated` est celui de la partie ; l'ajout est idempotent par identifiant). Reprend une
     * publication interrompue (réseau coupé) au prochain rattrapage. Rien si le journal serveur
     * n'est pas un début du journal local : il a alors avancé ailleurs, et c'est lui qui fait foi. */
    private suspend fun publishPendingEvents() {
        val link = link ?: return
        val match = attachedMatch?.let { matchRepository.match(it.id) } ?: return
        val serverIDs = link.session.eventsForMatch(match.id).map { it.id }
        val localLog = matchRepository.currentLog(match)
        if (serverIDs.size >= localLog.size || localLog.take(serverIDs.size).map { it.id } != serverIDs) return
        for (stamped in localLog.drop(serverIDs.size)) {
            val result = link.submit(stamped.event, match.id, stamped.id, stamped.occurredAt)
            if (result !is SessionLink.SubmitResult.Accepted) break
        }
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
        handleClaims()
    }

    suspend fun onForeground() {
        link?.refresh()
        publishPendingEvents()
        handleClaims()
    }

    private suspend fun connect(
        sessionID: UUID,
        code: String,
        deviceName: String,
    ) {
        val deviceID = deviceID ?: resolveDeviceID().also { this.deviceID = it }
        val session = OnlineSession(sessionID, code, deviceID, backend)
        val link =
            SessionLink(
                session,
                code,
                SessionPresence(deviceID, deviceName, isOwner = true),
                context,
                scope,
                ownerDeviceID = deviceID,
            )
        link.onNewRecords = { handle(it) }
        link.onNewIdentities = { handleClaims() }
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
        link?.let { HandledClaims.clear(context, it.sessionID) }
        link = null
        attachedMatch = null
        attachedMatchID = null
        allowsContributors = true
        claimNotices = emptyList()
    }

    // Qui es-tu ? (doc 16, phase D)

    /** Publie le registre du créateur — son profil, et les places que ses fiches relient déjà à un
     * profil — s'il a changé depuis le dernier publié. Un ami déjà lié est ainsi reconnu à son
     * arrivée sans qu'on lui demande qui il est, et sa place ne peut pas être prise. */
    private suspend fun publishRosterIfNeeded() {
        val link = link ?: return
        val match = attachedMatch ?: return
        val owner = runCatching { myProfileCard(playerRepository) }.getOrNull()
        val linkedSeats =
            matchRepository.participants(match.id).sortedBy { it.seatIndex }.mapNotNull { participant ->
                val profileID =
                    participant.playerId?.let { resolvePlayer(it) }?.sharedProfileID ?: return@mapNotNull null
                LinkedSeat(SeatRef(participant.seatIndex, participant.nicknameSnapshot), profileID)
            }
        val current = link.identities
        if (current.owner == owner && current.linkedSeats == linkedSeats.associate { it.seat to it.profileID }) return
        link.submitIdentity(SessionIdentityEvent.roster(owner, linkedSeats, deviceID ?: resolveDeviceID()), match.id)
    }

    /** Chaque revendication retenue, une seule fois (même si elle date d'avant un redémarrage, ou
     * d'un moment où le créateur était hors ligne) : la fiche de cette place, si elle ne
     * représente encore personne, est liée au profil — durablement, comme après un scan de QR de
     * profil (doc 14) — et le créateur en est averti. Puis le registre est republié. */
    private suspend fun handleClaims() {
        val link = link ?: return
        val match = attachedMatch ?: return
        val me = deviceID ?: resolveDeviceID()
        val handled = HandledClaims.load(context, link.sessionID).toMutableSet()
        val participants = matchRepository.participants(match.id)
        for (claim in link.identities.activeClaims) {
            if (claim.deviceID == me || claim.claimID in handled) continue
            val participant =
                participants.firstOrNull {
                    it.seatIndex == claim.seat.seatIndex && it.nicknameSnapshot == claim.seat.displayName
                } ?: continue
            val fiche = participant.playerId?.let { resolvePlayer(it) } ?: continue
            handled += claim.claimID
            if (fiche.sharedProfileID != null) continue
            playerRepository.linkSharedProfile(claim.profile.id, claim.profile.name, fiche)
            claimNotices = claimNotices + ClaimNotice(claim.claimID, claim.profile, fiche.nickname)
        }
        HandledClaims.save(context, link.sessionID, handled)
        publishRosterIfNeeded()
    }

    /** « Annuler » : la revendication est retirée pour tous, et la fiche déliée. */
    suspend fun revoke(notice: ClaimNotice) {
        claimNotices = claimNotices - notice
        val link = link ?: return
        val match = attachedMatch ?: return
        link.submitIdentity(SessionIdentityEvent.revoke(notice.id, deviceID ?: resolveDeviceID()), match.id)
        playerRepository.player(notice.profile.id)?.takeIf { !it.sharedProfileIsMine }?.let {
            playerRepository.unlinkSharedProfile(it)
        }
        publishRosterIfNeeded()
    }

    fun dismiss(notice: ClaimNotice) {
        claimNotices = claimNotices - notice
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

/** Doc 16, phase D — les revendications déjà traitées par le créateur, par session : une fiche
 * n'est liée, et le créateur averti, qu'une fois, même après un redémarrage. */
private object HandledClaims {
    private fun prefs(context: Context) = context.getSharedPreferences("online_session", Context.MODE_PRIVATE)

    private fun key(sessionID: UUID) = "handledClaims.$sessionID"

    fun load(
        context: Context,
        sessionID: UUID,
    ): Set<UUID> =
        prefs(context)
            .getStringSet(key(sessionID), emptySet())
            .orEmpty()
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .toSet()

    fun save(
        context: Context,
        sessionID: UUID,
        claims: Set<UUID>,
    ) {
        prefs(context).edit { putStringSet(key(sessionID), claims.map { it.toString() }.toSet()) }
    }

    fun clear(
        context: Context,
        sessionID: UUID,
    ) {
        prefs(context).edit { remove(key(sessionID)) }
    }
}
