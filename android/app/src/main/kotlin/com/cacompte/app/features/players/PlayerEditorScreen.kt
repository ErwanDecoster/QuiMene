package com.cacompte.app.features.players

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.features.join.QrScannerView
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.app.profilesharing.ProfileShareLink
import com.cacompte.designsystem.components.Avatar
import com.cacompte.designsystem.components.AvatarKind
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.PlayerPalette
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.components.QrCodeView
import com.cacompte.designsystem.components.SecondaryButton
import com.cacompte.designsystem.components.TertiaryButton
import com.cacompte.designsystem.components.color
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
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
            label = { Text("Pseudo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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
            SharedProfileSection(viewModel)

            if (viewModel.isArchivedPlayer) {
                TertiaryButton(text = "Supprimer ce joueur", onClick = { showDeleteConfirmation = true })
            } else {
                SecondaryButton(text = "Archiver ce joueur", onClick = { viewModel.archive(onDone) })
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Supprimer définitivement ce joueur ?") },
            text = {
                Text(
                    "La fiche joueur sera définitivement supprimée. Les parties déjà jouées restent dans " +
                        "l'historique, mais ne pointeront plus vers ce joueur. Cette action ne peut pas être annulée.",
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

/** Miroir de la section « Profil partagé » de `PlayerEditorView.swift` (doc 14) — trois états
 * mutuellement exclusifs : cette fiche EST le profil partagé de l'appareil (QR affiché), cette
 * fiche SUIT un ami (lié par scan), ou aucun des deux (les deux choix restent possibles). */
@Composable
private fun SharedProfileSection(viewModel: PlayerEditorViewModel) {
    val colors = LocalAppColors.current
    var isPresentingScanner by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text("Profil partagé", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)

        val shareURL = viewModel.shareURL
        val linkedName = viewModel.linkedProfileName
        val linkedDate = viewModel.linkedProfileDate
        when {
            shareURL != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    QrCodeView(content = shareURL, modifier = Modifier.size(160.dp))
                    Text(
                        "Fais scanner ce code par l'ami avec qui tu veux partager ton historique, depuis sa " +
                            "propre fiche « Suivre un profil reçu ».",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                SecondaryButton(text = "Ne plus partager", onClick = viewModel::unlinkProfile)
            }
            linkedName != null -> {
                Text(
                    "Tu suis **$linkedName**" +
                        (linkedDate?.let { ", depuis le ${dateFormatter.format(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                SecondaryButton(
                    text = "Suivre quelqu'un d'autre (remplace $linkedName)",
                    onClick = { isPresentingScanner = true },
                )
                SecondaryButton(text = "Ne plus suivre ce profil", onClick = viewModel::unlinkProfile)
            }
            else -> {
                SecondaryButton(text = "Partager ce profil (c'est moi)", onClick = viewModel::ensureSharedProfileID)
                viewModel.shareConflictMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.semanticError)
                }
                SecondaryButton(text = "Suivre un profil reçu", onClick = { isPresentingScanner = true })
            }
        }

        Text(
            "Partage ta fiche pour que tes amis puissent te suivre. Suis un ami pour retrouver vos parties " +
                "jouées ensemble dans son historique.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }

    if (isPresentingScanner) {
        ProfileLinkDialog(
            currentlyLinkedID = viewModel.sharedProfileID.takeUnless { viewModel.isMyOwnSharedProfile },
            currentlyLinkedName = viewModel.linkedProfileName,
            conflictingPlayerName = viewModel::conflictingPlayerName,
            onConfirm = { payload, adoptNameAndAvatar ->
                viewModel.linkProfile(
                    id = payload.id,
                    name = payload.name,
                    scannedAvatarKind = payload.avatarKind,
                    scannedAvatarValue = payload.avatarValue,
                    scannedPaletteID = payload.paletteID,
                    adoptNameAndAvatar = adoptNameAndAvatar,
                )
            },
            onDismiss = { isPresentingScanner = false },
        )
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofLocalizedDate(
            FormatStyle.MEDIUM,
        ).withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

/** Miroir de `ProfileLinkScanFlow.swift` — une seule présentation plein écran dont le contenu
 * bascule en interne entre scan et confirmation, plutôt que deux présentations système
 * successives (remontée Apple : la transition entre les deux traînait sensiblement). */
@Composable
private fun ProfileLinkDialog(
    currentlyLinkedID: UUID?,
    currentlyLinkedName: String?,
    conflictingPlayerName: suspend (UUID) -> String?,
    onConfirm: (ProfileShareLink.Payload, adoptNameAndAvatar: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var scannedPayload by remember { mutableStateOf<ProfileShareLink.Payload?>(null) }
    val payload = scannedPayload

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (payload != null) {
            var conflictName by remember(payload.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(payload.id) { conflictName = conflictingPlayerName(payload.id) }
            ConfirmProfileLinkScreen(
                payload = payload,
                conflictingPlayerName = conflictName,
                existingLinkName = if (payload.id == currentlyLinkedID) null else currentlyLinkedName,
                onConfirm = { adopt ->
                    onConfirm(payload, adopt)
                    onDismiss()
                },
                onCancel = onDismiss,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                QrScannerView(
                    onScan = { raw ->
                        if (scannedPayload ==
                            null
                        ) {
                            ProfileShareLink.parse(raw)?.let { scannedPayload = it }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(Space.lg)) {
                    Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }
        }
    }
}

/** Miroir de `ConfirmProfileLinkView.swift` — dernier moment où un scan malencontreux peut
 * encore être rattrapé : montre qui le lien prétend représenter avant d'écrire quoi que ce soit. */
@Composable
private fun ConfirmProfileLinkScreen(
    payload: ProfileShareLink.Payload,
    conflictingPlayerName: String?,
    existingLinkName: String?,
    onConfirm: (adoptNameAndAvatar: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    var adoptNameAndAvatar by remember { mutableStateOf(true) }
    var isLinking by remember { mutableStateOf(false) }
    val canAdoptAvatar = payload.avatarKind != "photo"
    val confirmButtonTitle =
        when {
            existingLinkName != null -> "Remplacer le lien"
            conflictingPlayerName != null -> "Suivre quand même"
            else -> "Suivre"
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suivre ce profil ?") },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !isLinking) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Annuler")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Space.lg)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (canAdoptAvatar) {
                    AvatarView(
                        Avatar(
                            kind = AvatarKind.Emoji(payload.avatarValue),
                            palette = PlayerPalette(payload.paletteID.toIntOrNull() ?: 1),
                        ),
                        size = AvatarSize.Large,
                    )
                }
                Text(
                    payload.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }

            existingLinkName?.let {
                Text(
                    "Cette fiche suit actuellement $it. Continuer la fera suivre ${payload.name} à la place : " +
                        "$it ne recevra plus les parties jouées avec cette fiche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.semanticError,
                )
            }
            conflictingPlayerName?.let {
                Text(
                    "Ce profil est déjà suivi par la fiche « $it » sur cet appareil. Continuer le fera suivre " +
                        "aussi par celle-ci — à ne faire que si c'est la même personne (par exemple une fiche recréée).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.semanticError,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Switch(checked = adoptNameAndAvatar, onCheckedChange = { adoptNameAndAvatar = it })
                Text("Adopter aussi son pseudo et son avatar", color = colors.textPrimary)
            }
            if (!canAdoptAvatar) {
                Text(
                    "Son avatar est une photo : seul le pseudo peut être repris.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }

            if (isLinking) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                PrimaryButton(
                    text = confirmButtonTitle,
                    onClick = {
                        isLinking = true
                        onConfirm(adoptNameAndAvatar)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
