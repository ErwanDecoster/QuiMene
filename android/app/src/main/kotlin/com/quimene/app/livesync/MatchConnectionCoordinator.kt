package com.quimene.app.livesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quimene.domain.rules.GameCatalog
import com.quimene.sync.OnlineSession
import com.quimene.sync.OnlineSessionError
import com.quimene.sync.Role
import com.quimene.sync.SessionPresence
import com.quimene.sync.SupabaseSessionBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Doc 16, phase C — miroir de `MatchConnectionCoordinator.swift` : côté participant d'une session
 * en ligne. Rejoindre, c'est résoudre le code, rattraper le journal serveur, puis écouter le canal :
 * plus de poignée de main avec un hôte, qui n'a plus besoin d'être allumé. Retient la session
 * ([PersistedOnlineSession]) : après un arrêt complet du processus, la partie suivie reprend sans
 * redemander le code.
 */
class MatchConnectionCoordinator(
    private val catalog: GameCatalog,
    private val context: Context,
    private val resolveDeviceID: suspend () -> String,
    private val scope: CoroutineScope,
) {
    private val backend = SupabaseSessionBackend()

    var sharedMatch: SharedMatchViewModel? by mutableStateOf(null)
        private set

    init {
        PersistedOnlineSession.load(context, PersistedOnlineSession.Role.Participant)?.let { persisted ->
            scope.launch { runCatching { connect(persisted.pairingCode, persisted.deviceName) } }
        }
    }

    /** Point d'entrée unique de [com.quimene.app.features.join.JoinScreen]. Renvoie le rôle
     * accordé : contributeur, ou observateur si le créateur n'autorise pas les contributeurs. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun join(
        code: String,
        deviceName: String,
        appVersion: String,
    ): Role = connect(code, deviceName)

    /** « Réessayer » : un rattrapage immédiat. */
    suspend fun reconnectNow(): Boolean {
        val link = sharedMatch?.link ?: return false
        link.refresh()
        return link.isReachable
    }

    /** Retour au premier plan ([com.quimene.app.QuiMeneApplication]) : rattraper. */
    suspend fun onForeground() {
        sharedMatch?.link?.refresh()
    }

    private suspend fun connect(
        code: String,
        deviceName: String,
    ): Role {
        val info = backend.resolve(code) ?: throw OnlineSessionError.SessionNotFound
        sharedMatch?.link?.stop()

        val deviceID = resolveDeviceID()
        val session = OnlineSession(info.sessionID, code, deviceID, backend)
        val link = SessionLink(session, code, SessionPresence(deviceID, deviceName, isOwner = false), context, scope)
        val role = if (info.allowsContributors) Role.Contributor else Role.Observer
        sharedMatch =
            SharedMatchViewModel(link, role, catalog, scope) {
                sharedMatch = null
                PersistedOnlineSession.clear(context, PersistedOnlineSession.Role.Participant)
            }
        PersistedOnlineSession(info.sessionID, code, PersistedOnlineSession.Role.Participant, deviceName).save(context)
        link.start()
        return role
    }

    /** « Quitter la partie » : départ volontaire. */
    fun stop() {
        sharedMatch?.stop()
        sharedMatch = null
        PersistedOnlineSession.clear(context, PersistedOnlineSession.Role.Participant)
    }
}
