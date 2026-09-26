package com.quimene.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.domain.stats.Insight
import com.quimene.domain.stats.InsightID
import java.text.NumberFormat
import java.util.UUID

/** Ce que l'écran de résultats affiche pour un fait marquant. */
class InsightPresentation(
    val headline: String,
    val detail: String,
    val icon: ImageVector,
)

/**
 * Titre, texte et icône rédigés ici à partir de [Insight.id] et [Insight.value] : `StatsEngine`
 * (`:domain`) ne produit que des valeurs (doc 06), l'app les met en mots dans la langue de
 * l'utilisateur. `null` pour un fait que cette version de l'app ne sait pas présenter. Miroir de
 * `Insight+Presentation.swift` côté Apple.
 */
@Composable
fun Insight.presentation(nameOf: (UUID?) -> String): InsightPresentation? {
    val single = value as? Insight.Value.Single
    return when (id) {
        InsightID.highestRoundScore ->
            single?.let {
                InsightPresentation(
                    stringResource(R.string.plus_gros_tour),
                    roundScoreDetail(it, nameOf),
                    Icons.Filled.LocalFireDepartment,
                )
            }
        InsightID.bestRoundScore ->
            single?.let {
                InsightPresentation(
                    stringResource(R.string.meilleur_tour),
                    roundScoreDetail(it, nameOf),
                    Icons.Filled.Star,
                )
            }
        InsightID.mostRegular ->
            single?.let {
                InsightPresentation(
                    stringResource(R.string.le_metronome),
                    deviationDetail(it, nameOf),
                    Icons.Filled.Timer,
                )
            }
        InsightID.mostIrregular ->
            single?.let {
                InsightPresentation(
                    stringResource(R.string.les_montagnes_russes),
                    deviationDetail(it, nameOf),
                    Icons.AutoMirrored.Filled.TrendingUp,
                )
            }
        InsightID.finalGap ->
            single?.let {
                InsightPresentation(
                    stringResource(R.string.ecart_final),
                    it.value.toInt().let { gap ->
                        pluralStringResource(R.plurals.count1_points_entre_le_premier_et_le_deuxieme, gap, gap)
                    },
                    Icons.Filled.SwapHoriz,
                )
            }
        InsightID.leadChanges ->
            single?.let {
                val changes = it.value.toInt()
                InsightPresentation(
                    stringResource(R.string.changements_de_tete),
                    if (changes == 0) {
                        stringResource(R.string.domination_du_debut_a_la_fin)
                    } else {
                        pluralStringResource(R.plurals.count1_changement_s_de_leader, changes, changes)
                    },
                    Icons.AutoMirrored.Filled.CompareArrows,
                )
            }
        InsightID.longestLeadStreak ->
            single?.let {
                InsightPresentation(
                    stringResource(R.string.plus_longue_serie_en_tete),
                    it.value.toInt().let { streak ->
                        pluralStringResource(
                            R.plurals.value1_count2_manche_s_d_affilee,
                            streak,
                            nameOf(it.participantID),
                            streak,
                        )
                    },
                    Icons.Filled.EmojiEvents,
                )
            }
        InsightID.roundsClosed ->
            InsightPresentation(
                stringResource(R.string.manches_fermees),
                stringResource(R.string.repartition_des_fermetures_de_manche),
                Icons.Filled.Lock,
            )
        InsightID.doublingsSuffered ->
            InsightPresentation(
                stringResource(R.string.doublements_subis),
                stringResource(R.string.repartition_des_scores_doubles),
                Icons.Filled.HighlightOff,
            )
        else -> null
    }
}

@Composable
private fun roundScoreDetail(
    value: Insight.Value.Single,
    nameOf: (UUID?) -> String,
): String {
    val points = value.value.toInt()
    return pluralStringResource(
        R.plurals.value1_count2_points_manche_count3,
        points,
        nameOf(value.participantID),
        points,
        (value.round ?: 0) + 1,
    )
}

@Composable
private fun deviationDetail(
    value: Insight.Value.Single,
    nameOf: (UUID?) -> String,
): String {
    val format =
        NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    return stringResource(R.string.value1_ecart_type_value2, nameOf(value.participantID), format.format(value.value))
}
