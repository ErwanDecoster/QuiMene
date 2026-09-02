package com.cacompte.app.features.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.ui.toColor
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.rules.GameDefinition

/** Miroir de `GamesCatalogView.swift` (doc 05) — la liste des jeux du catalogue, chacun ouvrant
 * la mise en place d'une partie. Pas de recherche/filtre à cette étape : ~20 jeux tiennent sans
 * défilement excessif sur un écran de téléphone. */
@Composable
fun GamesCatalogScreen(onGameSelected: (String) -> Unit) {
    val catalog = LocalAppContainer.current.catalog
    val games = catalog.allGames.sortedBy { it.name.localized }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Jouer") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(games, key = { it.id }) { definition ->
                GameRow(definition, onClick = { onGameSelected(definition.id) })
            }
        }
    }
}

@Composable
private fun GameRow(
    definition: GameDefinition,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(definition.paletteID.toColor()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        definition.name.localized
                            .take(1)
                            .uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBrandInk,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.name.localized,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = playerCountLabel(definition),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

private fun playerCountLabel(definition: GameDefinition): String {
    val players = definition.players
    return if (players.min == players.max) {
        "${players.min} joueurs"
    } else {
        "${players.min}–${players.max} joueurs"
    }
}
