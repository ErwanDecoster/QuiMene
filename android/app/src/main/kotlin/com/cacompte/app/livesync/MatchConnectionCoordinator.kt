package com.cacompte.app.livesync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.sync.LiveSession
import com.cacompte.sync.Role
import com.cacompte.sync.SupabaseTransport
import com.cacompte.sync.WireMessage
import kotlinx.coroutines.CoroutineScope

/**
 * Coordinateur **joueur** (contributeur/observateur), durée de vie de l'application — miroir de
 * `MatchConnectionCoordinator.swift`. Point d'entrée unique pour [com.cacompte.app.features.join.JoinScreen].
 *
 * Simplification assumée par rapport à Apple : pas de reprise automatique au lancement froid ni
 * au retour au premier plan (`PersistedSession`, `willEnterForegroundNotification`) — perdre la
 * connexion pendant que le processus est tué exige de retaper le code. `reconnectNow` couvre le
 * cas courant (coupure réseau pendant que l'app reste ouverte) en rejouant le dernier code de
 * pairage mémorisé.
 */
class MatchConnectionCoordinator(
    private val catalog: GameCatalog,
    private val resolveDeviceID: suspend () -> String,
    private val scope: CoroutineScope,
) {
    var sharedMatch: SharedMatchViewModel? by mutableStateOf(null)
        private set

    private var lastPairingCode: String? = null
    private var lastDeviceName: String? = null
    private var lastAppVersion: String? = null

    /** [requestedRole] est toujours [Role.Contributor] côté appelant (miroir de `JoinTabView` —
     * l'utilisateur ne choisit jamais son rôle) ; l'hôte peut le rétrograder en [Role.Observer]
     * si « Autoriser les contributeurs » est désactivé — [SharedMatchViewModel.role] porte le
     * rôle réellement assigné, lu via `LiveSession.currentRole()` après la poignée de main. */
    suspend fun join(
        code: String,
        deviceName: String,
        appVersion: String,
    ): Role {
        stop()
        val deviceID = resolveDeviceID()
        val transport =
            SupabaseTransport(
                deviceID = deviceID,
                deviceName = deviceName,
                scope = scope,
                platform = WireMessage.Platform.Android,
            )
        val host = transport.resolveGame(code)
        val transportSession = transport.connect(host)
        val session = LiveSession(deviceID = deviceID, catalog = catalog, scope = scope)
        session.attachToHost(
            session = transportSession,
            sessionID = host.id,
            pairingCode = code,
            requestedRole = Role.Contributor,
            deviceName = deviceName,
            appVersion = appVersion,
        )
        val assignedRole = session.currentRole()

        lastPairingCode = code
        lastDeviceName = deviceName
        lastAppVersion = appVersion
        sharedMatch = SharedMatchViewModel(session, assignedRole, catalog, scope) { sharedMatch = null }
        return assignedRole
    }

    /** Reconnexion manuelle après une perte de connexion à l'hôte (bandeau « Connexion perdue »
     * dans [com.cacompte.app.features.join.JoinScreen]) — rejoue le dernier code avec les mêmes
     * identifiants d'appareil. `false` si aucune tentative précédente n'existe. */
    suspend fun reconnectNow(): Boolean {
        val code = lastPairingCode ?: return false
        val deviceName = lastDeviceName ?: return false
        val appVersion = lastAppVersion ?: return false
        join(code, deviceName, appVersion)
        return true
    }

    /** « Quitter » explicite — contrairement à une perte de connexion, oublie le code mémorisé :
     * un « Réessayer » après un `stop()` volontaire n'a pas de sens. */
    suspend fun stop() {
        sharedMatch?.stop()
        sharedMatch = null
        lastPairingCode = null
        lastDeviceName = null
        lastAppVersion = null
    }
}
