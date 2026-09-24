package com.quimene.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.LocalIsDarkTheme
import com.quimene.designsystem.tokens.Radius
import com.quimene.designsystem.tokens.Space

/** Gouttière recommandée entre deux cartes (charte §3.1/§5.5). */
val CardGutter: Dp = 12.dp

/**
 * Miroir de `Card.swift` : `elev/1` (charte §5.2, colonne Android) = `surfaceContainerLow`,
 * tonal 1 dp + ombre L1 — pas un simple remplissage plat. Remontée utilisateur : les listes ne se
 * détachaient pas du fond d'écran, surtout en couleur dynamique (Material You), où `neutral/bg`
 * et `neutral/surface` peuvent devenir presque identiques puisque les deux dérivent du même fond
 * d'écran système ([com.quimene.designsystem.theme.QuiMeneTheme]) — un simple `Modifier
 * .background()` ne garantissait alors plus aucune séparation visuelle. `Surface` avec
 * `tonalElevation`/`shadowElevation` garantit une différence perceptible quelles que soient les
 * couleurs effectives, dynamiques ou fixes. Bordure 1 dp supplémentaire **seulement en mode
 * sombre** : une ombre portée est peu visible sur un fond déjà sombre.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val isDark = LocalIsDarkTheme.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        color = colors.neutralSurface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = if (isDark) BorderStroke(1.dp, colors.neutralBorder) else null,
    ) {
        Column(modifier = Modifier.padding(Space.lg), content = { content() })
    }
}
