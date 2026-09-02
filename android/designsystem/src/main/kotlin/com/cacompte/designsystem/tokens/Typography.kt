package com.cacompte.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Échelle Material 3 (charte §2.2, colonne Android) — pas un report des tailles iOS. La
 * hiérarchie est partagée avec `Font+Tokens.swift`, la mesure ne l'est pas : chaque plateforme
 * ressemble à elle-même. `letterSpacing` est posé explicitement (contrairement à iOS, où SF Pro
 * l'applique automatiquement). `fontFamily` reste `null` (Roboto système par défaut) — aucune
 * police embarquée.
 */
val CaCompteTypography =
    Typography(
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold), // h1
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold), // h2
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold), // h3
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold), // h4
        titleMedium =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
            ),
        // h5
        titleSmall =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.10.sp,
            ),
        // h6
        bodyLarge =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.50.sp,
            ),
        // body
        bodyMedium =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.25.sp,
            ),
        // bodySmall
        bodySmall =
            TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.40.sp,
            ),
        // caption
        labelLarge =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.10.sp,
            ),
        // button
        labelMedium =
            TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.50.sp,
            ),
        // label
        labelSmall =
            TextStyle(
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.50.sp,
            ),
        // caption2
    )

/**
 * Chiffres de score (charte §2.3) — le seul écart typographique assumé : chasse fixe
 * (`FontFeature.tabularNums`) pour que le tableau ne tressaute pas latéralement. Pas de police
 * "rounded" côté Android — la charte n'en demande pas (doc 11, tableau d'équivalences : Roboto +
 * `tabularFigures`, contrairement à SF Pro Rounded côté iOS).
 */
object ScoreTypography {
    // "tnum" = tag OpenType des chiffres à chasse fixe (tabular figures) — API stable
    // TextStyle.fontFeatureSettings (chaîne CSS-like), pas de classe FontFeature typée requise.
    private const val TABULAR_FIGURES = "tnum"

    val scoreXL: TextStyle =
        TextStyle(
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        )
    val scoreL: TextStyle =
        TextStyle(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        )
    val scoreM: TextStyle =
        TextStyle(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = TABULAR_FIGURES,
        )
}
