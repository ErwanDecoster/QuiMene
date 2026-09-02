package com.cacompte.app.features.players

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.designsystem.components.Avatar
import com.cacompte.designsystem.components.AvatarKind
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.PlayerPalette
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.components.SecondaryButton
import com.cacompte.designsystem.components.TertiaryButton
import com.cacompte.designsystem.components.color
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import java.util.UUID

/** Miroir de `PlayerEditorView.swift` (doc 08) — pseudo + avatar (généré automatiquement,
 * personnalisable), et pour une fiche existante : archiver/désarchiver ou supprimer
 * définitivement (avec confirmation, irréversible). */
@Composable
fun PlayerEditorScreen(
    playerId: String?,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val mode: PlayerEditorViewModel.Mode? =
        if (playerId == null) {
            PlayerEditorViewModel.Mode.Create
        } else {
            val players by container.playerRepository.observeAll().collectAsState(initial = null)
            val player = players?.firstOrNull { it.id == UUID.fromString(playerId) }
            player?.let { PlayerEditorViewModel.Mode.Edit(it) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (playerId == null) "Nouveau joueur" else "Modifier") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (mode == null) return@Scaffold
        PlayerEditorContent(mode, onDone, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun PlayerEditorContent(
    mode: PlayerEditorViewModel.Mode,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { PlayerEditorViewModel(mode, container.playerRepository) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxSize().padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        val avatar =
            Avatar(
                kind =
                    if (viewModel.avatarKind ==
                        "emoji"
                    ) {
                        AvatarKind.Emoji(viewModel.emojiValue)
                    } else {
                        AvatarKind.Symbol("?")
                    },
                palette = PlayerPalette(viewModel.paletteID.toIntOrNull() ?: 1),
            )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AvatarView(avatar, size = AvatarSize.Large)
        }

        OutlinedTextField(
            value = viewModel.nickname,
            onValueChange = viewModel::updateNickname,
            label = { Text("Pseudo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Emoji", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            items(Avatar.curatedEmoji) { emoji ->
                val isSelected = viewModel.avatarKind == "emoji" && viewModel.emojiValue == emoji
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.brandInk.copy(alpha = 0.12f) else colors.neutralFill)
                            .clickable {
                                viewModel.updateAvatarKind("emoji")
                                viewModel.selectEmoji(emoji)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        Text("Couleur", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            items((1..10).toList()) { index ->
                val palette = PlayerPalette(index)
                val isSelected = viewModel.paletteID == index.toString()
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.color())
                            .let { if (isSelected) it.border(2.dp, colors.textPrimary, CircleShape) else it }
                            .clickable { viewModel.selectPalette(index.toString()) },
                )
            }
        }

        if (viewModel.hasManualAvatarOverride) {
            TertiaryButton(text = "Revenir à l'avatar généré", onClick = viewModel::resetToGeneratedAvatar)
        }

        PrimaryButton(
            text = "Enregistrer",
            onClick = { viewModel.save(onDone) },
            enabled = viewModel.canSave,
        )

        if (viewModel.isEditing) {
            SecondaryButton(
                text = if (viewModel.isArchivedPlayer) "Désarchiver" else "Archiver",
                onClick = {
                    if (viewModel.isArchivedPlayer) viewModel.unarchive(onDone) else viewModel.archive(onDone)
                },
            )
            TertiaryButton(text = "Supprimer définitivement", onClick = { showDeleteConfirmation = true })
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Supprimer ce joueur ?") },
            text = {
                Text(
                    "Cette action est définitive. L'historique des parties déjà jouées est conservé, sans le lien vers cette fiche.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onDone) }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Annuler") }
            },
        )
    }
}
