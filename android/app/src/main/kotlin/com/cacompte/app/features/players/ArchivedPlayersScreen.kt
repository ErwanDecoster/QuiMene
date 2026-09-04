package com.cacompte.app.features.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cacompte.app.R
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.navigation.floatingNavBarContentPadding
import com.cacompte.app.ui.toAvatar
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.components.TertiaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.PlayerEntity

/** Miroir de `ArchivedPlayersView.swift` (doc 06) — écran séparé plutôt qu'une section toujours
 * visible dans la liste des joueurs : un cas d'usage occasionnel. Chaque ligne ouvre le profil
 * (mêmes statistiques que pour un joueur actif) ; « Réactiver » et la suppression définitive
 * vivent à côté, pas dans le profil. */
@Composable
fun ArchivedPlayersScreen(
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { ArchivedPlayersViewModel(container.playerRepository) }
    val archived by viewModel.archived.collectAsState()
    var playerPendingDeletion by remember { mutableStateOf<PlayerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.joueurs_archives)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        val currentArchived = archived
        if (currentArchived == null) return@Scaffold
        if (currentArchived.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Group,
                message = stringResource(R.string.aucun_joueur_archive),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = floatingNavBarContentPadding(systemBottomInset = innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(currentArchived, key = { it.id }) { player ->
                ArchivedPlayerRow(
                    player = player,
                    onOpenProfile = { onOpenProfile(player.id.toString()) },
                    onUnarchive = { viewModel.unarchive(player) },
                    onDelete = { playerPendingDeletion = player },
                )
            }
        }
    }

    playerPendingDeletion?.let { player ->
        AlertDialog(
            onDismissRequest = { playerPendingDeletion = null },
            title = { Text(stringResource(R.string.supprimer_definitivement_ce_joueur)) },
            text = {
                Text(stringResource(R.string.la_fiche_joueur_sera_definitivement_supprimee_les_parties))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playerPendingDeletion = null
                        viewModel.delete(player)
                    },
                ) { Text(stringResource(R.string.supprimer)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { playerPendingDeletion = null },
                ) { Text(stringResource(R.string.annuler)) }
            },
        )
    }
}

@Composable
private fun ArchivedPlayerRow(
    player: PlayerEntity,
    onOpenProfile: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                AvatarView(player.toAvatar(), size = AvatarSize.Medium)
                Text(player.nickname, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
            TertiaryButton(text = stringResource(R.string.reactiver), onClick = onUnarchive)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer définitivement ${player.nickname}")
            }
        }
    }
}
