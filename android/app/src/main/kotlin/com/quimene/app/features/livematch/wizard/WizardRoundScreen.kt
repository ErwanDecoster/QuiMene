package com.quimene.app.features.livematch.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.quimene.app.features.livematch.tarot.SteppedValue
import com.quimene.app.navigation.LocalFloatingNavBarHeight
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.CardGutter
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.model.Participant

/** Miroir de `WizardRoundView.swift` — annonce puis résultat par joueur, dans le même écran (pas
 * de flux en deux temps). Un bandeau compare le total des plis réalisés au numéro de la manche —
 * indication visuelle seulement, `WizardRulesV1.validate` reste le seul verrou réel. */
@Composable
fun WizardRoundScreen(liveMatch: LiveRoundEntryState) {
    val viewModel = rememberViewModel { WizardRoundViewModel(liveMatch) }
    val colors = LocalAppColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            item {
                val matches = viewModel.totalTricks == viewModel.roundNumber
                Card(modifier = Modifier.fillMaxWidth().padding(top = Space.lg)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.plis_distribues),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            stringResource(R.string.count1_count2, viewModel.totalTricks, viewModel.roundNumber),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (matches) colors.semanticSuccess else colors.semanticError,
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.manche_count1, viewModel.roundNumber),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }
            items(liveMatch.participants, key = { it.id }) { participant ->
                ParticipantBidRow(participant, viewModel, liveMatch.profileBadges[participant.id])
            }
        }
        PrimaryButton(
            text = stringResource(R.string.valider_la_manche),
            onClick = viewModel::submit,
            modifier = Modifier.padding(Space.lg).padding(bottom = LocalFloatingNavBarHeight.current),
        )
    }
}

@Composable
private fun ParticipantBidRow(
    participant: Participant,
    viewModel: WizardRoundViewModel,
    badge: ProfileBadge?,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(participant.displayName, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                ProfileBadgeView(badge)
            }
            SteppedValue(
                label = "Annonce",
                value = viewModel.bid(participant.id),
                onChange = { viewModel.setBid(participant.id, it) },
            )
            SteppedValue(
                label = "Réalisé",
                value = viewModel.result(participant.id),
                onChange = { viewModel.setResult(participant.id, it) },
            )
        }
    }
}
