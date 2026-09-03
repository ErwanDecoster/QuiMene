package com.cacompte.sync

import com.cacompte.domain.model.SharedMatchSummaryPayload
import com.cacompte.domain.model.UUIDSerializer
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Miroir de `SharedProfileTransport.swift` (doc 14, phase 2) — boîte aux lettres transitoire :
 * un résumé de partie terminée, poussé une fois par participant lié, retiré dès que l'appareil
 * concerné l'a récupéré. Jamais une copie durable côté serveur — même discipline que
 * `cacompte_open_games` ([SupabaseTransport]), juste une fenêtre de purge plus généreuse (un ami
 * peut rester hors ligne des semaines, pas seulement le temps d'une soirée). Orchestré par
 * `com.cacompte.app.profilesharing.SharedProfileSyncCoordinator` (`:app`), pas par cette classe
 * elle-même — miroir du même découpage que [SupabaseTransport]/`LiveShareCoordinator`.
 */
class SharedProfileTransport {
    private val client =
        createSupabaseClient(SupabaseSyncConfig.PROJECT_URL, SupabaseSyncConfig.ANON_KEY) {
            install(Postgrest)
        }

    /** `upsert` sur `(match_id, shared_profile_id)` plutôt qu'un simple insert : un push
     * retenté après une réponse perdue (mais un succès côté serveur) ne doit jamais dupliquer la
     * ligne — l'appelant ne sait retenter qu'en entier, pas distinguer « jamais reçu » de « reçu
     * mais confirmation perdue ». */
    suspend fun push(rows: List<SharedMatchSummaryRow>) {
        if (rows.isEmpty()) return
        client.postgrest.from(SHARED_SUMMARIES_TABLE).upsert(rows) {
            onConflict = "match_id,shared_profile_id"
        }
    }

    suspend fun fetchPending(sharedProfileIDs: List<UUID>): List<SharedMatchSummaryRow> {
        if (sharedProfileIDs.isEmpty()) return emptyList()
        return client.postgrest
            .from(SHARED_SUMMARIES_TABLE)
            .select {
                filter { isIn("shared_profile_id", sharedProfileIDs.map { it.toString() }) }
            }.decodeList()
    }

    suspend fun delete(
        matchID: UUID,
        sharedProfileID: UUID,
    ) {
        client.postgrest.from(SHARED_SUMMARIES_TABLE).delete {
            filter {
                eq("match_id", matchID.toString())
                eq("shared_profile_id", sharedProfileID.toString())
            }
        }
    }

    private companion object {
        const val SHARED_SUMMARIES_TABLE = "cacompte_shared_match_summaries"
    }
}

@Serializable
data class SharedMatchSummaryRow(
    @SerialName("match_id") @Serializable(with = UUIDSerializer::class) val matchID: UUID,
    @SerialName("shared_profile_id") @Serializable(with = UUIDSerializer::class) val sharedProfileID: UUID,
    val payload: SharedMatchSummaryPayload,
)
