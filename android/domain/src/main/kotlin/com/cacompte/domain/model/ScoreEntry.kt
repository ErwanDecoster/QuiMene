package com.cacompte.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Miroir de `ScoreEntry.swift`. */
@Serializable
data class ScoreEntry(
    @Serializable(with = UUIDSerializer::class)
    val participantID: UUID,
    val rawValue: Int,
    val computedValue: Int,
    val explanation: String? = null,
    val detail: ScoreDetail? = null,
    val modifiers: Set<ModifierID> = emptySet(),
)

/** Miroir de `Round.swift` (déclaré dans le même fichier Swift que `ScoreEntry`). */
@Serializable
data class Round(
    val index: Int,
    val entries: List<ScoreEntry>,
    @Serializable(with = InstantSerializer::class)
    val committedAt: Instant,
    val note: String? = null,
)
