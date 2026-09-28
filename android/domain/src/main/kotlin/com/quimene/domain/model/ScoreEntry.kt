package com.quimene.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Miroir de `ScoreExplanation` (`ScoreEntry.swift`) : pourquoi `computedValue` diffère de la
 * saisie brute. Le texte affiché est rédigé par l'app, dans la langue de l'utilisateur. */
@Serializable
enum class ScoreExplanation {
    /** Skyjo : a fermé la manche sans le score le plus bas, score doublé. */
    @SerialName("doubledForClosingWithoutLowest")
    DoubledForClosingWithoutLowest,

    /** Mölkky : dépassement de 50, retour à 25. */
    @SerialName("bustBackTo25")
    BustBackTo25,

    /** Yams : bonus de la section haute (+35). */
    @SerialName("upperSectionBonus")
    UpperSectionBonus,
}

/** Miroir de `ScoreEntry.swift`. */
@Serializable
data class ScoreEntry(
    @Serializable(with = UUIDSerializer::class)
    val participantID: UUID,
    val rawValue: Int,
    val computedValue: Int,
    val explanation: ScoreExplanation? = null,
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
