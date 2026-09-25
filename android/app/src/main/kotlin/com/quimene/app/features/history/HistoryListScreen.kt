package com.quimene.app.features.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.navigation.floatingNavBarContentPadding
import com.quimene.designsystem.components.Chip
import com.quimene.designsystem.components.EmptyState
import com.quimene.designsystem.components.ListContainer
import com.quimene.designsystem.components.ListRowDivider
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.rules.GameDefinition
import kotlinx.coroutines.launch
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
    initialGameFilter: String? = null,
) {
    val container = LocalAppContainer.current
    val viewModel =
        rememberViewModel { HistoryViewModel(container.catalog, container.matchRepository, initialGameFilter) }
    val state by viewModel.uiState.collectAsState()
    val rows = state.rows
    // Doc 16, phase E — les parties jouées par des amis arrivent sans relancer l'app : relève de
    // la boîte aux lettres à l'ouverture, et en tirant la liste vers le bas. La liste observe la
    // base, elle se met à jour d'elle-même.
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { container.sharedProfileSyncCoordinator.sync() } }
    val refresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            try {
                runCatching { container.sharedProfileSyncCoordinator.sync() }
            } finally {
                isRefreshing = false
            }
        }
    }
    val availableGames =
        rows
            .orEmpty()
            .map { it.match.gameID }
            .distinct()
            .mapNotNull { id -> container.catalog.allGames.firstOrNull { it.id == id } }
            .sortedBy { it.name.localized }
    val availablePlayers =
        rows
            .orEmpty()
            .flatMap { it.participants }
            .associate { HistoryViewModel.filterID(it) to it.nicknameSnapshot }
            .toList()
            .sortedBy { (_, name) -> name }
    val filteredRows =
        rows.orEmpty().filter { row ->
            (viewModel.selectedGameID == null || row.match.gameID == viewModel.selectedGameID) &&
                (
                    viewModel.selectedPlayerID == null ||
                        row.participants.any { HistoryViewModel.filterID(it) == viewModel.selectedPlayerID }
                )
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.historique)) },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
            HistoryContent(
                rows = rows,
                filteredRows = filteredRows,
                state = state,
                viewModel = viewModel,
                availableGames = availableGames,
                availablePlayers = availablePlayers,
                innerPadding = innerPadding,
                onOpenMatch = onOpenMatch,
                onOpenArchivedMatches = onOpenArchivedMatches,
            )
        }
    }
}

@Composable
private fun HistoryContent(
    rows: List<HistoryViewModel.Row>?,
    filteredRows: List<HistoryViewModel.Row>,
    state: HistoryViewModel.UiState,
    viewModel: HistoryViewModel,
    availableGames: List<GameDefinition>,
    availablePlayers: List<Pair<HistoryViewModel.PlayerFilterID, String>>,
    innerPadding: PaddingValues,
    onOpenMatch: (String) -> Unit,
    onOpenArchivedMatches: () -> Unit,
) {
    run {
        if (rows == null) return
        if (rows.isEmpty()) {
            // Défilable pour que « tirer pour actualiser » marche aussi sur une liste vide.
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Filled.History,
                    message = "Aucune partie terminée pour l'instant.",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return
        }
        Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            if (availableGames.isNotEmpty() || availablePlayers.isNotEmpty()) {
                FilterBar(viewModel, availableGames, availablePlayers)
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            floatingNavBarContentPadding(systemBottomInset = innerPadding.calculateBottomPadding()),
                        ),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                if (filteredRows.isNotEmpty()) {
                    ListContainer(modifier = Modifier.fillMaxWidth()) {
                        filteredRows.forEachIndexed { index, row ->
                            HistoryRow(
                                row,
                                onClick = { onOpenMatch(row.match.id.toString()) },
                                onArchive = { viewModel.archive(row.match) },
                            )
                            if (index < filteredRows.lastIndex) ListRowDivider()
                        }
                    }
                }
                if (state.archivedCount > 0) {
                    ListContainer(modifier = Modifier.fillMaxWidth()) {
                        ArchivedMatchesLink(count = state.archivedCount, onClick = onOpenArchivedMatches)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    viewModel: HistoryViewModel,
    availableGames: List<GameDefinition>,
    availablePlayers: List<Pair<HistoryViewModel.PlayerFilterID, String>>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (availableGames.isNotEmpty()) {
            GameFilterChip(viewModel, availableGames)
        }
        if (availablePlayers.isNotEmpty()) {
            PlayerFilterChip(viewModel, availablePlayers)
        }
    }
}

@Composable
private fun GameFilterChip(
    viewModel: HistoryViewModel,
    availableGames: List<GameDefinition>,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val label =
        availableGames
            .firstOrNull { it.id == viewModel.selectedGameID }
            ?.name
            ?.localized

    Box {
        Chip(
            title = label ?: stringResource(R.string.tous_les_jeux),
            isSelected = label != null,
            onClick = { menuExpanded = true },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tous_les_jeux)) },
                onClick = {
                    menuExpanded = false
                    viewModel.selectGame(null)
                },
            )
            for (game in availableGames) {
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

@Composable
private fun PlayerFilterChip(
    viewModel: HistoryViewModel,
    availablePlayers: List<Pair<HistoryViewModel.PlayerFilterID, String>>,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val label = availablePlayers.firstOrNull { (id, _) -> id == viewModel.selectedPlayerID }?.second

    Box {
        Chip(
            title = label ?: stringResource(R.string.tous_les_joueurs),
            isSelected = label != null,
            onClick = { menuExpanded = true },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tous_les_joueurs)) },
                onClick = {
                    menuExpanded = false
                    viewModel.selectPlayer(null)
                },
            )
            for ((id, name) in availablePlayers) {
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        menuExpanded = false
                        viewModel.selectPlayer(id)
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

@Composable
private fun ArchivedMatchesLink(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.parties_archivees_count1, count), color = colors.textSecondary)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textTertiary)
    }
}
