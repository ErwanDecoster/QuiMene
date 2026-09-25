package com.quimene.sync

import com.quimene.domain.model.UUIDSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * Doc 16, phase F — miroir de `LiveActivityPushClient.swift` (envoi seulement) : quand un appareil
 * Android enregistre une manche dans une session en ligne, c'est lui qui met à jour l'écran
 * verrouillé (Live Activity) des iPhone de la session, via la fonction Edge
 * `quimene-live-activity-push` — même quand le créateur est éteint. Android n'a pas de Live
 * Activity à lui : il n'inscrit aucun jeton.
 *
 * [LiveActivityContent] doit rester identique à `MatchActivityAttributes.ContentState` (Swift),
 * que l'iPhone décode tel quel : même noms de champs, `isStale` toujours présent.
 */
@Serializable
data class LiveActivityContent(
    @Serializable(with = UUIDSerializer::class) val matchID: UUID,
    val gameName: String,
    /** Nom de symbole SF de la définition du jeu (`GameDefinition.symbol`), affiché par l'iPhone. */
    val gameSymbol: String,
    val roundNumber: Int,
    val standings: List<Standing>,
    val isStale: Boolean = false,
) {
    @Serializable
    data class Standing(
        @Serializable(with = UUIDSerializer::class) val id: UUID,
        val name: String,
        val score: Int,
    )
}

@Serializable
internal data class LiveActivityPushBody(
    val activityKey: String,
    val event: String,
    val contentState: LiveActivityContent,
)

object LiveActivityPushClient {
    /** Même clé que `MatchLiveActivityController.activityKey` (Swift) : `uuidString` en majuscules. */
    fun sessionKey(sessionID: UUID): String = "session:${sessionID.toString().uppercase()}"

    internal fun body(
        activityKey: String,
        ended: Boolean,
        content: LiveActivityContent,
    ): String =
        OnlineSession.json.encodeToString(
            LiveActivityPushBody.serializer(),
            LiveActivityPushBody(activityKey, if (ended) "end" else "update", content),
        )

    /** Best-effort : un échec ne fait jamais échouer la manche, il prive seulement un iPhone
     * suspendu d'une mise à jour (il rattrape au retour au premier plan). */
    suspend fun push(
        activityKey: String,
        ended: Boolean,
        content: LiveActivityContent,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val connection =
                URI("${SupabaseSyncConfig.PROJECT_URL}/functions/v1/quimene-live-activity-push")
                    .toURL()
                    .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${SupabaseSyncConfig.ANON_KEY}")
                connection.outputStream.use { it.write(body(activityKey, ended, content).encodeToByteArray()) }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
        Unit
    }
}
