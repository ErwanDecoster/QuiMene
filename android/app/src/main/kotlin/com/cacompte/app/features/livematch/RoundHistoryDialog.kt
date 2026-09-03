package com.cacompte.app.features.livematch

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.model.ScoreEntry

/**
 * « Voir les manches précédentes » en cours de partie — miroir de `RoundHistoryView.swift`.
 * Grille manche × participant, ordonnée par siège (pas par classement, qui change à chaque
 * manche) — voir [LiveRoundEntryState].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RoundHistoryDialog(
    source: LiveRoundEntryState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val colors = LocalAppColors.current
    val participants = source.participants
    val rounds = source.rounds.sortedBy { it.index }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(Space.lg)) {
            Text("Manches précédentes", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

            if (rounds.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    message = "Aucune manche jouée pour l'instant.",
                    modifier = Modifier.padding(top = Space.lg),
                )
            } else {
                Card(modifier = Modifier.padding(top = Space.lg)) {
                    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                            Text("", modifier = Modifier.width(24.dp))
                            for (participant in participants) {
                                Text(
                                    participant.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.textSecondary,
                                    modifier = Modifier.width(96.dp),
                                )
                            }
                        }
                        for (round in rounds) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.lg),
                                modifier = Modifier.padding(top = Space.xs),
                            ) {
                                Text(
                                    "${round.index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.textTertiary,
                                    modifier = Modifier.width(24.dp),
                                )
                                for (participant in participants) {
                                    val entry = round.entries.firstOrNull { it.participantID == participant.id }
                                    RoundEntryCell(entry, colors.textPrimary, colors.textTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundEntryCell(
    entry: ScoreEntry?,
    primaryColor: Color,
    tertiaryColor: Color,
) {
    if (entry == null) {
        Text("—", style = MaterialTheme.typography.bodySmall, color = tertiaryColor, modifier = Modifier.width(96.dp))
        return
    }
    val text =
        if (entry.rawValue == entry.computedValue) {
            "${entry.computedValue}"
        } else {
            "${entry.rawValue} → ${entry.computedValue}"
        }
    Text(text, style = MaterialTheme.typography.bodySmall, color = primaryColor, modifier = Modifier.width(96.dp))
}
