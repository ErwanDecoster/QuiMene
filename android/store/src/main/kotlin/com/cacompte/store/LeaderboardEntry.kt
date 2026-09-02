package com.cacompte.store

import java.util.UUID

/**
 * Miroir de `LeaderboardEntry.swift` — un joueur dans le classement d'un jeu donné. Champs à
 * plat plutôt qu'une référence à [PlayerEntity], pour rester une valeur simple qui ne fait pas
 * fuiter un type Room hors de ce module.
 */
data class LeaderboardEntry(
    val playerID: UUID,
    val name: String,
    val avatarKind: String,
    val avatarValue: String,
    val avatarPhoto: ByteArray?,
    val paletteID: String,
    val played: Int,
    val wins: Int,
    val winRate: Double,
    /** `(nbJoueurs − rang) / (nbJoueurs − 1)`, moyenné — départage à taux de victoire égal. */
    val averageNormalizedRank: Double,
) {
    override fun equals(other: Any?): Boolean =
        other is LeaderboardEntry &&
            playerID == other.playerID &&
            name == other.name &&
            avatarKind == other.avatarKind &&
            avatarValue == other.avatarValue &&
            (avatarPhoto?.contentEquals(other.avatarPhoto) ?: (other.avatarPhoto == null)) &&
            paletteID == other.paletteID &&
            played == other.played &&
            wins == other.wins &&
            winRate == other.winRate &&
            averageNormalizedRank == other.averageNormalizedRank

    override fun hashCode(): Int = playerID.hashCode()
}
