package com.cacompte.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
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
import com.cacompte.app.ui.toAvatar
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
 * Miroir de `LiveMatchView.swift` (doc 05) — saisie de manche générique, pour la famille "entier
 * simple" (`EntryKind.Integer` : Skyjo, Mölkky, et la plupart des jeux du catalogue générique).
 * **Saisie au clavier système** (`KeyboardType.Number`), pas de pavé numérique maison (ADR-0013 :
 * retiré côté Apple, revalidé — pas simplement reporté — pour cette étape).
 *
 * Belote/Tarot (`structured`), Wizard (`predictionAndResult`) et Yams (`categorySheet`) ont
 * chacun une saisie fondamentalement différente (drapeaux d'équipe/contrat, annonce puis
 * résultat, grille de catégories) qui ne peut pas être rendue correctement par ce formulaire
 * générique — les servir ici produirait un score enregistré incomplet, la seule faute grave du
 * projet (doc 10). Ils affichent donc un repli explicite ([RoundEntryPlaceholder]) plutôt qu'un
 * formulaire silencieusement faux, en attendant leurs 4 écrans dédiés.
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

    if (setup.definition.scoring.entry.kind != EntryKind.Integer) {
        RoundEntryPlaceholder(setup.definition.name.localized, onAbandoned)
        return
    }

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
    LaunchedEffect(viewModel.isConcluded) {
        if (viewModel.isConcluded) onConcluded(matchId)
    }

    LiveMatchContent(viewModel, setup.snapshotsByParticipant, onAbandoned)
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

@Composable
private fun LiveMatchContent(
    viewModel: LiveMatchViewModel,
    snapshotsByParticipant: Map<UUID, ParticipantEntity>,
    onAbandoned: () -> Unit,
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
