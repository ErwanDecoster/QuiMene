package com.quimene.sync

import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

/**
 * Doc 16, phase C — miroir de `OnlineSession.swift` : client des sessions en ligne
 * (`quimene_session_*`, migration `create_quimene_sessions`). Le journal de la session vit sur le
 * serveur et fait foi pour tous les appareils, créateur compris.
 *
 * Format d'un événement stocké, commun à iOS et Android (vérifié par
 * `spec/session/sealed-events.json`, produit par le code Swift) : le [StampedEvent] en JSON,
 * scellé en AES-GCM avec la clé de session ([SessionCrypto.deriveKey]), puis en base64. Son
 * `lamport` vaut le numéro de séquence du serveur.
 */
@Serializable
data class SessionPresence(
    val deviceID: String,
    val deviceName: String,
    val isOwner: Boolean,
)

data class OnlineSessionInfo(
    val sessionID: UUID,
    val ownerDeviceID: String,
    val allowsContributors: Boolean,
    val lastSeq: Long,
)

/** Un événement tel que stocké par le serveur, encore chiffré. */
data class RawSessionEvent(
    val seq: Long,
    val eventID: UUID,
    val matchID: UUID,
    val deviceID: String,
    val ciphertext: String,
)

/** Un événement lisible du journal de la session. */
data class SessionEventRecord(
    val seq: Long,
    val matchID: UUID,
    val event: StampedEvent,
)

sealed class OnlineSessionError(
    message: String,
) : Exception(message) {
    /** Code déjà pris par une autre session vivante : en tirer un autre. */
    data object PairingCodeTaken : OnlineSessionError("pairing_code_taken")

    /** Aucun code ne correspond (erroné, expiré, ou session fermée). */
    data object SessionNotFound : OnlineSessionError("session_not_found")

    /** Un autre appareil a ajouté un événement entre-temps : le journal local est déjà rattrapé. */
    data object StaleSequence : OnlineSessionError("stale_seq")

    data object SessionClosed : OnlineSessionError("session_closed")

    data object NotSessionOwner : OnlineSessionError("not_session_owner")

    data object EventTooLarge : OnlineSessionError("event_too_large")
}

/** Accès au serveur, isolé pour que [OnlineSession] se teste sans réseau. */
interface OnlineSessionBackend {
    suspend fun open(
        sessionID: UUID,
        pairingCode: String,
        ownerDeviceID: String,
        allowsContributors: Boolean,
    )

    suspend fun resolve(pairingCode: String): OnlineSessionInfo?

    suspend fun append(
        sessionID: UUID,
        expectedSeq: Long,
        eventID: UUID,
        matchID: UUID,
        deviceID: String,
        ciphertext: String,
    ): Long

    suspend fun events(
        sessionID: UUID,
        afterSeq: Long,
    ): List<RawSessionEvent>

    suspend fun close(
        sessionID: UUID,
        ownerDeviceID: String,
    )
}

/** Une session ouverte ou rejointe par cet appareil : son journal lisible, le rattrapage, l'ajout. */
class OnlineSession(
    val sessionID: UUID,
    val pairingCode: String,
    val deviceID: String,
    private val backend: OnlineSessionBackend,
) {
    // `Mutex` n'est pas réentrant : les fonctions publiques verrouillent, les `…Locked` non.
    private val mutex = Mutex()
    private val key = SessionCrypto.deriveKey(pairingCode, sessionID)
    private val recordsInternal = mutableListOf<SessionEventRecord>()

    /** Dernier numéro vu, lisible ou non (un événement indéchiffrable compte dans la
     * numérotation). */
    var lastSeq: Long = 0
        private set

    suspend fun records(): List<SessionEventRecord> = mutex.withLock { recordsInternal.toList() }

    /** Rattrape tout ce qui suit le dernier numéro connu, par lots ; renvoie le nouveau lisible. */
    suspend fun sync(): List<SessionEventRecord> = mutex.withLock { syncLocked() }

    private suspend fun syncLocked(): List<SessionEventRecord> {
        val fresh = mutableListOf<SessionEventRecord>()
        while (true) {
            val batch = backend.events(sessionID, lastSeq)
            for (raw in batch) {
                if (raw.seq <= lastSeq) continue
                lastSeq = raw.seq
                open(raw, key)?.let {
                    recordsInternal += it
                    fresh += it
                }
            }
            if (batch.size < PAGE_SIZE) break
        }
        return fresh
    }

    /** Ajoute un événement à la suite du journal connu. Devancé : rattrape puis lève
     * [OnlineSessionError.StaleSequence]. [eventID]/[occurredAt] : à fournir pour publier un
     * événement qui existe déjà en local — l'identifiant du `matchCreated` est celui de la partie. */
    suspend fun append(
        event: MatchEvent,
        matchID: UUID,
        eventID: UUID = UUID.randomUUID(),
        occurredAt: Instant = Instant.now(),
    ): SessionEventRecord =
        mutex.withLock {
            val expected = lastSeq + 1
            val stamped =
                StampedEvent(
                    id = eventID,
                    lamport = expected.toULong(),
                    deviceID = deviceID,
                    occurredAt = occurredAt,
                    event = event,
                )
            val ciphertext = seal(stamped, key)
            val seq =
                try {
                    backend.append(sessionID, expected, eventID, matchID, deviceID, ciphertext)
                } catch (stale: OnlineSessionError.StaleSequence) {
                    syncLocked()
                    throw stale
                }
            if (seq != expected || seq != lastSeq + 1) {
                // Réponse d'un ajout déjà enregistré (envoi retenté) : le journal fait foi.
                syncLocked()
                return@withLock recordsInternal.firstOrNull { it.event.id == eventID }
                    ?: throw OnlineSessionError.StaleSequence
            }
            lastSeq = seq
            // L'événement tel que le relisent les autres appareils : la sérialisation de la date
            // (secondes décimales, format commun avec iOS) arrondit les nanosecondes.
            val published = open(RawSessionEvent(seq, eventID, matchID, deviceID, ciphertext), key)?.event ?: stamped
            SessionEventRecord(seq, matchID, published).also { recordsInternal += it }
        }

    /** Journal d'une partie, prêt pour `MatchEngine.replay`. */
    suspend fun eventsForMatch(matchID: UUID): List<StampedEvent> =
        mutex.withLock { recordsInternal.filter { it.matchID == matchID }.map { it.event } }

    /** La partie courante : celle du `matchCreated` le plus récent. */
    suspend fun currentMatchID(): UUID? =
        mutex.withLock { recordsInternal.lastOrNull { it.event.event is MatchEvent.MatchCreated }?.matchID }

    companion object {
        const val PAGE_SIZE = 500

        /** Même configuration que l'ancien `WireCodec` : JSON identique à `JSONEncoder` Swift. */
        val json =
            Json {
                encodeDefaults = true
                explicitNulls = false
            }

        fun newPairingCode(): String = "%06d".format(Random.nextInt(0, 1_000_000))

        fun seal(
            stamped: StampedEvent,
            key: ByteArray,
        ): String {
            val plaintext = json.encodeToString(StampedEvent.serializer(), stamped).encodeToByteArray()
            return Base64.getEncoder().encodeToString(SessionCrypto.encrypt(plaintext, key))
        }

        fun open(
            raw: RawSessionEvent,
            key: ByteArray,
        ): SessionEventRecord? =
            runCatching {
                val plaintext = SessionCrypto.decrypt(Base64.getDecoder().decode(raw.ciphertext), key)
                val stamped = json.decodeFromString(StampedEvent.serializer(), plaintext.decodeToString())
                SessionEventRecord(raw.seq, raw.matchID, stamped)
            }.getOrNull()
    }
}
