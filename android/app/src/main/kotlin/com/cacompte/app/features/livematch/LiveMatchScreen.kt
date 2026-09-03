package com.cacompte.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.features.livematch.belote.BeloteRoundScreen
import com.cacompte.app.features.livematch.tarot.TarotRoundScreen
import com.cacompte.app.features.livematch.wizard.WizardRoundScreen
import com.cacompte.app.features.livematch.yams.YamsRoundScreen
import com.cacompte.app.ui.toAvatar
import com.cacompte.catalog.games.BeloteRulesV1
import com.cacompte.catalog.games.TarotRulesV1
import com.cacompte.catalog.games.WizardRulesV1
import com.cacompte.catalog.games.YamsRulesV1
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.model.Participant
import com.cacompte.domain.rules.EntryKind
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.store.DeviceIdentity
import com.cacompte.store.MatchEntity
import com.cacompte.store.ParticipantEntity
import java.util.UUID

/**
 * Point d'entrée de la partie en direct — choisit la bonne forme de saisie selon le moteur du
 * jeu (miroir de `LiveMatchView.swift`, doc 05) : [GenericRoundEntry] pour la famille "entier
 * simple" (`EntryKind.Integer` : Skyjo, Mölkky, et la plupart du catalogue générique, **au
 * clavier système**, pas de pavé numérique maison — ADR-0013), et un écran dédié pour Belote/
 * Tarot ([BeloteRoundScreen]/[TarotRoundScreen], `structured`), Wizard ([WizardRoundScreen],
 * `predictionAndResult`) et Yams ([YamsRoundScreen], `categorySheet`) — chacun a une saisie
 * fondamentalement différente (drapeaux d'équipe/contrat, annonce puis résultat, grille de
 * catégories) que le formulaire générique ne pourrait pas rendre correctement. [RoundEntryPlaceholder]
 * reste un repli défensif pour un moteur qu'aucun des cinq cas ne couvrirait (aucun dans le
 * catalogue actuel) — jamais un formulaire silencieusement faux (doc 10).
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

    // Belote/Tarot/Wizard n'ont pas la même forme de saisie qu'un jeu à `EntryKind.Integer` —
    // dispatch sur `engine` (pas `entry.kind`, ambigu : Belote et Tarot partagent tous deux
    // `structured`) plutôt que sur `entry.kind` seul.
    when (setup.definition.engine) {
        BeloteRulesV1.ENGINE_ID ->
            LiveMatchScaffold(viewModel, onAbandoned) { BeloteRoundScreen(viewModel) }
        TarotRulesV1.ENGINE_ID ->
            LiveMatchScaffold(viewModel, onAbandoned) { TarotRoundScreen(viewModel) }
        WizardRulesV1.ENGINE_ID ->
            LiveMatchScaffold(viewModel, onAbandoned) { WizardRoundScreen(viewModel) }
        YamsRulesV1.ENGINE_ID ->
            LiveMatchScaffold(viewModel, onAbandoned) { YamsRoundScreen(viewModel) }
        else ->
            if (setup.definition.scoring.entry.kind == EntryKind.Integer) {
                LiveMatchScaffold(viewModel, onAbandoned) {
                    GenericRoundEntry(viewModel, setup.snapshotsByParticipant)
                }
            } else {
                RoundEntryPlaceholder(setup.definition.name.localized, onAbandoned)
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
 * Terminer/Annuler/Abandonner, bandeaux d'erreur/explication de manche — seul le contenu central
 * change d'un jeu à l'autre. */
@Composable
private fun LiveMatchScaffold(
    viewModel: LiveMatchViewModel,
    onAbandoned: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
            viewModel.roundExplanationMessage?.let { Banner(message = it, modifier = Modifier.padding(Space.lg)) }
            viewModel.validationErrorMessage?.let {
                Banner(message = it, modifier = Modifier.padding(horizontal = Space.lg))
            }
            content()
        }
    }
}

@Composable
private fun GenericRoundEntry(
    viewModel: LiveMatchViewModel,
    snapshotsByParticipant: Map<UUID, ParticipantEntity>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(viewModel.participants, key = { it.id }) { participant ->
                ParticipantScoreRow(
                    participant = participant,
                    snapshot = snapshotsByParticipant[participant.id],
                    total = viewModel.totals[participant.id] ?: 0,
                    pendingValue = viewModel.pendingScores[participant.id],
                    requiresCloserSelection = viewModel.requiresCloserSelection,
                    isCloser = viewModel.closedParticipantID == participant.id,
                    onScoreChange = { raw ->
                        val value = raw.toIntOrNull()
                        if (raw.isEmpty()) {
                            viewModel.clearScore(participant.id)
                        } else if (value != null) {
                            viewModel.setScore(participant.id, value)
                        }
                    },
                    onToggleCloser = {
                        viewModel.closedParticipantID =
                            if (viewModel.closedParticipantID == participant.id) null else participant.id
                    },
                    onFocus = { viewModel.focus(participant.id) },
                )
            }
        }

        PrimaryButton(
            text = "Valider la manche",
            onClick = { viewModel.commitRound() },
            modifier = Modifier.padding(Space.lg),
        )
    }
}

@Composable
private fun ParticipantScoreRow(
    participant: Participant,
    snapshot: ParticipantEntity?,
    total: Int,
    pendingValue: Int?,
    requiresCloserSelection: Boolean,
    isCloser: Boolean,
    onScoreChange: (String) -> Unit,
    onToggleCloser: () -> Unit,
    onFocus: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            if (snapshot != null) {
                AvatarView(snapshot.toAvatar(), size = AvatarSize.Small)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(participant.displayName, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text("Total : $total", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            if (requiresCloserSelection) {
                Chip(title = "Ferme", isSelected = isCloser, onClick = onToggleCloser)
            }
            OutlinedTextField(
                value = pendingValue?.toString() ?: "",
                onValueChange = onScoreChange,
                modifier =
                    Modifier.width(96.dp).onFocusChanged { focusState ->
                        if (focusState.isFocused) onFocus()
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
