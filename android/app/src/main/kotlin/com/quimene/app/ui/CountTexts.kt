package com.quimene.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.quimene.app.R

// Phrases qui accordent deux nombres (« 1 partie · 3 victoires ») : Android n'accorde qu'une
// quantité par ressource, chaque nombre est donc un `<plurals>` inséré dans un modèle
// (`Scripts/extract-android-strings.py`, substitutions du catalogue Apple).

/** « 12 parties · 5 victoires ». */
@Composable
fun matchesAndWinsText(
    played: Int,
    wins: Int,
): String =
    stringResource(
        R.string.count1_partie_s_count2_victoire_s,
        pluralStringResource(R.plurals.count1_partie_s_count2_victoire_s_parties, played, played),
        pluralStringResource(R.plurals.count1_partie_s_count2_victoire_s_victoires, wins, wins),
    )

/** « 12 parties · 5 victoires · 42 % ». */
@Composable
fun matchesWinsAndRateText(
    played: Int,
    wins: Int,
    rate: String,
): String =
    stringResource(
        R.string.count1_partie_s_count2_victoire_s_value3,
        pluralStringResource(R.plurals.count1_partie_s_count2_victoire_s_value3_parties, played, played),
        pluralStringResource(R.plurals.count1_partie_s_count2_victoire_s_value3_victoires, wins, wins),
        rate,
    )
