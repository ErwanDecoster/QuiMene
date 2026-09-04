package com.cacompte.app.features.livematch.yams

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cacompte.app.R
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.features.livematch.LiveRoundEntryState
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.model.Participant
import com.cacompte.domain.rules.Category

private val LabelColumnWidth = 132.dp
private val ParticipantColumnWidth = 96.dp

/** Miroir de `YamsSheetView.swift` — feuille de score façon tableur : une colonne par joueur,
 * une ligne par catégorie. Une catégorie déjà remplie s'affiche en lecture seule ; sinon un
 * bouton « + » ouvre une feuille de saisie adaptée au type de la catégorie (nombre de dés / somme
 * / réussi-raté). Le bonus de section haute (+35) est affiché tel que calculé par le moteur,
 * jamais saisi. */
@Composable
fun YamsRoundScreen(liveMatch: LiveRoundEntryState) {
    val viewModel = rememberViewModel { YamsRoundViewModel(liveMatch) }
    val definition = liveMatch.definition
    val categories =
        definition.scoring.entry.categories
            .orEmpty()
    val upperCategories = categories.filter { it.section == Category.Section.Upper }
    val lowerCategories = categories.filter { it.section == Category.Section.Lower }
    val participants = liveMatch.participants

    var pending by remember { mutableStateOf<PendingEntry?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.lg)
                .padding(bottom = LocalFloatingNavBarHeight.current)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
    ) {
        HeaderRow(participants)
        for (category in upperCategories) {
            CategoryRow(category, participants, viewModel) { participant ->
                pending =
                    PendingEntry(participant, category)
            }
        }
        BonusRow(upperCategories, participants, viewModel)
        HorizontalDivider(modifier = Modifier.padding(vertical = Space.sm))
        for (category in lowerCategories) {
            CategoryRow(category, participants, viewModel) { participant ->
                pending =
                    PendingEntry(participant, category)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Space.sm))
        TotalRow(participants, liveMatch)
    }

    pending?.let { entry ->
        YamsCategoryEntrySheet(
            participant = entry.participant,
            category = entry.category,
            onDismiss = { pending = null },
            onSubmit = { rawValue ->
                viewModel.submit(entry.participant.id, entry.category.id, rawValue)
                pending = null
            },
        )
    }
}

private data class PendingEntry(
    val participant: Participant,
    val category: Category,
)

@Composable
private fun HeaderRow(participants: List<Participant>) {
    val colors = LocalAppColors.current
    Row {
        Text("", modifier = Modifier.width(LabelColumnWidth))
        for (participant in participants) {
            Text(
                text = participant.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.width(ParticipantColumnWidth),
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    participants: List<Participant>,
    viewModel: YamsRoundViewModel,
    onOpenEntry: (Participant) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.xxs)) {
        Text(
            text = category.label.localized,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.width(LabelColumnWidth),
        )
        for (participant in participants) {
            val entry = viewModel.filledEntries(participant.id)[category.id]
            Row(modifier = Modifier.width(ParticipantColumnWidth), horizontalArrangement = Arrangement.Center) {
                if (entry != null) {
                    Text(
                        entry.computedValue.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                } else {
                    IconButton(onClick = { onOpenEntry(participant) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = "Remplir ${category.label.localized}",
                            tint = colors.brandInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BonusRow(
    upperCategories: List<Category>,
    participants: List<Participant>,
    viewModel: YamsRoundViewModel,
) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.xxs)) {
        Text(
            text = stringResource(R.string.bonus),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.width(LabelColumnWidth),
        )
        for (participant in participants) {
            val filled = viewModel.filledEntries(participant.id)
            val text =
                if (upperCategories.all { filled[it.id] != null }) {
                    if (upperCategories.any { filled[it.id]?.explanation != null }) {
                        "+35"
                    } else {
                        stringResource(
                            R.string.n_0,
                        )
                    }
                } else {
                    "—"
                }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.width(ParticipantColumnWidth),
            )
        }
    }
}

@Composable
private fun TotalRow(
    participants: List<Participant>,
    liveMatch: LiveRoundEntryState,
) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.xxs)) {
        Text(
            text = stringResource(R.string.total),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.width(LabelColumnWidth),
        )
        for (participant in participants) {
            Text(
                text = (liveMatch.totals[participant.id] ?: 0).toString(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.width(ParticipantColumnWidth),
            )
        }
    }
}

@Composable
private fun YamsCategoryEntrySheet(
    participant: Participant,
    category: Category,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState()
    var diceCount by remember { mutableIntStateOf(0) }
    var sum by remember { mutableIntStateOf(0) }
    var achieved by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(participant.displayName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(category.label.localized, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)

            val rawValue: Int
            when (category.scoring.kind) {
                Category.Scoring.Kind.MultipleOf -> {
                    rawValue = diceCount
                    Text(
                        stringResource(R.string.nombre_de_des_count1, diceCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        for (n in 0..5) {
                            DiceOption(n, isSelected = diceCount == n, onClick = { diceCount = n })
                        }
                    }
                    Text(
                        stringResource(R.string.count1_points, diceCount * (category.scoring.value ?: 0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                Category.Scoring.Kind.SumOfDice -> {
                    rawValue = sum
                    val max = category.scoring.max ?: 30
                    Text(
                        stringResource(R.string.somme_count1, sum),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        DiceOption(0, isSelected = sum == 0, onClick = { sum = 0 })
                        for (n in 5..max) {
                            DiceOption(n, isSelected = sum == n, onClick = { sum = n })
                        }
                    }
                }

                Category.Scoring.Kind.Fixed -> {
                    rawValue = if (achieved) 1 else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (achieved) {
                                stringResource(R.string.obtenu_count1, category.scoring.value ?: 0)
                            } else {
                                stringResource(
                                    R.string.rate_0,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Switch(checked = achieved, onCheckedChange = { achieved = it })
                    }
                }
            }

            PrimaryButton(text = stringResource(R.string.valider), onClick = { onSubmit(rawValue) })
        }
    }
}

@Composable
private fun DiceOption(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Chip(title = value.toString(), isSelected = isSelected, onClick = onClick)
}
