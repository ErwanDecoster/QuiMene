package com.quimene.app.features.livematch.tarot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.rememberViewModel
import com.quimene.app.features.livematch.LiveRoundEntryState
import com.quimene.app.features.livematch.ProfileBadge
import com.quimene.app.features.livematch.ProfileBadgeView
import com.quimene.app.navigation.LocalFloatingNavBarHeight
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.CardGutter
import com.quimene.designsystem.components.Chip
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.model.Participant

/** Miroir de `TarotRoundView.swift` — une seule saisie de donne : preneur (+ partenaire à 5
 * joueurs), contrat, points, bouts, poignée, petit au bout, chelem. Les défenseurs ne saisissent
 * rien : leur part est calculée par le moteur (`TarotRulesV1`). */
@Composable
fun TarotRoundScreen(liveMatch: LiveRoundEntryState) {
    val viewModel = rememberViewModel { TarotRoundViewModel(liveMatch) }
    val colors = LocalAppColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            item {
                Text(
                    stringResource(R.string.scores),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }
            items(liveMatch.participants, key = { it.id }) { participant ->
                ParticipantTotalRow(
                    participant,
                    liveMatch.totals[participant.id] ?: 0,
                    liveMatch.profileBadges[participant.id],
                )
            }
            item {
                Text(
                    stringResource(R.string.cette_donne),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.personne_ne_prend),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Switch(checked = viewModel.isPassed, onCheckedChange = viewModel::updatePassed)
                        }

                        if (!viewModel.isPassed) {
                            Text(
                                stringResource(R.string.preneur),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                for (participant in liveMatch.participants) {
                                    Chip(
                                        title = participant.displayName,
                                        isSelected = viewModel.takerID == participant.id,
                                        onClick = { viewModel.selectTaker(participant.id) },
                                    )
                                }
                            }

                            if (viewModel.needsPartner) {
                                Text(
                                    stringResource(R.string.partenaire_roi_appele),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    for (participant in liveMatch.participants) {
                                        if (participant.id == viewModel.takerID) continue
                                        Chip(
                                            title = participant.displayName,
                                            isSelected = viewModel.partnerID == participant.id,
                                            onClick = { viewModel.selectPartner(participant.id) },
                                        )
                                    }
                                }
                            }

                            Text(
                                stringResource(R.string.contrat),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                TarotRoundViewModel.contractLabels.forEachIndexed { index, labelRes ->
                                    Chip(
                                        title = stringResource(labelRes),
                                        isSelected = viewModel.contract == index,
                                        onClick = { viewModel.selectContract(index) },
                                    )
                                }
                            }

                            SteppedValue(
                                label = "Points du preneur",
                                value = viewModel.points,
                                onChange = viewModel::updatePoints,
                            )
                            SteppedValue(
                                label = "Bouts",
                                value = viewModel.bouts,
                                step = 1,
                                onChange = viewModel::updateBouts,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.petit_au_bout),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Switch(checked = viewModel.petitAuBout, onCheckedChange = viewModel::updatePetitAuBout)
                            }

                            Text(
                                stringResource(R.string.poignee),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                TarotRoundViewModel.poigneeLabels.forEachIndexed { index, labelRes ->
                                    Chip(
                                        title = stringResource(labelRes),
                                        isSelected = viewModel.poignee == index,
                                        onClick = { viewModel.selectPoignee(index) },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.chelem_annonce),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Switch(
                                    checked = viewModel.chelemAnnounced,
                                    onCheckedChange = viewModel::updateChelemAnnounced,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.chelem_realise),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Switch(
                                    checked = viewModel.chelemAchieved,
                                    onCheckedChange = viewModel::updateChelemAchieved,
                                )
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            text = stringResource(R.string.valider_la_donne),
            onClick = viewModel::submit,
            modifier = Modifier.padding(Space.lg).padding(bottom = LocalFloatingNavBarHeight.current),
        )
    }
}

@Composable
internal fun SteppedValue(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
) {
    val colors = LocalAppColors.current
    Text("$label : $value", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Chip(title = "−$step", isSelected = false, onClick = { onChange(value - step) })
        Chip(title = "+$step", isSelected = false, onClick = { onChange(value + step) })
    }
}

@Composable
private fun ParticipantTotalRow(
    participant: Participant,
    total: Int,
    badge: ProfileBadge?,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(participant.displayName, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                ProfileBadgeView(badge)
            }
            Text(total.toString(), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        }
    }
}
