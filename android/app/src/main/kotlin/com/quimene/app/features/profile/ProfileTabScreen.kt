package com.quimene.app.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.features.join.QrScannerView
import com.quimene.app.features.players.PlayerEditorScreen
import com.quimene.app.navigation.floatingNavBarContentPadding
import com.quimene.app.profilesharing.ProfileShareLink
import com.quimene.app.ui.toAvatar
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.ListContainer
import com.quimene.designsystem.components.ListRowDivider
import com.quimene.designsystem.components.PlayerPalette
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.components.QrCodeView
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.store.PlayerEntity
import kotlinx.coroutines.launch

/**
 * Miroir de `ProfileTabView.swift` (doc 16, phase A) — onglet Profil. « Mon profil » est la fiche
 * joueur que cet appareil désigne comme la sienne ([PlayerEntity.sharedProfileIsMine]) : pas de
 * modèle à part, l'utilisateur reste un joueur comme les autres dans ses propres parties.
 */
@Composable
fun ProfileTabScreen(
    onEditProfile: (String) -> Unit,
    onOpenStats: (String) -> Unit,
    onShowMyQr: () -> Unit,
    onJoin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val players by container.playerRepository.observeAll().collectAsState(initial = emptyList())
    val me = players.firstOrNull { it.sharedProfileIsMine }
    val friends = players.filter { it.sharedProfileID != null && !it.sharedProfileIsMine && !it.isArchived }
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var isAddingFriend by remember { mutableStateOf(false) }
    var isConfirmingDeletion by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.profil)) }) }) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(floatingNavBarContentPadding(systemBottomInset = innerPadding.calculateBottomPadding())),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            if (me != null) {
                ListContainer(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Space.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.lg),
                    ) {
                        AvatarView(me.toAvatar(), size = AvatarSize.Large)
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            Text(
                                me.nickname,
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.textPrimary,
                            )
                            TextButton(onClick = { onEditProfile(me.id.toString()) }) {
                                Text(stringResource(R.string.modifier_le_profil))
                            }
                        }
                    }
                    ListRowDivider()
                    NavRow(Icons.Filled.QrCode, stringResource(R.string.m_ajouter_comme_ami), onShowMyQr)
                    ListRowDivider()
                    NavRow(Icons.Filled.BarChart, stringResource(R.string.mes_statistiques)) {
                        onOpenStats(me.id.toString())
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        stringResource(R.string.amis),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.lg),
                    )
                    ListContainer(modifier = Modifier.fillMaxWidth()) {
                        if (friends.isEmpty()) {
                            Text(
                                stringResource(R.string.aucun_ami_lie_pour_l_instant),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                            )
                        } else {
                            friends.forEachIndexed { index, friend ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenStats(friend.id.toString()) }
                                            .padding(horizontal = Space.lg, vertical = Space.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                                ) {
                                    AvatarView(friend.toAvatar(), size = AvatarSize.Small)
                                    Text(
                                        friend.nickname,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textPrimary,
                                    )
                                }
                                if (index < friends.lastIndex) ListRowDivider()
                            }
                        }
                        ListRowDivider()
                        NavRow(
                            Icons.Filled.PersonAdd,
                            stringResource(R.string.ajouter_un_ami),
                        ) { isAddingFriend = true }
                    }
                    Text(
                        stringResource(R.string.vos_parties_jouees_ensemble_apparaissent_dans_vos_deux),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = Space.lg),
                    )
                }
            }

            ListContainer(modifier = Modifier.fillMaxWidth()) {
                NavRow(Icons.Filled.QrCodeScanner, stringResource(R.string.rejoindre_une_partie), onJoin)
                ListRowDivider()
                NavRow(Icons.Filled.Settings, stringResource(R.string.reglages), onOpenSettings)
            }

            if (me != null) {
                TextButton(
                    onClick = { isConfirmingDeletion = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.supprimer_mon_profil), color = colors.semanticError)
                }
            }
        }
    }

    if (isAddingFriend) {
        AddFriendDialog(players = players, onDismiss = { isAddingFriend = false })
    }

    if (isConfirmingDeletion && me != null) {
        AlertDialog(
            onDismissRequest = { isConfirmingDeletion = false },
            title = { Text(stringResource(R.string.supprimer_ton_profil)) },
            text = { Text(stringResource(R.string.tes_amis_ne_recevront_plus_les_parties_jouees_avec_toi_ta)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingDeletion = false
                    scope.launch { container.playerRepository.unlinkSharedProfile(me) }
                }) { Text(stringResource(R.string.supprimer_mon_profil), color = colors.semanticError) }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingDeletion = false }) { Text(stringResource(R.string.annuler)) }
            },
        )
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
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
        Icon(icon, contentDescription = null, tint = colors.brandInk)
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.textTertiary)
    }
}

/** Miroir de `MyProfileQRView` — QR à faire scanner par un ami (« Ajouter un ami » de son onglet
 * Profil), pour que vos parties communes arrivent dans vos deux historiques. */
@Composable
fun MyProfileQrScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val players by container.playerRepository.observeAll().collectAsState(initial = emptyList())
    val me = players.firstOrNull { it.sharedProfileIsMine }
    val colors = LocalAppColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.m_ajouter_comme_ami)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fermer))
                    }
                },
            )
        },
    ) { innerPadding ->
        val id = me?.sharedProfileID ?: return@Scaffold
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            AvatarView(me.toAvatar(), size = AvatarSize.Large)
            Text(me.nickname, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            QrCodeView(
                content =
                    ProfileShareLink.url(
                        id = id,
                        name = me.nickname,
                        avatarKind = me.avatarKind,
                        avatarValue = me.avatarValue,
                        paletteID = me.paletteID,
                    ),
                modifier = Modifier.size(240.dp),
            )
            Text(
                stringResource(R.string.fais_scanner_ce_code_par_un_ami_depuis_ajouter_un_ami_dans),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

/** Miroir de `AddFriendFlow` — scanner le QR d'un ami, puis dire qui il est dans mes joueurs : une
 * fiche existante ou une nouvelle, créée avec son pseudo et son avatar. Un seul plein écran dont le
 * contenu bascule. */
@Composable
private fun AddFriendDialog(
    players: List<PlayerEntity>,
    onDismiss: () -> Unit,
) {
    var payload by remember { mutableStateOf<ProfileShareLink.Payload?>(null) }
    val scanned = payload

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (scanned == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                QrScannerView(
                    onScan = { raw -> if (payload == null) ProfileShareLink.parse(raw)?.let { payload = it } },
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(Space.lg)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fermer), tint = Color.White)
                }
            }
        } else {
            ChooseFriendFiche(payload = scanned, players = players, onDone = onDismiss)
        }
    }
}

@Composable
private fun ChooseFriendFiche(
    payload: ProfileShareLink.Payload,
    players: List<PlayerEntity>,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val isMe = players.any { it.sharedProfileIsMine && it.sharedProfileID == payload.id }
    val alreadyLinked = players.firstOrNull { !it.sharedProfileIsMine && it.sharedProfileID == payload.id }
    // Fiches actives qui ne représentent encore personne (ni moi, ni un ami déjà lié).
    val candidates = players.filter { !it.isArchived && it.sharedProfileID == null }
    val avatar = avatarFor(payload)

    fun link(player: PlayerEntity) {
        scope.launch {
            container.playerRepository.linkSharedProfile(payload.id, payload.name, player)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ajouter_un_ami)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fermer))
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
                    .verticalScroll(rememberScrollState())
                    .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                AvatarView(avatar, size = AvatarSize.Large)
                Text(payload.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            }

            when {
                isMe -> Text(stringResource(R.string.c_est_ton_propre_profil), color = colors.textSecondary)
                alreadyLinked != null ->
                    Text(
                        stringResource(
                            R.string.value1_est_deja_ton_ami_sur_la_fiche_value2,
                            payload.name,
                            alreadyLinked.nickname,
                        ),
                        color = colors.textSecondary,
                    )
                else -> {
                    PrimaryButton(
                        text = stringResource(R.string.creer_la_fiche_value1, payload.name),
                        onClick = {
                            scope.launch {
                                val emoji = (avatar.kind as? AvatarKind.Emoji)?.character ?: Avatar.curatedEmoji.first()
                                val player =
                                    container.playerRepository.create(
                                        payload.name,
                                        "emoji",
                                        emoji,
                                        null,
                                        payload.paletteID,
                                    )
                                container.playerRepository.linkSharedProfile(payload.id, payload.name, player)
                                onDone()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.nouvelle_fiche_avec_son_pseudo_et_son_avatar),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                    if (candidates.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ou_c_est_une_de_mes_fiches),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        ListContainer(modifier = Modifier.fillMaxWidth()) {
                            candidates.forEachIndexed { index, player ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { link(player) }
                                            .padding(horizontal = Space.lg, vertical = Space.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                                ) {
                                    AvatarView(player.toAvatar(), size = AvatarSize.Medium)
                                    Text(
                                        player.nickname,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textPrimary,
                                    )
                                }
                                if (index < candidates.lastIndex) ListRowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Un avatar photo ne transite pas par le QR ([ProfileShareLink]) : repli sur l'avatar dérivé du
 * pseudo, comme pour toute nouvelle fiche. */
private fun avatarFor(payload: ProfileShareLink.Payload): Avatar {
    val palette = PlayerPalette(payload.paletteID.toIntOrNull() ?: 1)
    return if (payload.avatarKind == "emoji" && payload.avatarValue.isNotEmpty()) {
        Avatar(kind = AvatarKind.Emoji(payload.avatarValue), palette = palette)
    } else {
        Avatar.generated(payload.name).copy(palette = palette)
    }
}

/**
 * Miroir de `ProfileRequirement`/`ProfileOnboardingView` (doc 16, phase A) — le profil est
 * obligatoire : tant qu'aucune fiche n'est « la mienne », cet écran recouvre l'app et se referme
 * de lui-même dès qu'elle existe. Aucun retour possible (ni geste, ni bouton système).
 */
@Composable
fun ProfileRequirementGate() {
    val container = LocalAppContainer.current
    val players by container.playerRepository.observeAll().collectAsState(initial = null)
    val loaded = players ?: return
    if (loaded.any { it.sharedProfileIsMine }) return

    var isCreating by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        if (isCreating) {
            PlayerEditorScreen(playerId = null, onDone = { isCreating = false }, isCreatingProfile = true)
        } else {
            Scaffold { innerPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterVertically),
                ) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = colors.brandInk,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        stringResource(R.string.creer_mon_profil),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Benefit(stringResource(R.string.rejoindre_les_parties_de_tes_amis_et_y_etre_reconnu))
                        Benefit(stringResource(R.string.retrouver_vos_parties_jouees_ensemble_dans_ton_historique))
                        Benefit(stringResource(R.string.un_pseudo_et_un_avatar_rien_de_plus_aucun_compte_a_creer))
                    }
                    Spacer(Modifier.size(Space.sm))
                    PrimaryButton(
                        text = stringResource(R.string.creer_mon_profil),
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textSecondary)
}
