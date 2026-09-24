package com.quimene.sync

import com.quimene.domain.model.UUIDSerializer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PresenceAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Doc utilisateur P9 — clé **anon/publique** Supabase : conçue pour être embarquée dans un
 * client (l'accès aux tables passe par des fonctions SQL qui exigent un code ou un identifiant,
 * pas par le secret). Le contenu des parties partagées reste protégé par [SessionCrypto]. Même
 * projet que l'app Apple (`SupabaseSyncConfig.swift`). */
object SupabaseSyncConfig {
    const val PROJECT_URL = "https://hcjehnnvqmkdwirgpcgu.supabase.co"
    const val ANON_KEY = "sb_publishable_YhV5A3mH3aUCejLx1QC2OQ_Hc18SA_b"
}

/** Un seul client Supabase pour les sessions (appels et canal temps réel). */
internal object SessionSupabase {
    val client: SupabaseClient by lazy {
        createSupabaseClient(SupabaseSyncConfig.PROJECT_URL, SupabaseSyncConfig.ANON_KEY) {
            install(Postgrest)
            install(Realtime)
        }
    }
}

/** [OnlineSessionBackend] sur les fonctions SQL de la migration `create_quimene_sessions` —
 * miroir de `SupabaseSessionBackend` (Swift). */
class SupabaseSessionBackend : OnlineSessionBackend {
    private val client get() = SessionSupabase.client

    override suspend fun open(
        sessionID: UUID,
        pairingCode: String,
        ownerDeviceID: String,
        allowsContributors: Boolean,
    ) {
        call {
            client.postgrest.rpc(
                "quimene_session_open",
                buildJsonObject {
                    put("p_session_id", sessionID.toString())
                    put("p_pairing_code", pairingCode)
                    put("p_owner_device_id", ownerDeviceID)
                    put("p_allows_contributors", allowsContributors)
                },
            )
        }
    }

    override suspend fun resolve(pairingCode: String): OnlineSessionInfo? =
        call {
            client.postgrest
                .rpc("quimene_session_resolve", buildJsonObject { put("p_pairing_code", pairingCode) })
                .decodeList<ResolvedRow>()
                .firstOrNull()
                ?.let { OnlineSessionInfo(it.sessionID, it.ownerDeviceID, it.allowsContributors, it.lastSeq) }
        }

    override suspend fun append(
        sessionID: UUID,
        expectedSeq: Long,
        eventID: UUID,
        matchID: UUID,
        deviceID: String,
        ciphertext: String,
    ): Long =
        call {
            client.postgrest
                .rpc(
                    "quimene_session_append",
                    buildJsonObject {
                        put("p_session_id", sessionID.toString())
                        put("p_expected_seq", expectedSeq)
                        put("p_event_id", eventID.toString())
                        put("p_match_id", matchID.toString())
                        put("p_device_id", deviceID)
                        put("p_ciphertext", ciphertext)
                    },
                ).decodeAs<Long>()
        }

    override suspend fun events(
        sessionID: UUID,
        afterSeq: Long,
    ): List<RawSessionEvent> =
        call {
            client.postgrest
                .rpc(
                    "quimene_session_events_after",
                    buildJsonObject {
                        put("p_session_id", sessionID.toString())
                        put("p_after_seq", afterSeq)
                    },
                ).decodeList<EventRow>()
                .map { RawSessionEvent(it.seq, it.eventID, it.matchID, it.deviceID, it.ciphertext) }
        }

    override suspend fun close(
        sessionID: UUID,
        ownerDeviceID: String,
    ) {
        call {
            client.postgrest.rpc(
                "quimene_session_close",
                buildJsonObject {
                    put("p_session_id", sessionID.toString())
                    put("p_owner_device_id", ownerDeviceID)
                },
            )
        }
    }

    /** Traduit les exceptions levées par les fonctions SQL (`raise exception '<code>'`). */
    private suspend fun <T> call(body: suspend () -> T): T =
        try {
            body()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: RestException) {
            val message = error.message.orEmpty()
            throw when {
                "pairing_code_taken" in message -> OnlineSessionError.PairingCodeTaken
                "stale_seq" in message -> OnlineSessionError.StaleSequence
                "session_closed" in message -> OnlineSessionError.SessionClosed
                "not_session_owner" in message -> OnlineSessionError.NotSessionOwner
                "event_too_large" in message -> OnlineSessionError.EventTooLarge
                else -> error
            }
        }

    @Serializable
    private data class ResolvedRow(
        @SerialName("session_id") @Serializable(with = UUIDSerializer::class) val sessionID: UUID,
        @SerialName("owner_device_id") val ownerDeviceID: String,
        @SerialName("allows_contributors") val allowsContributors: Boolean,
        @SerialName("last_seq") val lastSeq: Long,
    )

    @Serializable
    private data class EventRow(
        val seq: Long,
        @SerialName("event_id") @Serializable(with = UUIDSerializer::class) val eventID: UUID,
        @SerialName("match_id") @Serializable(with = UUIDSerializer::class) val matchID: UUID,
        @SerialName("device_id") val deviceID: String,
        val ciphertext: String,
    )
}

/**
 * Canal `session:<id>` — miroir de `SessionChannel` (Swift) : la notification « un événement
 * vient d'arriver » (envoyée par le déclencheur SQL) et la présence des appareils. Le contenu se
 * lit toujours via [OnlineSession.sync] : une notification perdue ne perd rien. L'identifiant est
 * en minuscules (`UUID.toString()`), comme la forme texte d'un `uuid` Postgres utilisée par le
 * déclencheur.
 */
class SessionChannel(
    sessionID: UUID,
    private val me: SessionPresence,
    private val scope: CoroutineScope,
) {
    private val channel: RealtimeChannel =
        SessionSupabase.client.realtime.channel("session:$sessionID") { presence { key = me.deviceID } }
    private val jobs = mutableListOf<Job>()
    private val present = mutableMapOf<String, SessionPresence>()

    private val notificationsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val notifications: SharedFlow<Unit> = notificationsFlow
    private val presenceFlow = MutableStateFlow<List<SessionPresence>>(emptyList())
    val presence: StateFlow<List<SessionPresence>> = presenceFlow

    suspend fun connect() {
        jobs +=
            scope.launch {
                // Simple signal « rattraper » : le contenu (le numéro) n'est qu'indicatif.
                channel.broadcastFlow<JsonObject>("event").collect { notificationsFlow.emit(Unit) }
            }
        jobs += scope.launch { channel.presenceChangeFlow().collect(::apply) }
        // `track()` n'est jamais rejoué par un ré-abonnement du SDK : le refaire à chaque
        // passage à SUBSCRIBED (même remontée que côté Apple).
        jobs +=
            scope.launch {
                channel.status.collect { status ->
                    if (status == RealtimeChannel.Status.SUBSCRIBED) runCatching { channel.track(me) }
                }
            }
        channel.subscribe(blockUntilSubscribed = true)
        channel.track(me)
    }

    suspend fun disconnect() {
        for (job in jobs) job.cancel()
        jobs.clear()
        runCatching { channel.untrack() }
        runCatching { channel.unsubscribe() }
    }

    private fun apply(action: PresenceAction) {
        for ((key, presence) in action.joins) {
            runCatching { OnlineSession.json.decodeFromJsonElement(SessionPresence.serializer(), presence.state) }
                .getOrNull()
                ?.let { present[key] = it }
        }
        for (key in action.leaves.keys) present.remove(key)
        presenceFlow.value = present.values.sortedBy { it.deviceName }
    }
}
