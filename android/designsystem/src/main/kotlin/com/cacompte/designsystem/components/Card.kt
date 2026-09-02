package com.cacompte.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.LocalIsDarkTheme
import com.cacompte.designsystem.tokens.Radius
import com.cacompte.designsystem.tokens.Space

/** Gouttière recommandée entre deux cartes (charte §3.1/§5.5). */
val CardGutter: Dp = 12.dp

/**
 * Miroir de `Card.swift` : `elev/1`, `radius/md`, padding 16. Bordure 1 dp visible **seulement
 * en mode sombre** — en clair, le fond seul détache suffisamment la carte du fond d'écran ; en
 * sombre, `neutralSurface` (#131822) ne se détache pas assez de `neutralBg` (#0B0E14) sans elle.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(Radius.md)

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(colors.neutralSurface)
                .let {
                    if (isDark) it.border(1.dp, colors.neutralBorder, shape) else it
                }.padding(Space.lg),
        content = { content() },
    )
}
