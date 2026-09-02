package com.cacompte.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.cacompte.designsystem.tokens.CaCompteTypography
import com.cacompte.designsystem.tokens.DarkAppColors
import com.cacompte.designsystem.tokens.LightAppColors
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.LocalIsDarkTheme
import com.cacompte.designsystem.tokens.appColorScheme

/**
 * Thème racine de l'app — câble le `ColorScheme` Material 3 (dérivé des tokens de charte, pas de
 * `dynamicColor`), la typographie, et fournit [LocalAppColors] pour les tokens sans rôle M3
 * direct (palette joueurs, `brandInkPressed`, etc.). Suit le thème système, avec une bascule
 * manuelle prévue plus tard (réglages) — jamais forcé (charte §8).
 */
@Composable
fun CaCompteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = appColorScheme(appColors, dark = darkTheme)

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CaCompteTypography,
            content = content,
        )
    }
}
