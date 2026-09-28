package com.quimene.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Formats qui suivent la langue de l'app (`LocalConfiguration`), y compris un changement de langue
// sans redémarrage — contrairement à un formateur construit une fois avec `Locale.getDefault()`.
// Miroir des `.formatted(...)` de Foundation côté Apple.

/** Taux arrondi à l'unité, au format de la langue (« 45 % » en français, « 45% » en anglais). */
@Composable
fun formatPercent(fraction: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getPercentInstance(locale) }.format(fraction)
}

/** Date au format moyen de la langue (« 25 sept. 2026 », « Sep 25, 2026 »). */
@Composable
fun rememberMediumDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }
}
