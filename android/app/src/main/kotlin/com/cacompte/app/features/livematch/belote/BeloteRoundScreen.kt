package com.cacompte.app.features.livematch.belote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cacompte.app.R
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.features.livematch.LiveRoundEntryState
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.model.Participant

/** Miroir de `BeloteRoundView.swift` — une donne = équipe preneuse, points marqués, capot,
 * belote-rebelote. Les défenseurs ne saisissent rien : leur score (162 − points du preneur) est
 * calculé par le moteur. */
@Composable
fun BeloteRoundScreen(liveMatch: LiveRoundEntryState) {
    val viewModel = rememberViewModel { BeloteRoundViewModel(liveMatch) }
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
            items(viewModel.teams) { teamID ->
                TeamScoreRow(teamID, teamLabel(teamID, liveMatch.participants), teamTotal(teamID, liveMatch))
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
                        Text(
                            stringResource(R.string.equipe_preneuse),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            for (teamID in viewModel.teams) {
                                Chip(
                                    title = teamLabel(teamID, liveMatch.participants),
                                    isSelected = viewModel.takerTeamID == teamID,
                                    onClick = { viewModel.selectTaker(teamID) },
                                )
                            }
                        }

                        Text(
                            stringResource(R.string.points_du_preneur_count1, viewModel.takerPoints),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Chip(
                                title = "−10",
                                isSelected = false,
                                onClick = { viewModel.updateTakerPoints(viewModel.takerPoints - 10) },
                            )
                            Chip(
                                title = "−1",
                                isSelected = false,
                                onClick = { viewModel.updateTakerPoints(viewModel.takerPoints - 1) },
                            )
                            Chip(
                                title = "+1",
                                isSelected = false,
                                onClick = { viewModel.updateTakerPoints(viewModel.takerPoints + 1) },
                            )
                            Chip(
                                title = "+10",
                                isSelected = false,
                                onClick = { viewModel.updateTakerPoints(viewModel.takerPoints + 10) },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.capot),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Switch(checked = viewModel.isCapot, onCheckedChange = viewModel::updateCapot)
                        }

                        Text(
                            stringResource(R.string.belote_rebelote),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Chip(
                                title = stringResource(R.string.aucune),
                                isSelected = viewModel.beloteRebeloteTeamID == null,
                                onClick = { viewModel.selectBeloteRebelote(null) },
                            )
                            for (teamID in viewModel.teams) {
                                Chip(
                                    title = teamLabel(teamID, liveMatch.participants),
                                    isSelected = viewModel.beloteRebeloteTeamID == teamID,
                                    onClick = { viewModel.selectBeloteRebelote(teamID) },
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
private fun TeamScoreRow(
    teamID: String,
    label: String,
    total: Int,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(
                total.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.wrapContentWidth(),
            )
        }
    }
}

private fun teamLabel(
    teamID: String,
    participants: List<Participant>,
): String = participants.filter { it.teamID == teamID }.joinToString(" & ") { it.displayName }

/** Chaque coéquipier reçoit la même entrée à chaque donne ([BeloteRulesV1] la duplique) — le
 * total d'un seul membre de l'équipe suffit. */
private fun teamTotal(
    teamID: String,
    liveMatch: LiveRoundEntryState,
): Int {
    val representative = liveMatch.participants.firstOrNull { it.teamID == teamID } ?: return 0
    return liveMatch.totals[representative.id] ?: 0
}
