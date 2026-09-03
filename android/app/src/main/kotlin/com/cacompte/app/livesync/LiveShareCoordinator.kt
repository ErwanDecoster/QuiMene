package com.cacompte.app.livesync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cacompte.domain.engine.MatchEvent
import com.cacompte.domain.engine.StampedEvent
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import com.cacompte.sync.LiveSession
import com.cacompte.sync.SupabaseTransport
import com.cacompte.sync.WireMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Doc 09 — notifie [com.cacompte.app.features.livematch.LiveMatchViewModel] qu'une manche
 * distante (contributeur) vient d'être acceptée et persistée pour `matchID`, afin qu'il recharge
 * son état et affiche éventuellement un bandeau d'activité. Miroir des propriétés
 * `remoteEventMatchID`/`remoteEventDeviceID`/`remoteEventIsRoundCommit` de
 * `LiveShareCoordinator.swift`, réunies dans un seul événement plutôt qu'un jeton `.onChange` —
 * idiome SwiftUI sans équivalent direct en Compose. */
data class RemoteMatchUpdate(
    val matchID: UUID,
    val deviceName: String?,
    val isRoundCommit: Boolean,
)

/**
 * Coordinateur **hôte**, durée de vie de l'application (une seule instance dans
 * [com.cacompte.app.di.AppContainer]) — miroir de `LiveShareCoordinator.swift`. Possède le
 * `LiveSession`/`SupabaseTransport` de la session partagée courante indépendamment de l'écran
 * `LiveMatchScreen` affiché : la partie reste partagée même si l'utilisateur navigue ailleurs.
 *
 * Simplification assumée par rapport à Apple : pas d'enchaînement d'une session sur plusieurs
 * parties successives (`attach`/`switchMatch`) — démarrer un nouveau partage ferme d'abord
 * l'ancien. Suffisant pour partager une partie à la fois ; l'enchaînement pourra être ajouté
 * plus tard si le besoin se confirme.
 */
class LiveShareCoordinator(
    private val catalog: GameCatalog,
    private val matchRepository: MatchRepository,
    private val resolveDeviceID: suspend () -> String,
    private val scope: CoroutineScope,
) {
    private var session: LiveSession? = null
    private var transport: SupabaseTransport? = null
    private var attachedMatch: MatchEntity? = null
    private var backgroundJobs = mutableListOf<Job>()

    var pairingCode: String? by mutableStateOf(null)
        private set
    var connectedPeers: List<LiveSession.ConnectedPeer> by mutableStateOf(emptyList())
        private set
    var attachedMatchID: UUID? by mutableStateOf(null)
        private set
    var allowsContributors: Boolean by mutableStateOf(true)
        private set

    private val remoteMatchUpdatesFlow = MutableSharedFlow<RemoteMatchUpdate>(extraBufferCapacity = 8)
    val remoteMatchUpdates: SharedFlow<RemoteMatchUpdate> = remoteMatchUpdatesFlow

    /** Démarre le partage de [match] — ou ne fait rien si elle est déjà la partie partagée. */
    suspend fun startSharing(
        match: MatchEntity,
        participantCount: Int,
        deviceName: String,
        allowsContributors: Boolean,
    ) {
        if (attachedMatchID == match.id) return
        if (attachedMatchID != null) stopSharing()

        val deviceID = resolveDeviceID()
        val newSession = LiveSession(deviceID = deviceID, catalog = catalog, scope = scope)
        val newTransport =
            SupabaseTransport(
                deviceID = deviceID,
                deviceName = deviceName,
                scope = scope,
                platform = WireMessage.Platform.Android,
            )

        val sessionID = UUID.randomUUID()
        val code = LiveSession.generatePairingCode()
        newSession.startHosting(
            initialLog = matchRepository.currentLog(match),
            sessionID = sessionID,
            pairingCode = code,
            allowsContributors = allowsContributors,
        )
        newTransport.advertise(
            sessionID = sessionID,
            matchID = match.id,
            gameID = match.gameID,
            participantCount = participantCount,
            pairingCode = code,
        )

        session = newSession
        transport = newTransport
        attachedMatch = match
        attachedMatchID = match.id
        pairingCode = code
        this.allowsContributors = allowsContributors
        connectedPeers = emptyList()

        backgroundJobs +=
            scope.launch {
                newTransport.acceptIncoming().collect { transportSession ->
                    newSession.acceptConnection(transportSession)
                }
            }
        backgroundJobs += scope.launch { newSession.events.collect(::handleRemoteEvent) }
        backgroundJobs += scope.launch { newSession.peerUpdates.collect { peers -> connectedPeers = peers } }
    }

    /** Doc « Fin de partie » — republie le journal complet vers les pairs déjà connectés après
     * chaque écriture hôte locale (`LiveSession.syncHostLog` ne diffuse que les nouveaux
     * événements). No-op si [matchID] n'est pas la partie actuellement partagée. */
    suspend fun syncLog(matchID: UUID) {
        val match = attachedMatch ?: return
        if (match.id != matchID) return
        val current = requireNotNull(matchRepository.match(matchID))
        attachedMatch = current
        session?.syncHostLog(matchRepository.currentLog(current))
    }

    suspend fun setAllowsContributors(allowed: Boolean) {
        session?.setAllowsContributors(allowed) ?: return
        allowsContributors = allowed
    }

    suspend fun stopSharing() {
        for (job in backgroundJobs) job.cancel()
        backgroundJobs.clear()
        session?.stopHosting()
        transport?.stopAdvertising()
        session = null
        transport = null
        attachedMatch = null
        attachedMatchID = null
        pairingCode = null
        connectedPeers = emptyList()
    }

    private suspend fun handleRemoteEvent(stamped: StampedEvent) {
        val match = attachedMatch ?: return
        try {
            matchRepository.appendRemoteEvent(stamped, match, catalog)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return
        }
        attachedMatch = requireNotNull(matchRepository.match(match.id))
        val deviceName = connectedPeers.firstOrNull { it.deviceID == stamped.deviceID }?.deviceName
        remoteMatchUpdatesFlow.emit(
            RemoteMatchUpdate(
                matchID = match.id,
                deviceName = deviceName,
                isRoundCommit = stamped.event is MatchEvent.RoundCommitted,
            ),
        )
    }
}
