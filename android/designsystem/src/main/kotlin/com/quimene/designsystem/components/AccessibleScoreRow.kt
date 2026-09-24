package com.quimene.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import java.util.Locale

/**
 * Miroir de `accessibleScoreRow` (`AccessibleScoreRow.swift`) : `mergeDescendants = true`
 * (un seul arrêt TalkBack par ligne), libellé = nom + rang ordinal, valeur = score + delta.
 * Exemple visé : "Alice, deuxième" (contentDescription) / "44 points, +12 cette manche"
 * (stateDescription) — même découpage label/valeur que côté Swift (`accessibilityLabel` +
 * `accessibilityValue`), pas un texte unique concaténé.
 *
 * Swift utilise `NumberFormatter(.ordinal)`, un formateur système sensible à la locale.
 * L'équivalent le plus proche côté Android, `android.icu.text.RuleBasedNumberFormat`, **n'est
 * pas exposé par le SDK public d'Android** (absent du `android.jar` de compilation, vérifié —
 * Android n'embarque pas ce sous-ensemble d'ICU4J). [ordinal] est donc écrit à la main : mots
 * français complets pour les rangs courants (1 à 12, la plage réelle de joueurs, doc 05), repli
 * numérique (« 13e ») au-delà — pas une vraie localisation multi-langue, qui est le travail de
 * l'étape G, pas de cette session.
 */
fun Modifier.accessibleScoreRow(
    name: String,
    score: Int,
    rank: Int? = null,
    delta: Int? = null,
): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = accessibleScoreRowLabel(name, rank)
        stateDescription = accessibleScoreRowValue(score, delta)
    }

internal fun accessibleScoreRowLabel(
    name: String,
    rank: Int?,
    locale: Locale = Locale.getDefault(),
): String {
    if (rank == null) return name
    return "$name, ${ordinal(rank, locale)}"
}

internal fun accessibleScoreRowValue(
    score: Int,
    delta: Int?,
): String {
    val base = "$score points"
    if (delta == null) return base
    val signed = if (delta >= 0) "+$delta" else "$delta"
    return "$base, $signed cette manche"
}

private val frenchOrdinalWords =
    mapOf(
        1 to "premier",
        2 to "deuxième",
        3 to "troisième",
        4 to "quatrième",
        5 to "cinquième",
        6 to "sixième",
        7 to "septième",
        8 to "huitième",
        9 to "neuvième",
        10 to "dixième",
        11 to "onzième",
        12 to "douzième",
    )

private fun ordinal(
    rank: Int,
    locale: Locale,
): String =
    when (locale.language) {
        "en" -> englishOrdinal(rank)
        else -> frenchOrdinalWords[rank] ?: "${rank}e"
    }

private fun englishOrdinal(rank: Int): String {
    val suffix =
        when {
            rank % 100 in 11..13 -> "th"
            rank % 10 == 1 -> "st"
            rank % 10 == 2 -> "nd"
            rank % 10 == 3 -> "rd"
            else -> "th"
        }
    return "$rank$suffix"
}
