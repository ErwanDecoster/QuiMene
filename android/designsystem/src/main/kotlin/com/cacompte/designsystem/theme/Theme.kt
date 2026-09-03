package com.cacompte.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.cacompte.designsystem.tokens.AppColors
import com.cacompte.designsystem.tokens.CaCompteTypography
import com.cacompte.designsystem.tokens.DarkAppColors
import com.cacompte.designsystem.tokens.LightAppColors
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.LocalIsDarkTheme
import com.cacompte.designsystem.tokens.appColorScheme
import com.cacompte.designsystem.tokens.dynamicAppColors

/**
 * Thème racine de l'app — sur Android 12+ (API 31, `Build.VERSION_CODES.S`), suit les couleurs
 * dynamiques du téléphone (Material You, dérivées du fond d'écran) plutôt que la palette de
 * marque fixe ; en dessous, ou si le système ne les expose pas, repli sur les tokens de charte
 * (`docs/07-charte-graphique.md`, [appColorScheme]/[LightAppColors]/[DarkAppColors]). Les
 * couleurs sémantiques et la palette des 10 joueurs restent toujours fixes, même en mode
 * dynamique (voir [dynamicAppColors]). Fournit [LocalAppColors] pour les tokens sans rôle M3
 * direct (palette joueurs, `brandInkPressed`, etc.). Suit le thème clair/sombre système, avec
 * une bascule manuelle prévue plus tard (réglages) — jamais forcé (charte §8).
 */
@Composable
fun CaCompteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme
    val appColors: AppColors
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        appColors = dynamicAppColors(colorScheme, dark = darkTheme)
    } else {
        appColors = if (darkTheme) DarkAppColors else LightAppColors
        colorScheme = appColorScheme(appColors, dark = darkTheme)
    }

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
