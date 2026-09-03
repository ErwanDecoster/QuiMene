package com.cacompte.app.features.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.features.livematch.ShareSessionDialog
import com.cacompte.app.navigation.floatingNavBarContentPadding
import com.cacompte.app.ui.GameRequestMail
import com.cacompte.app.ui.gameIcon
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.components.ListContainer
import com.cacompte.designsystem.components.ListRowDivider
import com.cacompte.designsystem.tokens.IconSize
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.store.DeviceIdentity
import com.cacompte.store.MatchEntity
import kotlinx.coroutines.launch

/** Miroir de `GamesTabView.swift` (doc 05) — la liste des jeux du catalogue : icône, nom,
 * description courte. Chaque ligne ouvre la mise en place d'une partie ; l'icône de trophée
 * ouvre son classement (remplace l'action de balayage « Meilleurs joueurs » d'iOS, sans
 * équivalent standard côté Android). Au-dessus : les parties en cours à reprendre (doc 01) et,
 * quand une session est déjà partagée, un accès direct à sa gestion (code, pairs, arrêt) sans
 * avoir à rouvrir la partie — miroir du bouton `topBarTrailing` d'iOS. */
@Composable
fun GamesCatalogScreen(
    onGameSelected: (String) -> Unit,
    onOpenLeaderboard: (String) -> Unit,
    onResumeMatch: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = container.catalog
    val viewModel = rememberViewModel { GamesCatalogViewModel(container.matchRepository, catalog) }
    val inProgressMatches by viewModel.inProgressMatches.collectAsState()
    var matchPendingAbandon by remember { mutableStateOf<MatchEntity?>(null) }
    var isPresentingActiveShare by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isPresentingMailFallback by remember { mutableStateOf(false) }
    val isSharing = container.liveShareCoordinator.attachedMatchID != null
    val searchFocusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = viewModel.searchText,
                            onValueChange = viewModel::updateSearchText,
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                            placeholder = { Text("Rechercher un jeu") },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                ),
                        )
                    } else {
                        Text("Jeux")
                    }
                },
                navigationIcon = {
                    if (isSearching) {
                        IconButton(
                            onClick = {
                                isSearching = false
                                viewModel.updateSearchText("")
                            },
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Fermer la recherche")
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        if (viewModel.searchText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchText("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Rechercher un jeu")
                        }
                        if (isSharing) {
                            IconButton(onClick = { isPresentingActiveShare = true }) {
                                Icon(Icons.Filled.Wifi, contentDescription = "Session partagée en cours")
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (viewModel.searchText.isNotBlank() && viewModel.games.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Search,
                message = "Aucun jeu ne correspond à ta recherche.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                actionTitle = "Demander ce jeu",
                onAction = {
                    if (!GameRequestMail.open(context, viewModel.searchText)) isPresentingMailFallback = true
                },
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
            if (viewModel.searchText.isBlank() && inProgressMatches.isNotEmpty()) {
                ListContainer(modifier = Modifier.fillMaxWidth()) {
                    inProgressMatches.forEachIndexed { index, match ->
                        ResumeMatchRow(
                            gameName = viewModel.gameName(match),
                            onClick = { onResumeMatch(match.id.toString()) },
                            onAbandon = { matchPendingAbandon = match },
                        )
                        if (index < inProgressMatches.lastIndex) ListRowDivider()
                    }
                }
            }
            if (viewModel.games.isNotEmpty()) {
                ListContainer(modifier = Modifier.fillMaxWidth()) {
                    viewModel.games.forEachIndexed { index, definition ->
                        GameRow(
                            definition = definition,
                            onClick = { onGameSelected(definition.id) },
                            onOpenLeaderboard = { onOpenLeaderboard(definition.id) },
                        )
                        if (index < viewModel.games.lastIndex) ListRowDivider()
                    }
                }
            }
        }
    }

    if (isPresentingMailFallback) {
        AlertDialog(
            onDismissRequest = { isPresentingMailFallback = false },
            title = { Text("Aucune messagerie configurée") },
            text = { Text("Envoie ta demande à ${GameRequestMail.RECIPIENT} depuis l'application de ton choix.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(GameRequestMail.RECIPIENT))
                        isPresentingMailFallback = false
                    },
                ) { Text("Copier l'adresse") }
            },
            dismissButton = {
                TextButton(onClick = { isPresentingMailFallback = false }) { Text("OK") }
            },
        )
    }

    matchPendingAbandon?.let { match ->
        AlertDialog(
            onDismissRequest = { matchPendingAbandon = null },
            title = { Text("Abandonner cette partie ?") },
            text = {
                Text(
                    "La partie sera classée comme abandonnée dans l'historique, avec le classement " +
                        "atteint jusque-là. Cette action ne peut pas être annulée.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        matchPendingAbandon = null
                        scope.launch { viewModel.abandon(match, DeviceIdentity.current(context)) }
                    },
                ) { Text("Abandonner") }
            },
            dismissButton = { TextButton(onClick = { matchPendingAbandon = null }) { Text("Annuler") } },
        )
    }

    if (isPresentingActiveShare) {
        ShareSessionDialog(
            coordinator = container.liveShareCoordinator,
            isAttached = true,
            onDismiss = { isPresentingActiveShare = false },
        )
    }
}

@Composable
private fun ResumeMatchRow(
    gameName: String,
    onClick: () -> Unit,
    onAbandon: () -> Unit,
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
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.brandBrass,
                modifier = Modifier.size(IconSize.lg),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Reprendre la partie en cours",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(text = gameName, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        IconButton(onClick = onAbandon) {
            Icon(Icons.Filled.Close, contentDescription = "Abandonner la partie en cours de $gameName")
        }
    }
}

@Composable
private fun GameRow(
    definition: GameDefinition,
    onClick: () -> Unit,
    onOpenLeaderboard: () -> Unit,
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
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = gameIcon(definition.id),
                contentDescription = null,
                tint = colors.brandInk,
                modifier = Modifier.size(IconSize.lg),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition.name.localized,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            definition.shortDescription?.localized?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        IconButton(onClick = onOpenLeaderboard) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = "Classement de ${definition.name.localized}")
        }
    }
}
