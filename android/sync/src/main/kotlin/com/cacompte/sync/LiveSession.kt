package com.cacompte.sync

import com.cacompte.domain.engine.MatchEngine
import com.cacompte.domain.engine.MatchEvent
import com.cacompte.domain.engine.StampedEvent
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Doc 09 « Modèle : hôte autoritaire ». Un seul type, deux rôles possibles :
 * - **Hôte** : garde son propre [MatchState] pour arbitrer ([GameRules.validate] puis
 *   [MatchEngine.reduce]) les propositions des contributeurs, et rediffuse ce qui est accepté.
 * - **Pair (contributeur/observateur)** : relais fin vers l'hôte, sans copie locale de
 *   `MatchState` — c'est la couche app qui rejoue [events] avec son propre [MatchEngine],
 *   exactement comme au lancement de l'app.
 *
 * Miroir de `LiveSession.swift`, un `actor` — sans équivalent direct en Kotlin. Un seul [Mutex]
 * protège tout l'état mutable, comme le ferait l'isolation d'acteur Swift : chaque méthode
 * publique l'acquiert **une seule fois** à son point d'entrée ; les méthodes privées suffixées
 * `Locked` supposent le verrou déjà tenu par l'appelant et ne l'acquièrent jamais elles-mêmes —
 * `Mutex` n'est pas ré-entrant, un second `withLock` imbriqué bloquerait indéfiniment.
 */
class LiveSession(
    private val deviceID: String,
    private val catalog: GameCatalog,
    private val scope: CoroutineScope,
    private val engine: MatchEngine = MatchEngine(),
) {
    sealed class SessionError(
        message: String,
    ) : Exception(message) {
        data object NotConnected : SessionError("Non connecté.")

        data object NotAuthorized : SessionError("Non autorisé.")

        data object NoActiveMatch : SessionError("Aucune partie active.")

        /** L'hôte n'a jamais répondu au `hello` dans le délai imparti — code d'appairage erroné
         * (l'hôte ne peut alors pas déchiffrer le message, et ne répond jamais) ou hôte
         * injoignable. */
        data object NoResponseFromHost : SessionError("L'hôte n'a pas répondu à temps.")
    }

    data class RemoteValidationFailure(
        val reason: String,
    ) : Exception(reason)

    private val mutex = Mutex()

    private var clock = LamportClock()
    private var role: Role = Role.Observer
    private var sessionID: UUID? = null
    private var pairingKey: ByteArray? = null

    // Hôte uniquement : état de vérité pour arbitrer les propositions.
    private var allowsContributors = true
    private var hostState: MatchState? = null
    private var hostRules: GameRules? = null
    private var hostDefinition: GameDefinition? = null
    private var hostLog: List<StampedEvent> = emptyList()
    private val peerConnections = mutableMapOf<UUID, PeerConnection>()
    private val peerListenJobs = mutableMapOf<UUID, Job>()

    // Pair non-hôte uniquement : connexion vers l'hôte.
    private var hostConnection: TransportSession? = null
    private var hostListenJob: Job? = null

    /** Posé juste avant d'envoyer le `hello` dans [attachToHost], résolu par [handleFromHost] à
     * la réception du `welcome`, ou explicitement par [failPendingWelcome]. Volontairement en
     * dehors de [mutex] (`@Volatile` suffit, un seul écrivain à la fois par construction) :
     * l'imbriquer dans le verrou depuis l'intérieur de `suspendCancellableCoroutine` obligerait à
     * relancer une coroutine séparée rien que pour l'assignation, réintroduisant exactement la
     * course que ce champ existe pour éliminer. */
    @Volatile
    private var pendingWelcome: CancellableContinuation<Unit>? = null

    private class PeerConnection(
        val session: TransportSession,
        var deviceName: String,
        var role: Role,
        var deviceID: String?,
    )

    // `Channel` (pas `SharedFlow`) pour les 5 flux ci-dessous : miroir fidèle d'`AsyncStream`
    // côté Swift — un seul consommateur (la couche app, un unique `for await`/`collect`), qui
    // doit recevoir même les valeurs émises avant qu'il ne commence à collecter (`AsyncStream`
    // les met en attente indéfiniment par défaut). Un `SharedFlow` sans lecture (`replay = 0`)
    // perdrait silencieusement tout ce qui a été émis avant l'abonnement du premier collecteur —
    // sémantique différente, pas un détail.
    private val eventChannel = Channel<StampedEvent>(Channel.UNLIMITED)
    val events: Flow<StampedEvent> = eventChannel.receiveAsFlow()

    private val rejectionChannel = Channel<Pair<UUID, String>>(Channel.UNLIMITED)
    val rejections: Flow<Pair<UUID, String>> = rejectionChannel.receiveAsFlow()

    /** Doc 09 — un instantané des pairs connectés, republié à chaque connexion/déconnexion/
     * confirmation de rôle, pour que l'écran d'invitation affiche « Théo est connecté ». */
    data class ConnectedPeer(
        val id: UUID,
        val deviceName: String,
        val role: Role,
        /** Le même identifiant que celui qui horodate les [StampedEvent] de ce pair
         * (`StampedEvent.deviceID`) — permet à l'appelant de relier « cette manche vient de X »
         * à « X, c'est Théo ». `null` tant que le `hello` n'est pas encore arrivé. */
        val deviceID: String?,
    )

    private val peerUpdateChannel = Channel<List<ConnectedPeer>>(Channel.UNLIMITED)
    val peerUpdates: Flow<List<ConnectedPeer>> = peerUpdateChannel.receiveAsFlow()

    private val hostLeftChannel = Channel<Unit>(Channel.UNLIMITED)
    val hostLeft: Flow<Unit> = hostLeftChannel.receiveAsFlow()

    private val matchChangedChannel = Channel<List<StampedEvent>>(Channel.UNLIMITED)
    val matchChanged: Flow<List<StampedEvent>> = matchChangedChannel.receiveAsFlow()

    companion object {
        /** Doc 09 « Appairage et chiffrement » — affiché en clair par l'hôte à qui rejoint. */
        fun generatePairingCode(): String = SessionCrypto.generatePairingCode()
    }

    suspend fun currentRole(): Role = mutex.withLock { role }

    /** Doc 09 « Fin de partie » — identifiant de la session en cours, hôte ou pair (`null` avant
     * [startHosting]/[attachToHost]). */
    suspend fun currentSessionID(): UUID? = mutex.withLock { sessionID }

    // region Hôte

    /** Rejoue `log` et met à jour l'état d'arbitrage, partagé par [startHosting], [syncHostLog]
     * et [switchMatch] — les trois façons dont l'hôte peut se retrouver à arbitrer un nouveau
     * journal. Suppose le verrou déjà tenu par l'appelant. */
    private fun applyHostLogLocked(log: List<StampedEvent>): MatchState {
        val replayed = engine.replay(log, catalog)
        hostState = replayed
        hostDefinition = catalog.definition(replayed.gameID, replayed.rulesVersion)
        hostRules = catalog.rules(replayed.gameID, replayed.rulesVersion)
        hostLog = log
        return replayed
    }

    /** `initialLog` est rejoué immédiatement : l'hôte doit connaître l'état courant pour arbitrer
     * dès la première proposition. `sessionID` identifie la **session de partage**, distincte de
     * la partie rejouée ici — stable même quand l'hôte enchaîne une autre partie ensuite (voir
     * [switchMatch]), pour que [pairingKey] ne change jamais tant que la session dure. */
    suspend fun startHosting(
        initialLog: List<StampedEvent>,
        sessionID: UUID,
        pairingCode: String,
        allowsContributors: Boolean = true,
    ) {
        mutex.withLock {
            applyHostLogLocked(initialLog)
            role = Role.Host
            this.sessionID = sessionID
            clock = LamportClock(startingAt = initialLog.maxOfOrNull { it.lamport } ?: 0uL)
            pairingKey = SessionCrypto.deriveKey(pairingCode, sessionID)
            this.allowsContributors = allowsContributors
        }
    }

    /** Le journal faisant foi vient de `MatchRepository` (hôte), pas de [LiveSession] — cette
     * méthode resynchronise l'état interne d'arbitrage après chaque écriture locale de l'hôte
     * (saisie, annulation, fin de partie) et diffuse aux pairs connectés les seuls événements
     * apparus depuis le dernier appel. Le journal n'est jamais raccourci (event sourcing — une
     * annulation ajoute un événement, elle ne retire rien) : la longueur suffit à identifier ce
     * qui est nouveau. */
    suspend fun syncHostLog(log: List<StampedEvent>) {
        mutex.withLock {
            val newEvents = if (log.size > hostLog.size) log.subList(hostLog.size, log.size) else emptyList()
            applyHostLogLocked(log)
            log.maxOfOrNull { it.lamport }?.let { clock = LamportClock(startingAt = it) }
            if (newEvents.isNotEmpty()) {
                broadcastToConnectedPeersLocked(WireMessage.Kind.Events(newEvents))
            }
        }
    }

    /** Doc 09 « Fin de partie » — l'hôte enchaîne une nouvelle partie (même jeu rejoué ou jeu
     * différent) sans rompre la session : garde le canal, [pairingKey]/[sessionID] et les pairs
     * déjà connectés, ne remplace que l'état arbitré. Diffusé à tous les pairs déjà connectés
     * ([WireMessage.Kind.MatchChanged]) — contrairement à `welcome`, qui ne sert qu'au pair qui
     * vient de rejoindre — pour qu'ils repartent d'un journal vide plutôt que d'y ajouter ces
     * événements. */
    suspend fun switchMatch(initialLog: List<StampedEvent>) {
        mutex.withLock {
            applyHostLogLocked(initialLog)
            clock = LamportClock(startingAt = initialLog.maxOfOrNull { it.lamport } ?: 0uL)
            broadcastToConnectedPeersLocked(WireMessage.Kind.MatchChanged(initialLog))
        }
    }

    /** Doc utilisateur — modifiable en cours de partage : s'applique aux prochaines connexions
     * (`hello`), ne rétrograde pas un contributeur déjà connecté quand on la désactive. */
    suspend fun setAllowsContributors(allowed: Boolean) {
        mutex.withLock { allowsContributors = allowed }
    }

    /** Arrête le partage côté hôte : prévient chaque pair connecté (`goodbye`) puis ferme
     * réellement chaque connexion. Sans ce dernier point, rien ne clôt jamais le transport sous-
     * jacent : un pair qui a quitté resterait indéfiniment listé comme connecté. */
    suspend fun stopHosting() {
        mutex.withLock {
            val key = pairingKey
            val session = sessionID
            for (connection in peerConnections.values) {
                if (key != null && session != null) {
                    trySend {
                        sendLocked(
                            WireMessage(sessionID = session, kind = WireMessage.Kind.Goodbye),
                            connection.session,
                            key,
                        )
                    }
                }
                connection.session.close()
            }
            peerConnections.clear()
            for (job in peerListenJobs.values) job.cancel()
            peerListenJobs.clear()
            publishPeerUpdateLocked()
        }
    }

    /** Une connexion entrante par pair qui rejoint — venue de la découverte du transport actif. */
    suspend fun acceptConnection(session: TransportSession) {
        mutex.withLock {
            val peerID = UUID.randomUUID()
            peerConnections[peerID] =
                PeerConnection(session = session, deviceName = "…", role = Role.Observer, deviceID = null)
            peerListenJobs[peerID] =
                scope.launch {
                    session.incoming.collect { data -> handleFromPeer(data, peerID) }
                    mutex.withLock {
                        peerConnections.remove(peerID)
                        publishPeerUpdateLocked()
                    }
                }
        }
    }

    private suspend fun handleFromPeer(
        data: ByteArray,
        peerID: UUID,
    ) {
        mutex.withLock {
            val key = pairingKey ?: return@withLock
            val message = decodeOrNull(data, key) ?: return@withLock
            val connection = peerConnections[peerID] ?: return@withLock

            when (val kind = message.kind) {
                is WireMessage.Kind.Hello -> {
                    val assignedRole =
                        if (kind.role == Role.Contributor &&
                            !allowsContributors
                        ) {
                            Role.Observer
                        } else {
                            kind.role
                        }
                    connection.deviceName = kind.deviceName
                    connection.role = assignedRole
                    connection.deviceID = kind.deviceID
                    publishPeerUpdateLocked()
                    sendWelcomeLocked(connection.session, assignedRole)
                }

                is WireMessage.Kind.Proposal -> {
                    for (proposed in kind.events) {
                        arbitrateLocked(proposed, peerID, connection.session)
                    }
                }

                is WireMessage.Kind.Heartbeat -> Unit

                WireMessage.Kind.Goodbye -> {
                    peerConnections.remove(peerID)
                    publishPeerUpdateLocked()
                }

                // Jamais envoyés par un pair vers l'hôte.
                is WireMessage.Kind.Welcome, is WireMessage.Kind.Events, is WireMessage.Kind.MatchChanged,
                is WireMessage.Kind.Rejection,
                -> Unit
            }
        }
    }

    private fun publishPeerUpdateLocked() {
        val snapshot =
            peerConnections.map { (id, connection) ->
                ConnectedPeer(
                    id = id,
                    deviceName = connection.deviceName,
                    role = connection.role,
                    deviceID = connection.deviceID,
                )
            }
        peerUpdateChannel.trySend(snapshot)
    }

    private suspend fun sendWelcomeLocked(
        session: TransportSession,
        role: Role,
    ) {
        val key = pairingKey ?: return
        val currentSessionID = sessionID ?: return
        trySend {
            sendLocked(
                WireMessage(sessionID = currentSessionID, kind = WireMessage.Kind.Welcome(hostLog, role)),
                session,
                key,
            )
        }
    }

    private suspend fun arbitrateLocked(
        proposed: StampedEvent,
        peerID: UUID,
        session: TransportSession,
    ) {
        val connection = peerConnections[peerID]
        if (connection?.role != Role.Contributor) {
            rejectLocked(proposed.id, "Rôle non autorisé à proposer une manche.", session)
            return
        }
        try {
            // L'id de la proposition est préservé (seul l'horloge change) : le contributeur peut
            // ainsi reconnaître la confirmation de son événement optimiste plutôt que d'y voir un
            // second événement indépendant.
            val stamped = hostCommitLocked(proposed.event, proposed.id)
            broadcastToConnectedPeersLocked(WireMessage.Kind.Events(listOf(stamped)))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: RemoteValidationFailure) {
            rejectLocked(proposed.id, failure.reason, session)
        } catch (error: Exception) {
            rejectLocked(proposed.id, "Aucune partie active.", session)
        }
    }

    /** Valide, réduit, journalise et publie localement — partagé entre les propositions saisies
     * par l'hôte lui-même ([propose]) et celles acceptées d'un contributeur ([arbitrateLocked]).
     * Dans les deux cas l'événement final porte l'horloge de l'hôte, jamais celle proposée par le
     * pair. */
    private fun hostCommitLocked(
        event: MatchEvent,
        id: UUID = UUID.randomUUID(),
    ): StampedEvent {
        val rules = hostRules ?: throw SessionError.NoActiveMatch
        val definition = hostDefinition ?: throw SessionError.NoActiveMatch
        val currentState = hostState ?: throw SessionError.NoActiveMatch

        if (event is MatchEvent.RoundCommitted) {
            val validation = rules.validate(event.draft, currentState, definition)
            if (validation is ValidationResult.Invalid) {
                throw RemoteValidationFailure(validation.errors.firstOrNull()?.message ?: "Manche invalide.")
            }
        }
        val stamped = stampLocked(event, id)
        hostState = engine.reduce(currentState, stamped.event, rules, definition)
        hostLog = hostLog + stamped
        eventChannel.trySend(stamped)
        return stamped
    }

    private suspend fun rejectLocked(
        eventID: UUID,
        reason: String,
        session: TransportSession,
    ) {
        val key = pairingKey ?: return
        val currentSessionID = sessionID ?: return
        trySend {
            sendLocked(
                WireMessage(sessionID = currentSessionID, kind = WireMessage.Kind.Rejection(eventID, reason)),
                session,
                key,
            )
        }
    }

    private suspend fun broadcastToConnectedPeersLocked(kind: WireMessage.Kind) {
        val key = pairingKey ?: return
        val currentSessionID = sessionID ?: return
        val message = WireMessage(sessionID = currentSessionID, kind = kind)
        for (connection in peerConnections.values) {
            trySend { sendLocked(message, connection.session, key) }
        }
    }

    // endregion

    // region Pair (contributeur / observateur)

    /** Doc 09 — se connecte et **attend la confirmation de l'hôte** (`welcome`) avant de
     * retourner. Sans ce délai explicite, un code d'appairage erroné ne produit aucune erreur :
     * le `hello` chiffré avec la mauvaise clé arrive bien à l'hôte, qui ne peut simplement pas le
     * déchiffrer et ne répond donc jamais — l'appelant resterait sur « Connexion à la partie… »
     * indéfiniment sans ce délai.
     *
     * [pendingWelcome] est posé **avant** tout point de suspension (envoi du `hello`, écoute
     * démarrée) — même précaution que côté Swift (`withCheckedThrowingContinuation` avant les
     * `Task` concurrentes) : sans cet ordre, un `welcome` pourrait arriver et être traité avant
     * que la continuation n'existe pour le recevoir.
     */
    suspend fun attachToHost(
        session: TransportSession,
        sessionID: UUID,
        pairingCode: String,
        requestedRole: Role,
        deviceName: String,
        appVersion: String,
        timeoutMillis: Long = 8_000,
    ) {
        val key = SessionCrypto.deriveKey(pairingCode, sessionID)
        mutex.withLock {
            role = requestedRole
            this.sessionID = sessionID
            hostConnection = session
            pairingKey = key
        }

        val helloMessage =
            WireMessage(
                sessionID = sessionID,
                kind =
                    WireMessage.Kind.Hello(
                        deviceName = deviceName,
                        appVersion = appVersion,
                        platform = WireMessage.Platform.Android,
                        role = requestedRole,
                        deviceID = deviceID,
                    ),
            )

        val received =
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    pendingWelcome = continuation
                    hostListenJob =
                        scope.launch {
                            session.incoming.collect { data -> handleFromHost(data) }
                            mutex.withLock { hostConnection = null }
                            hostLeftChannel.trySend(Unit)
                        }
                    scope.launch {
                        try {
                            session.send(WireCodec.encode(helloMessage, key))
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            failPendingWelcome(error)
                        }
                    }
                }
            }
        if (received == null) {
            failPendingWelcome(SessionError.NoResponseFromHost)
            throw SessionError.NoResponseFromHost
        }
    }

    private suspend fun failPendingWelcome(error: Exception) {
        val continuation = pendingWelcome ?: return
        pendingWelcome = null
        mutex.withLock {
            hostConnection?.close()
            hostConnection = null
        }
        continuation.resumeWith(Result.failure(error))
    }

    /** Départ volontaire d'un pair non-hôte — prévient l'hôte (`goodbye`) puis ferme la
     * connexion. */
    suspend fun leave() {
        mutex.withLock {
            if (role == Role.Host) return@withLock
            val connection = hostConnection ?: return@withLock
            val key = pairingKey
            val currentSessionID = sessionID
            if (key != null && currentSessionID != null) {
                trySend {
                    sendLocked(
                        WireMessage(sessionID = currentSessionID, kind = WireMessage.Kind.Goodbye),
                        connection,
                        key,
                    )
                }
            }
            connection.close()
            hostConnection = null
        }
        hostListenJob?.cancel()
    }

    private suspend fun handleFromHost(data: ByteArray) {
        mutex.withLock {
            val key = pairingKey ?: return@withLock
            val message = decodeOrNull(data, key) ?: return@withLock

            when (val kind = message.kind) {
                is WireMessage.Kind.Welcome -> {
                    role = kind.role
                    kind.log.maxOfOrNull { it.lamport }?.let { clock = LamportClock(startingAt = it) }
                    for (stamped in kind.log) eventChannel.trySend(stamped)
                    pendingWelcome?.let {
                        pendingWelcome = null
                        it.resume(Unit)
                    }
                }

                is WireMessage.Kind.Events -> {
                    for (stamped in kind.events) {
                        clock.observe(stamped.lamport)
                        eventChannel.trySend(stamped)
                    }
                }

                is WireMessage.Kind.MatchChanged -> {
                    kind.log.maxOfOrNull { it.lamport }?.let { clock = LamportClock(startingAt = it) }
                    matchChangedChannel.trySend(kind.log)
                }

                is WireMessage.Kind.Rejection -> rejectionChannel.trySend(kind.eventID to kind.reason)

                WireMessage.Kind.Goodbye -> hostLeftChannel.trySend(Unit)

                // Jamais envoyés par l'hôte vers un pair.
                is WireMessage.Kind.Heartbeat, is WireMessage.Kind.Hello, is WireMessage.Kind.Proposal -> Unit
            }
        }
    }

    // endregion

    // region Commun

    /** Point d'entrée unique pour la couche app, quel que soit le rôle local : elle n'a pas à
     * savoir si cet appareil est l'hôte ou un contributeur. */
    suspend fun propose(event: MatchEvent) {
        mutex.withLock {
            when (role) {
                Role.Host -> {
                    val stamped = hostCommitLocked(event)
                    broadcastToConnectedPeersLocked(WireMessage.Kind.Events(listOf(stamped)))
                }

                Role.Contributor -> {
                    val connection = hostConnection ?: throw SessionError.NotConnected
                    val key = pairingKey ?: throw SessionError.NotConnected
                    val currentSessionID = sessionID ?: throw SessionError.NotConnected
                    val optimistic = stampLocked(event)
                    eventChannel.trySend(optimistic) // application optimiste locale.
                    sendLocked(
                        WireMessage(sessionID = currentSessionID, kind = WireMessage.Kind.Proposal(listOf(optimistic))),
                        connection,
                        key,
                    )
                }

                Role.Observer -> throw SessionError.NotAuthorized
            }
        }
    }

    private fun stampLocked(
        event: MatchEvent,
        id: UUID = UUID.randomUUID(),
    ): StampedEvent =
        StampedEvent(id = id, lamport = clock.tick(), deviceID = deviceID, occurredAt = Instant.now(), event = event)

    private suspend fun sendLocked(
        message: WireMessage,
        session: TransportSession,
        key: ByteArray,
    ) {
        session.send(WireCodec.encode(message, key))
    }

    private fun decodeOrNull(
        data: ByteArray,
        key: ByteArray,
    ): WireMessage? =
        try {
            WireCodec.decode(data, key)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }

    /** Équivalent de `try? await …` côté Swift — envoie au mieux, sans jamais avaler
     * `CancellationException` (contrairement à `runCatching`, un piège connu des coroutines
     * Kotlin : elle capture aussi l'annulation structurée par défaut). */
    private suspend fun trySend(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Un envoi qui échoue (pair déconnecté, socket fermé) n'est jamais fatal ici — la
            // détection de présence du transport actif signalera la déconnexion séparément.
        }
    }

    // endregion
}
