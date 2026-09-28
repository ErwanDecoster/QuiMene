package com.quimene.app.features.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.navigation.floatingNavBarContentPadding
import com.quimene.app.ui.formatPercent
import com.quimene.app.ui.matchesAndWinsText
import com.quimene.app.ui.toAvatar
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.BackButton
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.CardGutter
import com.quimene.designsystem.components.EmptyState
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.store.LeaderboardEntry

/** Miroir de `GameLeaderboardView.swift` (doc 06) — classé par taux de victoire, départagé par
 * rang normalisé moyen (voir [LeaderboardEntry]). */
@Composable
fun GameLeaderboardScreen(
    gameId: String,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val definitionName =
        container.catalog.allGames
            .firstOrNull { it.id == gameId }
            ?.name
            ?.localized ?: gameId
    val viewModel = rememberViewModel { GameLeaderboardViewModel(gameId, container.leaderboardRepository) }
    val entries by viewModel.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(definitionName) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                actions = {
                    IconButton(onClick = { onOpenHistory(gameId) }) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.historique))
                    }
                },
            )
        },
    ) { innerPadding ->
        val currentEntries = entries
        if (currentEntries == null) return@Scaffold
        if (currentEntries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.EmojiEvents,
                message = stringResource(R.string.aucune_partie_de_value1_terminee_pour_l_instant_le, definitionName),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = floatingNavBarContentPadding(systemBottomInset = innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            itemsIndexed(currentEntries) { index, entry ->
                LeaderboardRow(rank = index + 1, entry = entry)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
) {
    val colors = LocalAppColors.current
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
                modifier = Modifier.width(40.dp),
            )
            AvatarView(entry.toAvatar(), size = AvatarSize.Small)
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    text = matchesAndWinsText(entry.played, entry.wins),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = formatPercent(entry.winRate),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
    }
}
