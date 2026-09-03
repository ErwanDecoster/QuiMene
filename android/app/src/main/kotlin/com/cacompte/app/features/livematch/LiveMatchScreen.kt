package com.cacompte.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.store.DeviceIdentity
import com.cacompte.store.MatchEntity
import com.cacompte.store.ParticipantEntity
import java.util.UUID

/**
 * Point d'entrée de la partie en direct — choisit la bonne forme de saisie selon le moteur du
 * jeu (miroir de `LiveMatchView.swift`, doc 05) via [RoundEntryDispatch], enveloppée dans
 * [LiveMatchScaffold] (titre, menu Terminer/Annuler/Abandonner/Partager, bandeaux).
 */
@Composable
fun LiveMatchScreen(
    matchId: String,
    onConcluded: (String) -> Unit,
    onAbandoned: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val loaded =
        produceState<LiveMatchSetup?>(initialValue = null, matchId) {
            val id = UUID.fromString(matchId)
            val match = requireNotNull(container.matchRepository.match(id)) { "Partie introuvable : $matchId" }
            val definition = container.catalog.definition(match.gameID, match.rulesVersion)
            val rules = container.catalog.rules(match.gameID, match.rulesVersion)
            val deviceID = DeviceIdentity.current(context)
            val participantSnapshots = container.matchRepository.participants(id)
            value = LiveMatchSetup(match, definition, rules, deviceID, participantSnapshots)
        }
    val setup = loaded.value ?: return

    val viewModel =
        rememberViewModel {
            LiveMatchViewModel(
                setup.match,
                setup.definition,
                setup.rules,
                container.catalog,
                container.matchRepository,
                setup.deviceID,
                container.liveShareCoordinator,
            )
        }
    // `LiveMatchViewModel.init` charge le `MatchState` de façon asynchrone (Room dispatche
    // réellement vers un thread d'arrière-plan hors test) — `participants`/`isConcluded`/etc.
    // lèvent tant que ce chargement n'est pas terminé. Ne rien lire dessus avant.
    if (viewModel.stateOrNull == null) {
        LoadingScreen()
        return
    }

    LaunchedEffect(viewModel.isConcluded) {
        if (viewModel.isConcluded) onConcluded(matchId)
    }

    LiveMatchScaffold(viewModel, onAbandoned) {
        RoundEntryDispatch(viewModel, setup.snapshotsByParticipant) { gameName ->
            RoundEntryPlaceholder(gameName, onAbandoned)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private data class LiveMatchSetup(
    val match: MatchEntity,
    val definition: GameDefinition,
    val rules: GameRules,
    val deviceID: String,
    val snapshots: List<ParticipantEntity>,
) {
    val snapshotsByParticipant: Map<UUID, ParticipantEntity> get() = snapshots.associateBy { it.id }
}

@Composable
private fun RoundEntryPlaceholder(
    gameName: String,
    onAbandoned: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(gameName) }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text("La saisie dédiée de ce jeu n'est pas encore construite dans cette version.")
            PrimaryButton(text = "Retour", onClick = onAbandoned)
        }
    }
}

/** Coquille commune aux 5 formes de saisie (générique + les 4 dédiées) : titre, menu
 * Terminer/Annuler/Abandonner/Partager, bandeaux d'erreur/explication de manche — seul le
 * contenu central change d'un jeu à l'autre. */
@Composable
private fun LiveMatchScaffold(
    viewModel: LiveMatchViewModel,
    onAbandoned: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isPresentingShareSession by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.definition.name.localized) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (viewModel.isSharing) "Voir la session partagée" else "Partager en direct",
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                isPresentingShareSession = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Terminer la partie") },
                            enabled = viewModel.canEndManually,
                            onClick = {
                                menuExpanded = false
                                viewModel.endManually()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Annuler la dernière manche") },
                            enabled = viewModel.canEndManually,
                            onClick = {
                                menuExpanded = false
                                viewModel.undoLastRound()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Abandonner") },
                            onClick = {
                                menuExpanded = false
                                viewModel.abandon()
                                onAbandoned()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            viewModel.remoteActivityMessage?.let { Banner(message = it, modifier = Modifier.padding(Space.lg)) }
            viewModel.roundExplanationMessage?.let { Banner(message = it, modifier = Modifier.padding(Space.lg)) }
            viewModel.validationErrorMessage?.let {
                Banner(message = it, modifier = Modifier.padding(horizontal = Space.lg))
            }
            content()
        }
    }

    if (isPresentingShareSession) {
        ShareSessionDialog(viewModel, onDismiss = { isPresentingShareSession = false })
    }
}
