package com.quimene.app.features.players

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.navigation.LocalFloatingNavBarHeight
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.PlayerPalette
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.components.SecondaryButton
import com.quimene.designsystem.components.TertiaryButton
import com.quimene.designsystem.components.color
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/** Miroir de `PlayerEditorView.swift` (doc 08) — pseudo + avatar (généré automatiquement,
 * personnalisable), et pour une fiche existante : archiver/désarchiver ou supprimer
 * définitivement (avec confirmation, irréversible). */
@Composable
fun PlayerEditorScreen(
    playerId: String?,
    onDone: () -> Unit,
    isCreatingProfile: Boolean = false,
) {
    val container = LocalAppContainer.current
    val mode: PlayerEditorViewModel.Mode? =
        if (isCreatingProfile) {
            PlayerEditorViewModel.Mode.CreateProfile
        } else if (playerId == null) {
            PlayerEditorViewModel.Mode.Create
        } else {
            val players by container.playerRepository.observeAll().collectAsState(initial = null)
            val player = players?.firstOrNull { it.id == UUID.fromString(playerId) }
            player?.let { PlayerEditorViewModel.Mode.Edit(it) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isCreatingProfile -> stringResource(R.string.creer_mon_profil)
                            playerId == null -> "Nouveau joueur"
                            else -> stringResource(R.string.modifier)
                        },
                    )
                },
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
    val nicknameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Doc utilisateur — remontée : à la création d'un joueur, le champ de saisie du pseudo
    // doit déjà être prêt à recevoir la frappe, comme sur Apple. Pas au moment de modifier un
    // joueur existant : ouvrirait le clavier sans y avoir été invité, pour une fiche déjà remplie.
    LaunchedEffect(Unit) {
        if (!viewModel.isEditing) {
            nicknameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Space.lg)
                .padding(bottom = LocalFloatingNavBarHeight.current)
                .verticalScroll(rememberScrollState()),
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
            label = { Text(stringResource(R.string.pseudo)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().focusRequester(nicknameFocusRequester),
        )

        Text(stringResource(R.string.emoji), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
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
            TertiaryButton(
                text = stringResource(R.string.reinitialiser_l_avatar_genere),
                onClick = viewModel::resetToGeneratedAvatar,
            )
        }

        PrimaryButton(
            text = stringResource(R.string.enregistrer),
            onClick = { viewModel.save(onDone) },
            enabled = viewModel.canSave,
        )

        // Doc 16, phase A — ajouter un ami se fait depuis l'onglet Profil ; mon propre profil
        // se gère sur sa page et ne s'archive ni ne se supprime comme une fiche ordinaire.
        if (viewModel.isEditing && !viewModel.isMyOwnSharedProfile) {
            FriendSection(viewModel)

            if (viewModel.isArchivedPlayer) {
                TertiaryButton(text = stringResource(R.string.supprimer_ce_joueur), onClick = {
                    showDeleteConfirmation =
                        true
                })
            } else {
                SecondaryButton(
                    text = stringResource(R.string.archiver_ce_joueur),
                    onClick = { viewModel.archive(onDone) },
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.supprimer_definitivement_ce_joueur)) },
            text = {
                Text(stringResource(R.string.la_fiche_joueur_sera_definitivement_supprimee_les_parties))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onDone) }) { Text(stringResource(R.string.supprimer)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.annuler)) }
            },
        )
    }
}

/** Doc 16, phase A — miroir de la section « Ami » de `PlayerEditorView.swift` : seul reste ici le
 * lien d'un ami déjà ajouté, pour pouvoir le retirer. */
@Composable
private fun FriendSection(viewModel: PlayerEditorViewModel) {
    val colors = LocalAppColors.current
    val linkedName = viewModel.linkedProfileName
    if (viewModel.sharedProfileID == null) return

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            stringResource(R.string.ami),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        if (linkedName != null) {
            val linkedDate = viewModel.linkedProfileDate
            Text(
                linkedDate?.let {
                    stringResource(
                        R.string.lie_au_profil_de_value1_depuis_le_value2,
                        linkedName,
                        dateFormatter.format(it),
                    )
                } ?: stringResource(R.string.lie_au_profil_de_value1, linkedName),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        SecondaryButton(text = stringResource(R.string.retirer_des_amis), onClick = viewModel::unlinkProfile)
        Text(
            stringResource(R.string.vos_prochaines_parties_jouees_ensemble_n_arriveront_plus),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofLocalizedDate(
            FormatStyle.MEDIUM,
        ).withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
