package com.cacompte.app.features.players

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.cacompte.app.navigation.floatingNavBarContentPadding
import com.cacompte.app.ui.toAvatar
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.PlayerEntity

/** Miroir de `PlayersListView.swift` (doc 06) — actifs puis archivés, chacun ouvrant la fiche
 * d'édition (archiver/supprimer y vivent, pas ici — une seule action par ligne, charte §13). */
@Composable
fun PlayersListScreen(
    onAddPlayer: () -> Unit,
    onEditPlayer: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { PlayersListViewModel(container.playerRepository, container.appSettings) }
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Joueurs") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPlayer) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un joueur")
            }
        },
    ) { innerPadding ->
        if (state.active.isEmpty() && state.archived.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                message = "Aucun joueur pour l'instant — ajoutez le premier pour commencer une partie.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                actionTitle = "Ajouter un joueur",
                onAction = onAddPlayer,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = floatingNavBarContentPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(state.active, key = { it.id }) { player ->
                PlayerRow(player, onClick = { onEditPlayer(player.id.toString()) })
            }
            if (state.archived.isNotEmpty()) {
                item {
                    Text(
                        text = "Archivés",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs),
                    )
                }
                items(state.archived, key = { it.id }) { player ->
                    PlayerRow(player, onClick = { onEditPlayer(player.id.toString()) })
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerEntity,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            AvatarView(player.toAvatar(), size = AvatarSize.Medium)
            Text(
                text = player.nickname,
                style = MaterialTheme.typography.bodyLarge,
                color = if (player.isArchived) colors.textTertiary else colors.textPrimary,
            )
        }
    }
}
