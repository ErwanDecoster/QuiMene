package com.quimene.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Miroir de `ScoreInput.swift`. */
@Serializable
data class ScoreInput(
    @Serializable(with = UUIDSerializer::class)
    val participantID: UUID,
    val rawValue: Int,
    val detail: ScoreDetail? = null,
    val modifiers: Set<ModifierID> = emptySet(),
)

/** Miroir de `RoundDraft.swift` (déclaré dans le même fichier Swift que `ScoreInput`). */
@Serializable
data class RoundDraft(
    val index: Int,
    val inputs: List<ScoreInput>,
    val note: String? = null,
)
