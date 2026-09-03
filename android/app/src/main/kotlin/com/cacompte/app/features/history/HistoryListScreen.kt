package com.cacompte.app.features.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.navigation.floatingNavBarContentPadding
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Miroir de `HistoryListView.swift` (doc 06) — liste des parties terminées, tap pour rouvrir le
 * classement final. Filtre par jeu, archivage par ligne (menu plutôt que balayage — aucune
 * convention de balayage ailleurs dans cette app Android), accès aux parties archivées. */
@Composable
fun HistoryListScreen(
    onOpenMatch: (String) -> Unit,
    onOpenArchivedMatches: () -> Unit,
    onOpenSettings: () -> Unit,
    initialGameFilter: String? = null,
) {
    val container = LocalAppContainer.current
    val viewModel =
        rememberViewModel { HistoryViewModel(container.catalog, container.matchRepository, initialGameFilter) }
    val rows = viewModel.rows

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (rows == null) return@Scaffold
        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                message = "Aucune partie terminée pour l'instant.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (viewModel.availableGames.isNotEmpty()) {
                GameFilterBar(viewModel)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = floatingNavBarContentPadding(),
                verticalArrangement = Arrangement.spacedBy(CardGutter),
            ) {
                items(viewModel.filteredRows, key = { it.match.id }) { row ->
                    HistoryRow(
                        row,
                        onClick = { onOpenMatch(row.match.id.toString()) },
                        onArchive = { viewModel.archive(row.match) },
                    )
                }
                if (viewModel.archivedCount > 0) {
                    item {
                        ArchivedMatchesLink(count = viewModel.archivedCount, onClick = onOpenArchivedMatches)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameFilterBar(viewModel: HistoryViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    val label =
        viewModel.availableGames
            .firstOrNull { it.id == viewModel.selectedGameID }
            ?.name
            ?.localized

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)) {
        Chip(
            title = label ?: "Tous les jeux",
            isSelected = label != null,
            onClick = { menuExpanded = true },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Tous les jeux") },
                onClick = {
                    menuExpanded = false
                    viewModel.selectGame(null)
                },
            )
            for (game in viewModel.availableGames) {
                DropdownMenuItem(
                    text = { Text(game.name.localized) },
                    onClick = {
                        menuExpanded = false
                        viewModel.selectGame(game.id)
                    },
                )
            }
        }
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

@Composable
private fun HistoryRow(
    row: HistoryViewModel.Row,
    onClick: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.gameName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    text = dateFormatter.format(row.match.startedAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Filled.Archive, contentDescription = "Archiver cette partie")
            }
        }
    }
}

@Composable
private fun ArchivedMatchesLink(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Parties archivées ($count)", color = colors.textSecondary)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textTertiary)
        }
    }
}
