package com.quimene.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.quimene.designsystem.tokens.LocalAppColors

/**
 * Symbole de courbe distinct par joueur (charte §1.5) — un daltonien total doit pouvoir lire un
 * graphique sans la couleur seule. `BasicChartSymbolShape` (Swift Charts) n'a que 8 formes
 * natives pour 10 joueurs ; le doublement de forme aux joueurs 6/7/9 est un écart connu du
 * source Swift (pas encore de jeu de symboles custom), porté ici tel quel plutôt que "corrigé"
 * silencieusement — l'unicité n'est garantie/requise que parmi les 6 premiers joueurs.
 */
enum class ChartSymbol { Circle, Square, Triangle, Diamond, Cross, Asterisk, Pentagon, Plus }

private val chartSymbols =
    listOf(
        ChartSymbol.Circle, // 1
        ChartSymbol.Square, // 2
        ChartSymbol.Triangle, // 3
        ChartSymbol.Diamond, // 4
        ChartSymbol.Cross, // 5
        ChartSymbol.Asterisk, // 6 — doublon avec 2 (charte : symboles natifs limités à 8)
        ChartSymbol.Triangle, // 7 — doublon avec 3
        ChartSymbol.Pentagon, // 8
        ChartSymbol.Square, // 9 — doublon avec 2
        ChartSymbol.Plus, // 10
    )

private val accessibilityNames =
    listOf(
        "Azur",
        "Ambre",
        "Émeraude",
        "Magenta",
        "Ardoise",
        "Cyan",
        "Vermillon",
        "Violet",
        "Olive",
        "Rose",
    )

/**
 * Miroir de `PlayerPalette.swift` — sans champ `color` résolu à la construction : contrairement
 * à `Color("player/N", bundle:)` sur iOS qui s'adapte seul au thème système, une couleur Compose
 * dépend du thème courant et ne peut être résolue que dans un contexte `@Composable`
 * ([color]), pas stockée dans une valeur immuable construite en dehors de la composition.
 */
data class PlayerPalette(
    val index: Int,
) {
    init {
        require(index in 1..10) { "player index must be in 1..10, was $index" }
    }

    val chartSymbol: ChartSymbol get() = chartSymbols[index - 1]
    val accessibilityName: String get() = accessibilityNames[index - 1]
}

/** Couleur résolue au thème courant (clair/sombre) pour ce joueur. */
@Composable
fun PlayerPalette.color(): Color = LocalAppColors.current.player(index)
