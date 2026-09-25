package com.quimene.app.livesync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.designsystem.components.PlayerPalette
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import com.quimene.store.DeviceIdentity
import com.quimene.store.PlayerEntity
import com.quimene.store.PlayerRepository
import com.quimene.sync.LiveActivityContent
import com.quimene.sync.LiveActivityPushClient
import com.quimene.sync.OnlineSession
import com.quimene.sync.OnlineSessionError
import com.quimene.sync.ProfileCard
import com.quimene.sync.SessionChannel
import com.quimene.sync.SessionEventRecord
import com.quimene.sync.SessionIdentities
import com.quimene.sync.SessionIdentityEvent
import com.quimene.sync.SessionIdentityRecord
import com.quimene.sync.SessionPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Doc 16, phase C — miroir de `SessionLink.swift` : lien d'un appareil avec une session en ligne,
 * commun au créateur ([LiveShareCoordinator]) et aux participants ([MatchConnectionCoordinator]).
 * Le journal ([OnlineSession]), le canal temps réel, le rattrapage à chaque notification, retour
 * du réseau ou au premier plan, et l'ajout d'un événement. Plus d'hôte qui arbitre : chaque
 * appareil valide sa saisie, le serveur garantit l'ordre.
 */
class SessionLink(
    val session: OnlineSession,
    val pairingCode: String,
    me: SessionPresence,
    val context: Context,
    private val scope: CoroutineScope,
    /** L'appareil du créateur : seul son registre (`roster`) fait foi (doc 16, phase D). */
    val ownerDeviceID: String,
) {
    sealed interface SubmitResult {
        data class Accepted(
            val record: SessionEventRecord,
        ) : SubmitResult

        /** Un autre appareil a ajouté un événement entre-temps ; le journal local est à jour. Pas
         * de nouvel essai automatique : deux personnes qui saisissent la même manche physique
         * créeraient un doublon silencieux. */
        data class Overtaken(
            val byDeviceName: String?,
        ) : SubmitResult

        data object Offline : SubmitResult

        data object Closed : SubmitResult
    }

    var presence: List<SessionPresence> by mutableStateOf(emptyList())
        private set

    /** Faux hors ligne : la saisie est alors bloquée (doc 16). */
    var isReachable: Boolean by mutableStateOf(true)
        private set
    var isClosed: Boolean by mutableStateOf(false)
        private set

    /** Nouveaux événements lisibles, dans l'ordre — publiés à chaque rattrapage. */
    var onNewRecords: (suspend (List<SessionEventRecord>) -> Unit)? = null

    /** Doc 16, phase D — qui est qui, recalculé à chaque nouvel événement d'identité. */
    var identities: SessionIdentities by mutableStateOf(SessionIdentities(emptyList(), ownerDeviceID))
        private set

    /** Nouveaux événements d'identité, dans l'ordre (après [identities] mis à jour). */
    var onNewIdentities: (suspend (List<SessionIdentityRecord>) -> Unit)? = null
    private var knownIdentityCount = 0

    val sessionID: UUID get() = session.sessionID

    private val channel = SessionChannel(session.sessionID, me, scope)
    private val jobs = mutableListOf<Job>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    isReachable = true
                    refresh()
                }
            }

            override fun onLost(network: Network) {
                isReachable = false
            }
        }

    /** Rattrapage initial, puis écoute. Le canal est un confort : s'il ne se connecte pas, le
     * rattrapage au premier plan et après chaque saisie suffit à rester juste. */
    suspend fun start() {
        refresh()
        runCatching { channel.connect() }
        jobs += scope.launch { channel.notifications.collect { refresh() } }
        jobs += scope.launch { channel.presence.collect { presence = it } }
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    suspend fun stop() {
        for (job in jobs) job.cancel()
        jobs.clear()
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        channel.disconnect()
    }

    /** Rattrape le journal et publie ce qui est nouveau. */
    suspend fun refresh() {
        try {
            val fresh = session.sync()
            isReachable = true
            // Identités d'abord : qui occupe quelle place doit être connu quand la partie se recharge.
            publishIdentityChanges()
            if (fresh.isNotEmpty()) onNewRecords?.invoke(fresh)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            isReachable = false
        }
    }

    suspend fun submit(
        event: MatchEvent,
        matchID: UUID,
        eventID: UUID = UUID.randomUUID(),
        occurredAt: Instant = Instant.now(),
    ): SubmitResult {
        if (isClosed) return SubmitResult.Closed
        val before = session.lastSeq
        return try {
            val record = session.append(event, matchID, eventID, occurredAt)
            isReachable = true
            onNewRecords?.invoke(session.records().filter { it.seq > before })
            publishIdentityChanges()
            SubmitResult.Accepted(record)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (stale: OnlineSessionError.StaleSequence) {
            isReachable = true
            val fresh = session.records().filter { it.seq > before }
            if (fresh.isNotEmpty()) onNewRecords?.invoke(fresh)
            publishIdentityChanges()
            val author = fresh.lastOrNull { it.event.deviceID != session.deviceID }?.event?.deviceID
            SubmitResult.Overtaken(author?.let(::deviceName))
        } catch (closed: OnlineSessionError.SessionClosed) {
            isClosed = true
            SubmitResult.Closed
        } catch (error: Exception) {
            isReachable = false
            SubmitResult.Offline
        }
    }

    /** Doc 16, phase D — publie un événement d'identité dans la partie [matchID]. Contrairement à
     * une manche, nouvel essai automatique si un autre appareil a devancé : une revendication ne
     * dépend pas de l'état de la partie ([SessionIdentities] départage ensuite). */
    suspend fun submitIdentity(
        event: SessionIdentityEvent,
        matchID: UUID,
    ): Boolean {
        if (isClosed) return false
        repeat(3) {
            try {
                session.appendIdentity(event, matchID)
                isReachable = true
                publishIdentityChanges()
                return true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (stale: OnlineSessionError.StaleSequence) {
                publishIdentityChanges()
            } catch (closed: OnlineSessionError.SessionClosed) {
                isClosed = true
                return false
            } catch (error: Exception) {
                isReachable = false
                return false
            }
        }
        return false
    }

    private suspend fun publishIdentityChanges() {
        val all = session.identities()
        if (all.size == knownIdentityCount) return
        val fresh = all.drop(knownIdentityCount)
        knownIdentityCount = all.size
        identities = SessionIdentities(all, ownerDeviceID)
        onNewIdentities?.invoke(fresh)
    }

    /** Doc 16, phase F — cet appareil vient d'enregistrer un événement dans la session : c'est lui
     * qui met à jour l'écran verrouillé des iPhone de la session (Live Activity), même si le
     * créateur est éteint. Jamais pour un événement reçu d'un autre appareil. Miroir de
     * `MatchLiveActivityController.refresh(isAuthoritative: true)`. */
    fun announceToLockScreens(
        state: MatchState,
        definition: GameDefinition,
        rules: GameRules,
    ) {
        val names = state.participants.associate { it.id to it.displayName }
        val standings =
            rules
                .standings(state, definition)
                .sortedBy { it.rank }
                .take(4)
                .map { LiveActivityContent.Standing(it.participantID, names[it.participantID].orEmpty(), it.score) }
        val content =
            LiveActivityContent(
                matchID = state.matchID,
                gameName = definition.name.localized,
                gameSymbol = definition.symbol,
                roundNumber = state.rounds.size,
                standings = standings,
            )
        val ended = state.status == MatchStatus.Ended || state.status == MatchStatus.Abandoned
        scope.launch { LiveActivityPushClient.push(LiveActivityPushClient.sessionKey(sessionID), ended, content) }
    }

    fun deviceName(deviceID: String): String? = presence.firstOrNull { it.deviceID == deviceID }?.deviceName
}

/** Miroir de `PersistedOnlineSession` — ce qu'un appareil retient d'une session pour la reprendre
 * après un redémarrage, créateur compris. */
@Serializable
data class PersistedOnlineSession(
    @Serializable(with = com.quimene.domain.model.UUIDSerializer::class) val sessionID: UUID,
    val pairingCode: String,
    val role: Role,
    val deviceName: String,
    /** Doc 16, phase D — côté participant : mon profil tel que publié, et « Je regarde
     * seulement ». */
    val profile: ProfileCard? = null,
    val isSpectator: Boolean = false,
) {
    @Serializable
    enum class Role { Owner, Participant }

    fun save(context: Context) {
        prefs(context).edit { putString(key(role), Json.encodeToString(serializer(), this@PersistedOnlineSession)) }
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("online_session", Context.MODE_PRIVATE)

        private fun key(role: Role) = "onlineSession.${role.name}"

        fun load(
            context: Context,
            role: Role,
        ): PersistedOnlineSession? =
            prefs(context).getString(key(role), null)?.let {
                runCatching { Json.decodeFromString(serializer(), it) }.getOrNull()
            }

        fun clear(
            context: Context,
            role: Role,
        ) {
            prefs(context).edit { remove(key(role)) }
        }
    }
}

/** Doc 16 — le nom montré aux autres appareils d'une session (appareils connectés, « X vient de
 * valider une manche ») : le pseudo du profil, obligatoire depuis la phase A ; le nom de
 * l'appareil seulement en repli. Miroir de `SessionDisplayName` (Swift). */
suspend fun sessionDisplayName(
    context: Context,
    playerRepository: PlayerRepository,
): String =
    playerRepository
        .myOwnSharedPlayer()
        ?.nickname
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: DeviceIdentity.name(context)

/** Doc 16, phase D — la carte de mon profil, publiée dans une session (« Qui es-tu ? », registre
 * du créateur). `null` sans profil. Un avatar photo ne voyage pas : repli sur l'emoji dérivé du
 * pseudo. Miroir de `ProfileCard.mine` (Swift). */
suspend fun myProfileCard(playerRepository: PlayerRepository): ProfileCard? {
    val me = playerRepository.myOwnSharedPlayer() ?: return null
    return profileCard(me, playerRepository.sharedProfileID(me))
}

fun profileCard(
    player: PlayerEntity,
    id: UUID,
): ProfileCard {
    val emoji =
        if (player.avatarKind == "emoji" && player.avatarValue.isNotEmpty()) {
            player.avatarValue
        } else {
            (Avatar.generated(player.nickname).kind as? AvatarKind.Emoji)?.character.orEmpty()
        }
    return ProfileCard(id, player.nickname, "emoji", emoji, player.paletteID)
}

/** L'avatar à montrer pour cette carte. */
fun ProfileCard.avatar(): Avatar {
    val emoji =
        if (avatarKind == "emoji" && avatarValue.isNotEmpty()) {
            avatarValue
        } else {
            (Avatar.generated(name).kind as? AvatarKind.Emoji)?.character ?: "🙂"
        }
    return Avatar(AvatarKind.Emoji(emoji), PlayerPalette(paletteID.toIntOrNull()?.coerceIn(1, 10) ?: 1))
}

/** Doc 16, phase D — la liaison « Qui es-tu ? » vaut dans les deux sens : côté participant, le
 * créateur devient un ami lié (doc 14). Miroir de `FriendLinking` (Swift) : une fiche déjà liée
 * à ce profil, rien à faire ; sinon une fiche active non liée qui porte exactement ce pseudo (et
 * une seule) est liée ; à défaut, une fiche est créée. */
suspend fun ensureFriend(
    card: ProfileCard,
    playerRepository: PlayerRepository,
) {
    if (playerRepository.player(card.id) != null) return
    val name = card.name.trim()
    val sameName =
        playerRepository.allPlayers().filter {
            !it.isArchived && it.sharedProfileID == null && it.nickname.trim().equals(name, ignoreCase = true)
        }
    val fiche =
        sameName.singleOrNull()
            ?: playerRepository.create(
                nickname = name,
                avatarKind = "emoji",
                avatarValue = (card.avatar().kind as AvatarKind.Emoji).character,
                paletteID = card.paletteID,
            )
    playerRepository.linkSharedProfile(card.id, card.name, fiche)
}
