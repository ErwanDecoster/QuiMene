package com.cacompte.app.features.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cacompte.app.features.livematch.RoundEntryDispatch
import com.cacompte.app.livesync.SharedMatchViewModel
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space

/**
 * Vue d'une partie rejointe — miroir de `SharedMatchView.swift`. Contributeur : mêmes écrans de
 * saisie que l'hôte ([RoundEntryDispatch], partagés via [com.cacompte.app.features.livematch.LiveRoundEntryState]).
 * Observateur : classement en lecture seule, aucune saisie affichée.
 */
@Composable
fun SharedMatchScreen(
    viewModel: SharedMatchViewModel,
    onQuit: () -> Unit,
    onReconnect: () -> Unit,
) {
    val colors = LocalAppColors.current
    val state = viewModel.stateOrNull

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.let { viewModel.definition.name.localized } ?: "Connexion…") },
                actions = { TextButton(onClick = onQuit) { Text("Quitter") } },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!viewModel.isHostConnected) {
                Banner(
                    message = "Connexion à l'hôte perdue.",
                    modifier = Modifier.padding(Space.lg),
                    actionTitle = "Réessayer",
                    onAction = onReconnect,
                )
            }
            viewModel.roundExplanationMessage?.let { Banner(message = it, modifier = Modifier.padding(Space.lg)) }
            viewModel.latestRejectionReason?.let {
                Banner(message = "Manche refusée par l'hôte : $it", modifier = Modifier.padding(horizontal = Space.lg))
            }
            viewModel.validationErrorMessage?.let {
                Banner(message = it, modifier = Modifier.padding(horizontal = Space.lg))
            }

            if (state == null) return@Column

            StandingsSection(viewModel)

            if (viewModel.canPropose) {
                RoundEntryDispatch(viewModel) { gameName ->
                    Text(
                        "La saisie dédiée de $gameName n'est pas encore disponible en tant que contributeur.",
                        modifier = Modifier.padding(Space.lg),
                    )
                }
            } else {
                Text(
                    "Tu observes cette partie : la saisie se fait sur l'appareil de l'hôte ou d'un contributeur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Space.lg),
                )
            }
        }
    }
}

@Composable
private fun StandingsSection(viewModel: SharedMatchViewModel) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        for (standing in viewModel.currentStandings) {
            val participant = viewModel.participants.firstOrNull { it.id == standing.participantID } ?: continue
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(participant.displayName, color = colors.textPrimary)
                Text("${standing.score}", color = colors.textSecondary)
            }
        }
    }
}
