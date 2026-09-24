package com.quimene.sync

import com.quimene.domain.model.UUIDSerializer
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PresenceAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.UUID

/**
 * Doc utilisateur P9 — remplace les transports Wi-Fi/BLE maison (historique Git : cinq correctifs
 * BLE distincts, tous réels et vérifiés, sans que la connexion ne s'établisse jamais entre deux
 * appareils physiques). Miroir de `SupabaseTransport.swift` — la fiabilité de connexion/
 * reconnexion est déléguée à un SDK websocket mature (Supabase Realtime) plutôt qu'à du code
 * réseau/Bluetooth maison.
 *
 * **Un canal Realtime par session de partage** (`session:<sessionID>`, indépendant de la partie
 * courante) où l'hôte et chaque pair rejoignent le même canal :
 * - **Presence** identifie qui est là — l'hôte s'annonce toujours sous la clé constante
 *   [HOST_PRESENCE_KEY] (pas besoin qu'un pair la connaisse à l'avance), chaque pair sous son
 *   propre `deviceID`. Une déconnexion (y compris abrupte) déclenche un événement de présence
 *   côté serveur.
 * - **Broadcast** transporte chaque message avec un en-tête `from`/`to` en plus du contenu déjà
 *   chiffré ([WireCodec], inchangé) — [SupabaseTransportSession] filtre simplement ce flux
 *   partagé par ces champs pour se comporter comme une session point-à-point ordinaire du point
 *   de vue de [LiveSession], qui ne voit aucune différence.
 * - Pas de fragmentation nécessaire : un message tient dans un seul frame JSON.
 *
 * **Découverte** : `quimene_open_games` (Postgres, mêmes migrations `supabase/migrations` que
 * l'app Apple) résout un code d'appairage tapé à la main vers le `sessionID` correspondant.
 *
 * Contrairement à [LiveSession]/[SessionCrypto]/[WireCodec], cette classe ne peut être vérifiée
 * que manuellement, en recette croisée sur appareils réels (« Fini quand » de l'étape F, doc 11)
 * — aucun golden file ni transport en mémoire ne peut se substituer à un vrai aller-retour réseau
 * Supabase.
 */
class SupabaseTransport(
    private val deviceID: String,
    private val deviceName: String,
    private val scope: CoroutineScope,
    private val platform: WireMessage.Platform = WireMessage.Platform.Android,
) {
    private val client =
        createSupabaseClient(SupabaseSyncConfig.PROJECT_URL, SupabaseSyncConfig.ANON_KEY) {
            install(Realtime)
            install(Postgrest)
        }

    // region Hôte
    private var hostChannel: RealtimeChannel? = null
    private var hostSessionID: UUID? = null
    private val sessionsByPeerID = mutableMapOf<String, SupabaseTransportSession>()
    private val acceptedChannel = Channel<TransportSession>(Channel.UNLIMITED)
    private val hostJobs = mutableListOf<Job>()

    // endregion

    // region Pair
    private var joinedChannel: RealtimeChannel? = null
    private val joinedJobs = mutableListOf<Job>()
    // endregion

    /** `sessionID` identifie la **session de partage** — adresse le canal Realtime et la ligne
     * `quimene_open_games`, stable pour toute la durée de la session. `matchID`/`gameID`/
     * `participantCount` décrivent la partie *courante* : à chaque changement de partie au sein
     * de la même session, [updateActiveMatch] les met à jour sans jamais rappeler [advertise]
     * (qui ouvrirait un nouveau canal et casserait les pairs déjà connectés). */
    suspend fun advertise(
        sessionID: UUID,
        matchID: UUID,
        gameID: String,
        participantCount: Int,
        pairingCode: String,
    ) {
        hostSessionID = sessionID
        // Doc utilisateur — la table n'est plus accessible directement (migration
        // `secure_cacompte_open_games`) ; la fonction refuse un code déjà pris par une autre session.
        client.postgrest.rpc(
            "quimene_advertise_game",
            buildJsonObject {
                put("p_pairing_code", pairingCode)
                put("p_session_id", sessionID.toString())
                put("p_match_id", matchID.toString())
                put("p_game_id", gameID)
                put("p_participant_count", participantCount)
                put("p_device_name", deviceName)
                put("p_platform", platform.wireValue)
            },
        )

        val channel = client.realtime.channel("session:$sessionID") { presence { key = HOST_PRESENCE_KEY } }
        hostChannel = channel

        hostJobs +=
            scope.launch {
                channel.presenceChangeFlow().collect { action -> handleHostPresence(action, channel) }
            }
        hostJobs +=
            scope.launch {
                channel.broadcastFlow<SupabaseEnvelope>("msg").collect { envelope -> handleHostBroadcast(envelope) }
            }
        // Doc utilisateur P9 — remontée : après une mise en arrière-plan assez longue pour que
        // l'OS suspende le processus, le socket se ferme ; au retour au premier plan, le SDK le
        // rouvre et réabonne le canal tout seul, mais ne retrace jamais la présence pour nous —
        // `track()` n'est jamais rejoué automatiquement lors d'un ré-abonnement. Sans ce
        // ré-enregistrement à chaque passage à SUBSCRIBED, l'hôte disparaîtrait silencieusement
        // de la présence pour les pairs déjà connectés après une reconnexion sous-jacente.
        hostJobs +=
            scope.launch {
                channel.status.collect { status ->
                    if (status == RealtimeChannel.Status.SUBSCRIBED) channel.track(PresenceState(deviceName))
                }
            }

        channel.subscribe(blockUntilSubscribed = true)
        channel.track(PresenceState(deviceName))
    }

    /** Doc « Fin de partie » — appelé à chaque fois que l'hôte enchaîne une nouvelle partie au
     * sein de la même session : met à jour la ligne existante plutôt que d'en créer une nouvelle.
     * Ne touche ni au canal Realtime ni aux pairs déjà connectés — c'est [LiveSession.switchMatch]
     * qui les prévient. */
    suspend fun updateActiveMatch(
        matchID: UUID,
        gameID: String,
        participantCount: Int,
    ) {
        val sessionID = hostSessionID ?: return
        client.postgrest.rpc(
            "quimene_update_open_game",
            buildJsonObject {
                put("p_session_id", sessionID.toString())
                put("p_match_id", matchID.toString())
                put("p_game_id", gameID)
                put("p_participant_count", participantCount)
            },
        )
    }

    suspend fun stopAdvertising() {
        for (job in hostJobs) job.cancel()
        hostJobs.clear()
        for (session in sessionsByPeerID.values) session.markClosed()
        sessionsByPeerID.clear()
        hostChannel?.let { channel ->
            channel.untrack()
            channel.unsubscribe()
        }
        hostChannel = null
        hostSessionID?.let { sessionID ->
            trySuspend {
                client.postgrest.rpc(
                    "quimene_close_open_game",
                    buildJsonObject { put("p_session_id", sessionID.toString()) },
                )
            }
        }
        hostSessionID = null
    }

    fun acceptIncoming(): Flow<TransportSession> = acceptedChannel.receiveAsFlow()

    private fun handleHostPresence(
        action: PresenceAction,
        channel: RealtimeChannel,
    ) {
        for (peerID in action.joins.keys) {
            if (peerID == HOST_PRESENCE_KEY) continue
            session(peerID, channel)
        }
        for (peerID in action.leaves.keys) {
            sessionsByPeerID[peerID]?.markClosed()
            sessionsByPeerID.remove(peerID)
        }
    }

    private fun handleHostBroadcast(envelope: SupabaseEnvelope) {
        if (envelope.to != deviceID && envelope.to != HOST_PRESENCE_KEY) return
        val channel = hostChannel ?: return
        session(envelope.from, channel).receive(envelope.data)
    }

    /** Doc utilisateur — remontée : le « hello » d'un pair (broadcast) et son entrée de présence
     * (« il vient de rejoindre ») arrivent sur le même socket, dans le bon ordre, mais sont
     * consommés par deux collecteurs indépendants (présence, broadcast) : rien ne garantit que
     * la présence soit traitée avant le broadcast. Créer la session à la première occurrence,
     * quel que soit le chemin qui arrive en premier, rend le raccordement robuste à cette course
     * plutôt que de dépendre d'un ordre non garanti. */
    private fun session(
        peerID: String,
        channel: RealtimeChannel,
    ): SupabaseTransportSession {
        sessionsByPeerID[peerID]?.let { return it }
        val session =
            SupabaseTransportSession(channel = channel, selfID = deviceID, peerID = peerID, ownsChannel = false)
        sessionsByPeerID[peerID] = session
        acceptedChannel.trySend(session)
        return session
    }

    // region Pair

    /** Doc utilisateur — remplace le scan Wi-Fi/BLE : une simple lecture par code, sans aucune
     * notion de proximité physique. `quimene_resolve_game` renvoie zéro ou une ligne (jamais la
     * liste des parties ouvertes) : une liste vide veut dire « aucun code correspondant » — plus
     * direct que l'inspection du code d'erreur PostgREST que fait la version Swift. */
    suspend fun resolveGame(code: String): DiscoveredHost {
        val row =
            client.postgrest
                .rpc("quimene_resolve_game", buildJsonObject { put("p_pairing_code", code) })
                .decodeList<OpenGameRow>()
                .firstOrNull() ?: throw SupabaseTransportError.GameNotFound
        return DiscoveredHost(
            id = row.sessionID,
            deviceName = row.deviceName,
            gameID = row.gameID,
            participantCount = row.participantCount,
            platform = platformFromWireValue(row.platform),
        )
    }

    suspend fun connect(host: DiscoveredHost): TransportSession {
        val channel = client.realtime.channel("session:${host.id}") { presence { key = deviceID } }
        val session =
            SupabaseTransportSession(
                channel = channel,
                selfID = deviceID,
                peerID = HOST_PRESENCE_KEY,
                ownsChannel = true,
            )
        joinedChannel = channel

        joinedJobs +=
            scope.launch {
                channel.broadcastFlow<SupabaseEnvelope>("msg").collect { envelope ->
                    if (envelope.to == deviceID) session.receive(envelope.data)
                }
            }
        // Doc utilisateur — remplace `LiveSession.hostLeft` détecté côté BLE/Wi-Fi : l'hôte
        // quitte la présence du canal (déconnexion propre ou abrupte, le serveur Realtime la
        // détecte dans les deux cas) → cette session se ferme, exactement comme une coupure
        // réseau avant.
        joinedJobs +=
            scope.launch {
                channel.presenceChangeFlow().collect { action ->
                    if (action.leaves.containsKey(HOST_PRESENCE_KEY)) session.markClosed()
                }
            }
        joinedJobs +=
            scope.launch {
                channel.status.collect { status ->
                    if (status == RealtimeChannel.Status.SUBSCRIBED) channel.track(PresenceState(deviceName))
                }
            }

        channel.subscribe(blockUntilSubscribed = true)
        channel.track(PresenceState(deviceName))
        return session
    }

    // endregion

    companion object {
        /** Doc utilisateur — clé constante côté hôte, jamais le `deviceID` réel : un pair n'a
         * besoin de connaître que ce sentinel, pas un identifiant d'appareil qu'il n'a aucun
         * moyen d'obtenir à l'avance. */
        private const val HOST_PRESENCE_KEY = "host"
    }
}

/** Équivalent de `try?` côté Swift, sans avaler `CancellationException` (voir [LiveSession]). */
private suspend fun trySuspend(block: suspend () -> Unit) {
    try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        // Best-effort : une ligne quimene_open_games qui ne se supprime pas n'empêche rien
        // (elle sera écrasée par un futur `upsert` au même pairing_code, ou périmée).
    }
}

@Serializable
private data class SupabaseEnvelope(
    val from: String,
    val to: String,
    val data: String,
)

@Serializable
private data class PresenceState(
    val deviceName: String,
)

sealed class SupabaseTransportError(
    message: String,
) : Exception(message) {
    /** Aucune ligne `quimene_open_games` pour ce code — code erroné, périmé (partie déjà
     * arrêtée), ou jamais existé. */
    data object GameNotFound : SupabaseTransportError("Aucune partie ne correspond à ce code.")
}

@Serializable
data class OpenGameRow(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("session_id") @Serializable(with = UUIDSerializer::class) val sessionID: UUID,
    @SerialName("match_id") @Serializable(with = UUIDSerializer::class) val matchID: UUID,
    @SerialName("game_id") val gameID: String,
    @SerialName("participant_count") val participantCount: Int,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
)


/** Doc utilisateur P9 — clé **anon/publique** Supabase : conçue pour être embarquée dans un
 * client (l'accès aux tables passe par des fonctions SQL qui exigent un code ou un identifiant,
 * pas par le secret), à la
 * différence d'une clé `service_role`. Le contenu réel des manches reste protégé par
 * [SessionCrypto] (chiffrement dérivé du code d'appairage), pas par cette clé. Même projet que
 * l'app Apple (`SupabaseSyncConfig.swift`) : les deux plateformes doivent pointer vers le même
 * canal Realtime pour se voir. */
object SupabaseSyncConfig {
    const val PROJECT_URL = "https://hcjehnnvqmkdwirgpcgu.supabase.co"
    const val ANON_KEY = "sb_publishable_YhV5A3mH3aUCejLx1QC2OQ_Hc18SA_b"
}

private val WireMessage.Platform.wireValue: String
    get() =
        when (this) {
            WireMessage.Platform.Apple -> "apple"
            WireMessage.Platform.Android -> "android"
        }

private fun platformFromWireValue(value: String): WireMessage.Platform =
    when (value) {
        "android" -> WireMessage.Platform.Android
        else -> WireMessage.Platform.Apple
    }

class SupabaseTransportSession(
    private val channel: RealtimeChannel,
    private val selfID: String,
    private val peerID: String,
    private val ownsChannel: Boolean,
) : TransportSession {
    private val channelIO = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = channelIO.receiveAsFlow()

    override suspend fun send(data: ByteArray) {
        val envelope = SupabaseEnvelope(from = selfID, to = peerID, data = Base64.getEncoder().encodeToString(data))
        channel.broadcast("msg", envelope)
    }

    fun receive(base64: String) {
        val data = runCatching { Base64.getDecoder().decode(base64) }.getOrNull() ?: return
        channelIO.trySend(data)
    }

    fun markClosed() {
        channelIO.close()
    }

    /** Doc utilisateur — une session côté hôte (une par pair) ne possède pas le canal, partagé
     * entre tous les pairs de cette partie : la fermer ne doit jamais le désabonner (ça
     * couperait tout le monde). Seule la session du pair ([ownsChannel] `true`, un canal par
     * pair qui rejoint) le fait vraiment. */
    override suspend fun close() {
        channelIO.close()
        if (!ownsChannel) return
        channel.untrack()
        channel.unsubscribe()
    }
}
