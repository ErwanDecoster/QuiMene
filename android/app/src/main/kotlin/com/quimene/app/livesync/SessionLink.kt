package com.quimene.app.livesync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.quimene.domain.engine.MatchEvent
import com.quimene.sync.OnlineSession
import com.quimene.sync.OnlineSessionError
import com.quimene.sync.SessionChannel
import com.quimene.sync.SessionEventRecord
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
            SubmitResult.Accepted(record)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (stale: OnlineSessionError.StaleSequence) {
            isReachable = true
            val fresh = session.records().filter { it.seq > before }
            if (fresh.isNotEmpty()) onNewRecords?.invoke(fresh)
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
