package com.cacompte.app.features.play

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.ui.gameIcon
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.tokens.IconSize
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.rules.GameDefinition

/** Miroir de `GamesTabView.swift` (doc 05) — la liste des jeux du catalogue : icône, nom,
 * description courte. Chaque ligne ouvre la mise en place d'une partie ; l'icône de trophée
 * ouvre son classement (remplace l'action de balayage « Meilleurs joueurs » d'iOS, sans
 * équivalent standard côté Android). */
@Composable
fun GamesCatalogScreen(
    onGameSelected: (String) -> Unit,
    onOpenLeaderboard: (String) -> Unit,
) {
    val catalog = LocalAppContainer.current.catalog
    val games = catalog.allGames.sortedBy { it.name.localized }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Jeux") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(games, key = { it.id }) { definition ->
                GameRow(
                    definition = definition,
                    onClick = { onGameSelected(definition.id) },
                    onOpenLeaderboard = { onOpenLeaderboard(definition.id) },
                )
            }
        }
    }
}

@Composable
private fun GameRow(
    definition: GameDefinition,
    onClick: () -> Unit,
    onOpenLeaderboard: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = gameIcon(definition.id),
                    contentDescription = null,
                    tint = colors.brandInk,
                    modifier = Modifier.size(IconSize.lg),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.name.localized,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                definition.shortDescription?.localized?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            IconButton(onClick = onOpenLeaderboard) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = "Classement de ${definition.name.localized}")
            }
        }
    }
}
