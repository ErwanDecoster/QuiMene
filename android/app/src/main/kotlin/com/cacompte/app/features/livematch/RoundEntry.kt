package com.cacompte.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cacompte.app.features.livematch.belote.BeloteRoundScreen
import com.cacompte.app.features.livematch.tarot.TarotRoundScreen
import com.cacompte.app.features.livematch.wizard.WizardRoundScreen
import com.cacompte.app.features.livematch.yams.YamsRoundScreen
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.app.ui.toAvatar
import com.cacompte.catalog.games.BeloteRulesV1
import com.cacompte.catalog.games.TarotRulesV1
import com.cacompte.catalog.games.WizardRulesV1
import com.cacompte.catalog.games.YamsRulesV1
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.model.Participant
import com.cacompte.domain.rules.EntryKind
import com.cacompte.store.ParticipantEntity
import java.util.UUID

/**
 * Dispatch sur la bonne forme de saisie de manche selon le moteur du jeu — miroir de
 * `LiveMatchView.swift`, partagé entre l'hôte ([LiveMatchScreen]) et le contributeur
 * ([com.cacompte.app.features.join.JoinScreen]) : [source] est un [LiveMatchViewModel] pour l'un,
 * un `SharedMatchViewModel` pour l'autre — ni les écrans dédiés ni [GenericRoundEntry] ne
 * connaissent la différence (voir [LiveRoundEntryState]).
 */
@Composable
fun RoundEntryDispatch(
    source: LiveRoundEntryState,
    snapshotsByParticipant: Map<UUID, ParticipantEntity> = emptyMap(),
    onUnsupported: @Composable (gameName: String) -> Unit,
) {
    when (source.definition.engine) {
        BeloteRulesV1.ENGINE_ID -> BeloteRoundScreen(source)
        TarotRulesV1.ENGINE_ID -> TarotRoundScreen(source)
        WizardRulesV1.ENGINE_ID -> WizardRoundScreen(source)
        YamsRulesV1.ENGINE_ID -> YamsRoundScreen(source)
        else ->
            if (source.definition.scoring.entry.kind == EntryKind.Integer) {
                GenericRoundEntry(source, snapshotsByParticipant)
            } else {
                onUnsupported(source.definition.name.localized)
            }
    }
}

@Composable
fun GenericRoundEntry(
    source: LiveRoundEntryState,
    snapshotsByParticipant: Map<UUID, ParticipantEntity> = emptyMap(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(source.participants, key = { it.id }) { participant ->
                ParticipantScoreRow(
                    participant = participant,
                    snapshot = snapshotsByParticipant[participant.id],
                    total = source.totals[participant.id] ?: 0,
                    pendingValue = source.pendingScores[participant.id],
                    requiresCloserSelection = source.requiresCloserSelection,
                    isCloser = source.closedParticipantID == participant.id,
                    onScoreChange = { raw ->
                        val value = raw.toIntOrNull()
                        if (raw.isEmpty()) {
                            source.clearScore(participant.id)
                        } else if (value != null) {
                            source.setScore(participant.id, value)
                        }
                    },
                    onToggleCloser = {
                        source.closedParticipantID =
                            if (source.closedParticipantID == participant.id) null else participant.id
                    },
                    onFocus = { source.focus(participant.id) },
                )
            }
        }

        PrimaryButton(
            text = "Valider la manche",
            onClick = { source.commitRound() },
            modifier = Modifier.padding(Space.lg).padding(bottom = LocalFloatingNavBarHeight.current),
        )
    }
}

@Composable
private fun ParticipantScoreRow(
    participant: Participant,
    snapshot: ParticipantEntity?,
    total: Int,
    pendingValue: Int?,
    requiresCloserSelection: Boolean,
    isCloser: Boolean,
    onScoreChange: (String) -> Unit,
    onToggleCloser: () -> Unit,
    onFocus: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            if (snapshot != null) {
                AvatarView(snapshot.toAvatar(), size = AvatarSize.Small)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(participant.displayName, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text("Total : $total", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            if (requiresCloserSelection) {
                Chip(title = "Ferme", isSelected = isCloser, onClick = onToggleCloser)
            }
            OutlinedTextField(
                value = pendingValue?.toString() ?: "",
                onValueChange = onScoreChange,
                modifier =
                    Modifier.width(96.dp).onFocusChanged { focusState ->
                        if (focusState.isFocused) onFocus()
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
