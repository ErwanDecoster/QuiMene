package com.quimene.app.features.livematch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.ui.gameIcon
import com.quimene.catalog.games.TarotRulesV1
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.rules.EntryKind
import com.quimene.domain.rules.GameDefinition
import java.text.Collator

/**
 * Doc 16, phase C — miroir de `NextMatchPicker.swift` : « Partie suivante » d'une session en ligne,
 * proposée à tous (créateur et participants) sur l'écran de résultats. Mêmes joueurs ; seuls les
 * jeux à saisie de score simple, les seuls jouables à plusieurs appareils aujourd'hui, et
 * compatibles avec le nombre de joueurs. Le jeu qui vient de se terminer est proposé en tête.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextMatchPicker(
    playerCount: Int,
    currentGameID: String,
    onDismiss: () -> Unit,
    onPick: (GameDefinition) -> Unit,
) {
    val colors = LocalAppColors.current
    val catalog = LocalAppContainer.current.catalog
    val games =
        remember(playerCount, currentGameID) {
            val collator = Collator.getInstance()
            catalog.allGames
                .filter { isShareable(it) && playerCount in it.players.min..it.players.max }
                .sortedWith(
                    compareBy<GameDefinition> { it.id != currentGameID }
                        .thenComparator { lhs, rhs -> collator.compare(lhs.name.localized, rhs.name.localized) },
                )
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Space.lg)) {
            Text(
                stringResource(R.string.partie_suivante),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )
            LazyColumn {
                items(games, key = { it.id }) { definition ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(definition)
                                    onDismiss()
                                }.padding(horizontal = Space.lg, vertical = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(gameIcon(definition.id), contentDescription = null, tint = colors.brandInk)
                        Text(
                            definition.name.localized,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (definition.id == currentGameID) {
                            Text(
                                stringResource(R.string.rejouer),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.avec_les_memes_joueurs_tous_les_appareils_de_la_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                    )
                }
            }
        }
    }
}

private fun isShareable(definition: GameDefinition): Boolean =
    definition.engine != TarotRulesV1.ENGINE_ID &&
        (definition.scoring.entry.kind == EntryKind.Integer || definition.scoring.entry.kind == EntryKind.Rank)

/** Bouton « Partie suivante » posé sous l'écran de résultats d'une partie partagée. */
@Composable
fun NextMatchBar(
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PrimaryButton(
        text = stringResource(R.string.partie_suivante),
        onClick = onClick,
        enabled = !isBusy,
        isLoading = isBusy,
        modifier = modifier.fillMaxWidth(),
    )
}
