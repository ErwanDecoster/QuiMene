package com.cacompte.app.features.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
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
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.app.navigation.floatingNavBarContentPadding
import com.cacompte.app.ui.toAvatar
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.components.ListContainer
import com.cacompte.designsystem.components.ListRowDivider
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.PlayerEntity

/** Miroir de `PlayersListView.swift` (doc 06) — une ligne ouvre le profil (statistiques), jamais
 * directement l'éditeur (atteint depuis le profil). Mode sélection contextuel (icône dédiée dans
 * la barre de titre, pas d'`EditButton` — équivalent Android d'Apple) pour archiver plusieurs
 * fiches à la fois ; les archivés vivent sur [ArchivedPlayersScreen], un lien en bas de liste. */
@Composable
fun PlayersListScreen(
    onAddPlayer: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenArchivedPlayers: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { PlayersListViewModel(container.playerRepository, container.appSettings) }
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current

    Scaffold(
        topBar = {
            if (viewModel.isSelecting) {
                TopAppBar(
                    title = { Text("${viewModel.selectedPlayerIDs.size} sélectionné(s)") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::toggleSelectionMode) {
                            Icon(Icons.Filled.Close, contentDescription = "Annuler la sélection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::archiveSelected,
                            enabled = viewModel.selectedPlayerIDs.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archiver la sélection")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Joueurs") },
                    actions = {
                        if (state.active.isNotEmpty()) {
                            IconButton(onClick = viewModel::toggleSelectionMode) {
                                Icon(Icons.Filled.Checklist, contentDescription = "Sélectionner des joueurs")
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!viewModel.isSelecting) {
                FloatingActionButton(
                    onClick = onAddPlayer,
                    // Scaffold place déjà le FAB en tenant compte de systemBarsForVisualComponents
                    // (ScaffoldDefaults.contentWindowInsets) — ajouter à nouveau WindowInsets
                    // .navigationBars ici double-compterait l'inset système et ferait flotter le
                    // bouton trop haut. Seule la hauteur de l'îlot flottant (pas un inset) manque.
                    modifier = Modifier.padding(bottom = LocalFloatingNavBarHeight.current + Space.sm),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter un joueur")
                }
            }
        },
    ) { innerPadding ->
        if (state.active.isEmpty() && state.archivedCount == 0) {
            EmptyState(
                icon = Icons.Filled.Add,
                message = "Aucun joueur pour l'instant — ajoutez le premier pour commencer une partie.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                actionTitle = "Ajouter un joueur",
                onAction = onAddPlayer,
            )
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(floatingNavBarContentPadding(systemBottomInset = innerPadding.calculateBottomPadding())),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            if (state.active.isNotEmpty()) {
                ListContainer(modifier = Modifier.fillMaxWidth()) {
                    state.active.forEachIndexed { index, player ->
                        PlayerRow(
                            player = player,
                            isSelecting = viewModel.isSelecting,
                            isSelected = player.id in viewModel.selectedPlayerIDs,
                            onClick = {
                                if (viewModel.isSelecting) {
                                    viewModel.toggleSelected(player.id)
                                } else {
                                    onOpenProfile(player.id.toString())
                                }
                            },
                        )
                        if (index < state.active.lastIndex) ListRowDivider()
                    }
                }
            }
            if (state.archivedCount > 0 && !viewModel.isSelecting) {
                ListContainer(modifier = Modifier.fillMaxWidth()) {
                    ArchivedPlayersLink(count = state.archivedCount, onClick = onOpenArchivedPlayers)
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerEntity,
    isSelecting: Boolean,
    isSelected: Boolean,
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
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (isSelecting) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        }
        AvatarView(player.toAvatar(), size = AvatarSize.Medium)
        Text(player.nickname, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
    }
}

@Composable
private fun ArchivedPlayersLink(
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
        Text("Joueurs archivés ($count)", color = colors.textSecondary)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textTertiary)
    }
}
