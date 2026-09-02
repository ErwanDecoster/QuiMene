package com.cacompte.app.features.leaderboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.rules.GameDefinition

/** Miroir de `LeaderboardView.swift` (doc 06) — un jeu par ligne, tap pour son classement. */
@Composable
fun LeaderboardScreen(onOpenGame: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { LeaderboardListViewModel(container.catalog, container.matchRepository) }
    val games by viewModel.games.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Classements") }) }) { innerPadding ->
        val currentGames = games
        if (currentGames == null) return@Scaffold
        if (currentGames.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.EmojiEvents,
                message = "Aucun classement pour l'instant — terminez une partie pour en voir un apparaître ici.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(currentGames, key = { it.id }) { definition ->
                GameLeaderboardRow(definition, onClick = { onOpenGame(definition.id) })
            }
        }
    }
}

@Composable
private fun GameLeaderboardRow(
    definition: GameDefinition,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(definition.name.localized, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
    }
}
