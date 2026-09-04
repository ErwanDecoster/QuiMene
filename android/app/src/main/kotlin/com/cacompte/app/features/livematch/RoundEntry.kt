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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cacompte.app.R
import com.cacompte.app.features.livematch.belote.BeloteRoundScreen
import com.cacompte.app.features.livematch.tarot.TarotRoundScreen
import com.cacompte.app.features.livematch.wizard.WizardRoundScreen
import com.cacompte.app.features.livematch.yams.YamsRoundScreen
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.catalog.games.BeloteRulesV1
import com.cacompte.catalog.games.TarotRulesV1
import com.cacompte.catalog.games.WizardRulesV1
import com.cacompte.catalog.games.YamsRulesV1
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.ScoreTypography
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.model.Participant
import com.cacompte.domain.rules.EntryKind

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
    onUnsupported: @Composable (gameName: String) -> Unit,
) {
    when (source.definition.engine) {
        BeloteRulesV1.ENGINE_ID -> BeloteRoundScreen(source)
        TarotRulesV1.ENGINE_ID -> TarotRoundScreen(source)
        WizardRulesV1.ENGINE_ID -> WizardRoundScreen(source)
        YamsRulesV1.ENGINE_ID -> YamsRoundScreen(source)
        else ->
            if (source.definition.scoring.entry.kind == EntryKind.Integer) {
                GenericRoundEntry(source)
            } else {
                onUnsupported(source.definition.name.localized)
            }
    }
}

@Composable
fun GenericRoundEntry(source: LiveRoundEntryState) {
    val rankByParticipant = source.currentStandings.associate { it.participantID to it.rank }
    // Miroir de `ScoreBoardView.rankedParticipants` — la liste elle-même est triée par
    // classement (l'ordre des sièges ne sert qu'à départager une égalité), pas seulement le
    // numéro affiché sur chaque ligne : remontée utilisateur, le numéro de rang affiché ne
    // correspondait pas à la position dans la liste tant que celle-ci restait triée par siège.
    val rankedParticipants =
        source.participants.sortedWith(
            compareBy({ rankByParticipant[it.id] ?: Int.MAX_VALUE }, { it.seatIndex }),
        )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(rankedParticipants, key = { it.id }) { participant ->
                ParticipantScoreRow(
                    participant = participant,
                    rank = rankByParticipant[participant.id],
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
            text = stringResource(R.string.valider_la_manche),
            onClick = { source.commitRound() },
            modifier = Modifier.padding(Space.lg).padding(bottom = LocalFloatingNavBarHeight.current),
        )
    }
}

/** Miroir de `ScoreBoardView.swift` : rang, nom, total (grand format, sans étiquette « Total »),
 * fermeture de manche selon le jeu, saisie — dans cet ordre, tout tenant sur une seule ligne
 * (doc utilisateur). Pas d'avatar ici, comme côté Apple : la place gagnée est ce qui garantit que
 * le pseudo reste lisible même à 6+ joueurs. */
@Composable
private fun ParticipantScoreRow(
    participant: Participant,
    rank: Int?,
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
            Text(
                text = rank?.toString() ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.width(20.dp),
            )
            Text(
                text = participant.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = total.toString(), style = ScoreTypography.scoreL, color = colors.textSecondary)
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
                textStyle = ScoreTypography.scoreM,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
