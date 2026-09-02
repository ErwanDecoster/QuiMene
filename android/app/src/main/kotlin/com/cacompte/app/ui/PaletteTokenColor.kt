package com.cacompte.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.domain.rules.PaletteToken

/**
 * Couleur résolue au thème courant pour la palette d'un jeu (`GameDefinition.paletteID`). Vit
 * dans `:app` plutôt que `:designsystem` : [PaletteToken] est un type `:domain`, et
 * `:designsystem` ne dépend délibérément d'aucun autre module (étape A). Les 9 tokens
 * `azur`…`olive` réutilisent exactement les couleurs joueur 1..9 (même liste, même ordre que
 * `PlayerPalette`) ; `ink`/`brass`/`teal` réutilisent les couleurs de marque.
 */
@Composable
fun PaletteToken.toColor(): Color {
    val colors = LocalAppColors.current
    return when (this) {
        PaletteToken.Ink -> colors.brandInk
        PaletteToken.Brass -> colors.brandBrass
        PaletteToken.Teal -> colors.brandTeal
        PaletteToken.Azur -> colors.player(1)
        PaletteToken.Ambre -> colors.player(2)
        PaletteToken.Emeraude -> colors.player(3)
        PaletteToken.Magenta -> colors.player(4)
        PaletteToken.Ardoise -> colors.player(5)
        PaletteToken.Cyan -> colors.player(6)
        PaletteToken.Vermillon -> colors.player(7)
        PaletteToken.Violet -> colors.player(8)
        PaletteToken.Olive -> colors.player(9)
    }
}
