package com.quimene.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `SharedMatchSummaryPayload.swift` (transport Supabase, doc 09/14 — pas encore câblé
 * côté Android, l'étape F). Clés JSON snake_case (`@SerialName`), différentes des propriétés
 * Kotlin camelCase — même écart que côté Swift (`CodingKeys`).
 */
@Serializable
data class SharedMatchSummaryPayload(
    @SerialName("game_id") val gameID: String,
    @SerialName("rules_version") val rulesVersion: Int,
    @SerialName("played_at") @Serializable(with = InstantSerializer::class) val playedAt: Instant,
    val standings: List<Entry>,
) {
    @Serializable
    data class Entry(
        @SerialName("shared_profile_id")
        @Serializable(with = UUIDSerializer::class)
        val sharedProfileID: UUID?,
        val nickname: String,
        @SerialName("avatar_kind") val avatarKind: String,
        @SerialName("avatar_value") val avatarValue: String,
        @SerialName("palette_id") val paletteID: String,
        val rank: Int,
        val score: Int,
    )
}
