package com.cacompte.store

import java.time.Instant
import java.util.UUID

/** Miroir de `ProfileStats.swift` — statistiques de profil, agrégées sur tout l'historique d'un
 * joueur, calculées à la demande (aucune pré-agrégation persistée, doc 06). */
data class ProfileStats(
    val played: Int,
    val wins: Int,
    val winRate: Double,
    val averageRank: Double,
    /** `(nbJoueurs − rang) / (nbJoueurs − 1)`, moyenné — comparable entre parties à effectifs
     * différents (doc 06 : gagner à 2 n'est pas gagner à 8). */
    val averageNormalizedRank: Double,
    val byGame: List<GameBreakdown>,
    val nemesis: Nemesis?,
    val currentWinStreak: Int,
    val bestWinStreak: Int,
    /** 12 derniers mois, ordre chronologique, mois sans partie inclus à zéro. */
    val activity: List<MonthActivity>,
) {
    /** Miroir du tuple nommé Swift `(value: Int, date: Date)`. */
    data class ScoreRecord(
        val value: Int,
        val date: Instant,
    )

    data class GameBreakdown(
        val gameID: String,
        val gameName: String,
        val played: Int,
        val wins: Int,
        val winRate: Double,
        /** Le score personnel le plus favorable (selon le sens du jeu) et sa date. */
        val bestScore: ScoreRecord?,
        /** Le score personnel le moins favorable et sa date. */
        val worstScore: ScoreRecord?,
    )

    /** Doc 06 : « adversaire rencontré au moins 5 fois contre qui le taux de victoire est le
     * plus faible ». Le taux de victoire s'entend comme ailleurs : la part de parties gagnées
     * (rang 1) parmi celles jouées ensemble — pas un score tête-à-tête séparé. */
    data class Nemesis(
        val playerID: UUID,
        val name: String,
        val matchesTogether: Int,
        val winRateWithThemPresent: Double,
    )

    data class MonthActivity(
        /** Format `yyyy-MM`, pour un tri et un affichage stables indépendants du fuseau horaire. */
        val monthKey: String,
        val count: Int,
    )

    companion object {
        val empty =
            ProfileStats(
                played = 0,
                wins = 0,
                winRate = 0.0,
                averageRank = 0.0,
                averageNormalizedRank = 0.0,
                byGame = emptyList(),
                nemesis = null,
                currentWinStreak = 0,
                bestWinStreak = 0,
                activity = emptyList(),
            )
    }
}
