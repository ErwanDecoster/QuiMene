package com.quimene.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.designsystem.components.Banner
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import com.quimene.store.DeviceIdentity
import com.quimene.store.MatchEntity
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
            value = LiveMatchSetup(match, definition, rules, deviceID)
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
        RoundEntryDispatch(viewModel) { gameName ->
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
)

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

/** Coquille commune aux 5 formes de saisie (générique + les 4 dédiées) : titre, « Annuler la
 * dernière manche » toujours visible (miroir de `LiveMatchView.swift` — Apple la sort du menu
 * plutôt que de l'y enterrer), menu Partager/Voir les manches/Terminer/Abandonner, bandeaux
 * d'erreur/explication de manche — seul le contenu central change d'un jeu à l'autre.
 *
 * Contrairement à Apple (5 barres d'outils différentes — les 4 écrans dédiés n'ont ni Terminer ni
 * Partager), cette coquille unique s'applique aux 5 formes : plutôt que d'appauvrir Android pour
 * copier l'incohérence d'Apple entre ses propres écrans, tous les jeux gagnent un accès uniforme
 * ici (doc utilisateur — cohérence *au sein* d'Android, pas seulement avec Apple). */
@Composable
private fun LiveMatchScaffold(
    viewModel: LiveMatchViewModel,
    onAbandoned: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isPresentingShareSession by remember { mutableStateOf(false) }
    var isPresentingRoundHistory by remember { mutableStateOf(false) }
    var isConfirmingEnd by remember { mutableStateOf(false) }
    var isConfirmingAbandon by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.definition.name.localized) },
                actions = {
                    if (viewModel.canEndManually) {
                        IconButton(onClick = { viewModel.undoLastRound() }) {
                            Icon(
                                Icons.Filled.Undo,
                                contentDescription = stringResource(R.string.annuler_la_derniere_manche),
                            )
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (viewModel.isSharing) {
                                        stringResource(
                                            R.string.voir_la_session_partagee,
                                        )
                                    } else {
                                        stringResource(R.string.partager_en_direct)
                                    },
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                isPresentingShareSession = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.voir_les_manches)) },
                            onClick = {
                                menuExpanded = false
                                isPresentingRoundHistory = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminer_la_partie)) },
                            enabled = viewModel.canEndManually,
                            onClick = {
                                menuExpanded = false
                                isConfirmingEnd = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.abandonner_la_partie)) },
                            onClick = {
                                menuExpanded = false
                                isConfirmingAbandon = true
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

    val coordinator = viewModel.shareCoordinator
    if (isPresentingShareSession && coordinator != null) {
        val context = LocalContext.current
        ShareSessionDialog(
            coordinator = coordinator,
            isAttached = viewModel.isSharing,
            onDismiss = { isPresentingShareSession = false },
            startAction = { viewModel.startSharing(DeviceIdentity.name(context), allowsContributors = true) },
        )
    }
    if (isPresentingRoundHistory) {
        RoundHistoryDialog(viewModel, onDismiss = { isPresentingRoundHistory = false })
    }
    if (isConfirmingEnd) {
        AlertDialog(
            onDismissRequest = { isConfirmingEnd = false },
            title = { Text(stringResource(R.string.terminer_la_partie_2)) },
            text = {
                Text(stringResource(R.string.le_classement_final_sera_calcule_a_partir_des_manches))
            },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingEnd = false
                    viewModel.endManually()
                }) {
                    Text(stringResource(R.string.terminer_la_partie))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isConfirmingEnd = false },
                ) { Text(stringResource(R.string.annuler)) }
            },
        )
    }
    if (isConfirmingAbandon) {
        AlertDialog(
            onDismissRequest = { isConfirmingAbandon = false },
            title = { Text(stringResource(R.string.abandonner_cette_partie)) },
            text = {
                Text(stringResource(R.string.la_partie_sera_classee_comme_abandonnee_dans_l_historique))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingAbandon = false
                        viewModel.abandon()
                        onAbandoned()
                    },
                ) { Text(stringResource(R.string.abandonner)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { isConfirmingAbandon = false },
                ) { Text(stringResource(R.string.annuler)) }
            },
        )
    }
}
