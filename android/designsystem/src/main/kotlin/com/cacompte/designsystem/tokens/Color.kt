package com.cacompte.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tous les tokens de couleur de la charte (docs/07-charte-graphique.md §1 et §14), valeurs hex
 * identiques à `Color+Tokens.swift`/`Assets.xcassets`. Source unique — aucune couleur littérale
 * ailleurs dans le module (charte §13, règle 1).
 */
data class AppColors(
    val brandInk: Color,
    val brandInkPressed: Color,
    val brandBrass: Color,
    val brandTeal: Color,
    val neutralBg: Color,
    val neutralSurface: Color,
    val neutralSunken: Color,
    val neutralFill: Color,
    val neutralBorder: Color,
    val neutralBorderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val semanticSuccess: Color,
    val semanticError: Color,
    val semanticWarning: Color,
    val semanticInfo: Color,
    /** Libellé sur `brandInk` — blanc en clair, `neutralBg` en sombre (jamais blanc pur sur fond
     * clair `brandInk` sombre, charte §5.3). */
    val onBrandInk: Color,
    private val players: List<Color>,
    private val playersHighContrast: List<Color>,
) {
    /** Couleur du joueur 1..10 (charte §1.5). */
    fun player(index: Int): Color {
        require(index in 1..10) { "player index must be in 1..10, was $index" }
        return players[index - 1]
    }

    /** Variante haute lisibilité du joueur 1..10 — pas encore câblée à un réglage (étape G). */
    fun playerHighContrast(index: Int): Color {
        require(index in 1..10) { "player index must be in 1..10, was $index" }
        return playersHighContrast[index - 1]
    }
}

val LightAppColors =
    AppColors(
        brandInk = Color(0xFF1F4899),
        brandInkPressed = Color(0xFF173A80),
        brandBrass = Color(0xFF9A5B00),
        brandTeal = Color(0xFF0A6A72),
        neutralBg = Color(0xFFF6F7F9),
        neutralSurface = Color(0xFFFFFFFF),
        neutralSunken = Color(0xFFECEEF2),
        neutralFill = Color(0xFFE3E6EC),
        neutralBorder = Color(0xFFD4D9E2),
        neutralBorderStrong = Color(0xFF7E879A),
        textPrimary = Color(0xFF161B24),
        textSecondary = Color(0xFF4A5260),
        textTertiary = Color(0xFF5A6373),
        textDisabled = Color(0xFF9AA3B2),
        semanticSuccess = Color(0xFF0F6B43),
        semanticError = Color(0xFFBC2418),
        semanticWarning = Color(0xFF8A5300),
        semanticInfo = Color(0xFF1B5FA6),
        onBrandInk = Color(0xFFFFFFFF),
        players =
            listOf(
                Color(0xFF0066FF), // 1 Azur
                Color(0xFFD97300), // 2 Ambre
                Color(0xFF00A550), // 3 Émeraude
                Color(0xFFD6006D), // 4 Magenta
                Color(0xFF254EAF), // 5 Ardoise
                Color(0xFF00A0B4), // 6 Cyan
                Color(0xFFFF2903), // 7 Vermillon
                Color(0xFF7220FF), // 8 Violet
                Color(0xFF6E8C00), // 9 Olive
                Color(0xFFFF165F), // 10 Rose
            ),
        playersHighContrast =
            listOf(
                Color(0xFF0066FF), // 1 identique
                Color(0xFFB56000), // 2
                Color(0xFF008641), // 3
                Color(0xFFD6006D), // 4 identique
                Color(0xFF1B4AB9), // 5
                Color(0xFF008090), // 6
                Color(0xFFE32200), // 7
                Color(0xFF7220FF), // 8 identique
                Color(0xFF627D00), // 9
                Color(0xFFEC004A), // 10
            ),
    )

val DarkAppColors =
    AppColors(
        brandInk = Color(0xFF8FB2FF),
        brandInkPressed = Color(0xFFA9C3FF),
        brandBrass = Color(0xFFF0A73F),
        brandTeal = Color(0xFF4CC7D1),
        neutralBg = Color(0xFF0B0E14),
        neutralSurface = Color(0xFF131822),
        neutralSunken = Color(0xFF090C11),
        neutralFill = Color(0xFF1D2432),
        neutralBorder = Color(0xFF2B3342),
        neutralBorderStrong = Color(0xFF69738A),
        textPrimary = Color(0xFFEDF0F5),
        textSecondary = Color(0xFFBAC2D0),
        textTertiary = Color(0xFF98A2B4),
        textDisabled = Color(0xFF5A6478),
        semanticSuccess = Color(0xFF41CE92),
        semanticError = Color(0xFFFF8A7A),
        semanticWarning = Color(0xFFFFC161),
        semanticInfo = Color(0xFF79B6F7),
        onBrandInk = Color(0xFF0B0E14),
        players =
            listOf(
                Color(0xFF5C9CFF), // 1
                Color(0xFFFFA733), // 2
                Color(0xFF0BFF81), // 3
                Color(0xFFFF5CB8), // 4
                Color(0xFF89A0E6), // 5
                Color(0xFF1CE8FF), // 6
                Color(0xFFFF7A5C), // 7
                Color(0xFFB18CFF), // 8
                Color(0xFFD5FF24), // 9
                Color(0xFFFF7BAA), // 10
            ),
        playersHighContrast =
            listOf(
                Color(0xFF5C9CFF), // 1 identique
                Color(0xFFFFA733), // 2 identique
                Color(0xFF0BFF81), // 3 identique
                Color(0xFFFF5CB8), // 4 identique
                Color(0xFF829CED), // 5
                Color(0xFF1CE8FF), // 6 identique
                Color(0xFFFF7A5C), // 7 identique
                Color(0xFFB18CFF), // 8 identique
                Color(0xFFD5FF24), // 9 identique
                Color(0xFFFF7BAA), // 10 identique
            ),
    )

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Thème sombre effectivement résolu par [com.cacompte.designsystem.theme.CaCompteTheme] — à
 * lire plutôt que de rappeler `isSystemInDarkTheme()` dans chaque composant, pour rester
 * cohérent avec une éventuelle bascule manuelle (clair/sombre/système, charte §8). */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * ColorScheme Material 3 dérivé des mêmes tokens — repli utilisé quand la couleur dynamique
 * (Material You, Android 12+) n'est pas disponible ou désactivée par le système ; voir
 * [dynamicAppColors] et `CaCompteTheme` pour le cas dynamique, aujourd'hui le cas par défaut.
 *
 * Les rôles `surfaceContainer*` (élévation, charte §5.2) n'ont pas de hex distinct documenté par
 * la charte au-delà de `neutral/surface` — celle-ci ne donne que les ombres L1-L4. En attendant
 * une valeur de charte dédiée, les quatre rôles réutilisent `neutralSurface` et laissent
 * l'ombre Material (portée par les composants, pas par ce ColorScheme) porter la différence
 * visuelle entre paliers.
 */
fun appColorScheme(
    colors: AppColors,
    dark: Boolean,
): ColorScheme =
    // Deux appels directs plutôt qu'une référence de fonction stockée (`::darkColorScheme`) :
    // appeler une factory M3 via une valeur de type fonction perd les noms de paramètres et les
    // valeurs par défaut (règle du langage Kotlin), ce qui obligerait à fournir tous les rôles.
    if (dark) {
        darkColorScheme(
            primary = colors.brandInk,
            onPrimary = colors.onBrandInk,
            primaryContainer = colors.brandInk,
            onPrimaryContainer = colors.onBrandInk,
            secondary = colors.brandTeal,
            onSecondary = colors.onBrandInk,
            tertiary = colors.brandBrass,
            onTertiary = colors.onBrandInk,
            error = colors.semanticError,
            onError = colors.onBrandInk,
            background = colors.neutralBg,
            onBackground = colors.textPrimary,
            surface = colors.neutralSurface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.neutralFill,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.neutralBg,
            surfaceContainerLow = colors.neutralSurface,
            surfaceContainer = colors.neutralSurface,
            surfaceContainerHigh = colors.neutralSurface,
            surfaceContainerHighest = colors.neutralSurface,
            outline = colors.neutralBorderStrong,
            outlineVariant = colors.neutralBorder,
        )
    } else {
        lightColorScheme(
            primary = colors.brandInk,
            onPrimary = colors.onBrandInk,
            primaryContainer = colors.brandInk,
            onPrimaryContainer = colors.onBrandInk,
            secondary = colors.brandTeal,
            onSecondary = colors.onBrandInk,
            tertiary = colors.brandBrass,
            onTertiary = colors.onBrandInk,
            error = colors.semanticError,
            onError = colors.onBrandInk,
            background = colors.neutralBg,
            onBackground = colors.textPrimary,
            surface = colors.neutralSurface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.neutralFill,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.neutralBg,
            surfaceContainerLow = colors.neutralSurface,
            surfaceContainer = colors.neutralSurface,
            surfaceContainerHigh = colors.neutralSurface,
            surfaceContainerHighest = colors.neutralSurface,
            outline = colors.neutralBorderStrong,
            outlineVariant = colors.neutralBorder,
        )
    }

/**
 * [AppColors] dérivé du `ColorScheme` dynamique du téléphone (Material You, Android 12+,
 * `dynamicLightColorScheme`/`dynamicDarkColorScheme`) — utilisé par `CaCompteTheme` quand le
 * système le permet, à la place des tokens fixes de charte. Les couleurs sémantiques
 * (succès/erreur/avertissement/info) et la palette des 10 joueurs restent **toujours** celles de
 * [LightAppColors]/[DarkAppColors] : un statut ou l'identité visuelle d'un joueur (hash FNV-1a,
 * doit rester stable et distinguable) n'a pas de raison de dériver du fond d'écran de la
 * personne qui tient le téléphone.
 */
fun dynamicAppColors(
    scheme: ColorScheme,
    dark: Boolean,
): AppColors {
    val fixed = if (dark) DarkAppColors else LightAppColors
    return AppColors(
        brandInk = scheme.primary,
        brandInkPressed = scheme.primaryContainer,
        brandBrass = scheme.tertiary,
        brandTeal = scheme.secondary,
        neutralBg = scheme.background,
        neutralSurface = scheme.surface,
        neutralSunken = scheme.surfaceContainerLowest,
        neutralFill = scheme.surfaceVariant,
        neutralBorder = scheme.outlineVariant,
        neutralBorderStrong = scheme.outline,
        textPrimary = scheme.onBackground,
        textSecondary = scheme.onSurfaceVariant,
        textTertiary = scheme.onSurfaceVariant,
        textDisabled = scheme.onSurface.copy(alpha = 0.38f),
        semanticSuccess = fixed.semanticSuccess,
        semanticError = fixed.semanticError,
        semanticWarning = fixed.semanticWarning,
        semanticInfo = fixed.semanticInfo,
        onBrandInk = scheme.onPrimary,
        players = List(10) { fixed.player(it + 1) },
        playersHighContrast = List(10) { fixed.playerHighContrast(it + 1) },
    )
}
