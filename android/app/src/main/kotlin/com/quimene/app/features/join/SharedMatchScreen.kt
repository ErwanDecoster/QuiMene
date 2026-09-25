package com.quimene.app.features.join

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.features.livematch.NextMatchBar
import com.quimene.app.features.livematch.NextMatchPicker
import com.quimene.app.features.livematch.ProfileBadgeView
import com.quimene.app.features.livematch.RoundEntryDispatch
import com.quimene.app.features.results.MatchSummaryContent
import com.quimene.app.livesync.SharedMatchViewModel
import com.quimene.app.livesync.ensureFriend
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.Banner
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.SecondaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space

/**
 * Vue d'une partie rejointe — miroir de `SharedMatchView.swift`. Contributeur : mêmes écrans de
 * saisie que l'hôte ([RoundEntryDispatch], partagés via [com.quimene.app.features.livematch.LiveRoundEntryState]).
 * Observateur : classement en lecture seule, aucune saisie affichée.
 */
@Composable
fun SharedMatchScreen(
    viewModel: SharedMatchViewModel,
    onQuit: () -> Unit,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val state = viewModel.stateOrNull
    val playerRepository = LocalAppContainer.current.playerRepository
    // Mes amis liés (doc 14) : leur place porte un lien.
    LaunchedEffect(viewModel) {
        playerRepository.observeAll().collect { players ->
            viewModel.friendProfileIDs =
                players.filter { !it.sharedProfileIsMine }.mapNotNull { it.sharedProfileID }.toSet()
        }
    }
    // Doc 16, phase D — ma place retenue : le créateur devient mon ami (liaison dans les deux sens).
    val ownerToBefriend = viewModel.ownerToBefriend
    LaunchedEffect(ownerToBefriend?.id) {
        ownerToBefriend?.let { runCatching { ensureFriend(it, playerRepository) } }
    }
    var isPickingNextMatch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state?.let { viewModel.definition.name.localized } ?: stringResource(R.string.connexion),
                    )
                },
                // Doc 16, phase A — revenir en arrière garde la partie suivie (bandeau de reprise
                // dans Jeux) ; seul « Quitter la partie » déconnecte.
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fermer))
                    }
                },
                actions = { TextButton(onClick = onQuit) { Text(stringResource(R.string.quitter_la_partie)) } },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Doc 16, phase C — plus d'hôte à rejoindre : seule la connexion de cet appareil compte.
            // Le tableau reste celui du dernier rattrapage, la saisie est bloquée.
            if (viewModel.isSessionClosed) {
                Banner(
                    message = stringResource(R.string.le_createur_a_arrete_la_session_le_tableau_affiche_est_le),
                    modifier = Modifier.padding(Space.lg),
                )
            } else if (!viewModel.isHostConnected) {
                Banner(
                    message = stringResource(R.string.hors_connexion_le_tableau_affiche_est_le_dernier_recu),
                    modifier = Modifier.padding(Space.lg),
                    actionTitle = stringResource(R.string.reessayer),
                    onAction = onReconnect,
                )
            }
            viewModel.roundExplanationMessage?.let { Banner(message = it, modifier = Modifier.padding(Space.lg)) }
            viewModel.latestRejectionReason?.let {
                Banner(message = it, modifier = Modifier.padding(horizontal = Space.lg))
            }
            viewModel.validationErrorMessage?.let {
                Banner(message = it, modifier = Modifier.padding(horizontal = Space.lg))
            }

            if (state == null) return@Column

            // Doc 16, phase D — « Qui es-tu dans cette partie ? » tant que je n'ai ni place ni
            // choisi de seulement regarder.
            if (viewModel.needsIdentity) {
                WhoAreYou(viewModel)
                return@Column
            }

            // Doc 16, phase C — même écran de résultats que le créateur, puis « Partie suivante » :
            // un participant peut enchaîner même si le créateur est absent.
            if (viewModel.isConcluded) {
                viewModel.summaryState()?.let { MatchSummaryContent(it, modifier = Modifier.weight(1f)) }
                if (viewModel.canPropose) {
                    NextMatchBar(
                        isBusy = viewModel.isSubmitting,
                        modifier = Modifier.padding(Space.lg),
                    ) { isPickingNextMatch = true }
                }
                if (isPickingNextMatch) {
                    NextMatchPicker(
                        playerCount = state.participants.size,
                        currentGameID = state.gameID,
                        onDismiss = { isPickingNextMatch = false },
                    ) { viewModel.startNextMatch(it) }
                }
                return@Column
            }

            IdentityRow(viewModel)
            StandingsSection(viewModel)

            if (viewModel.canPropose) {
                RoundEntryDispatch(viewModel) { gameName ->
                    Text(
                        "La saisie dédiée de $gameName n'est pas encore disponible en tant que contributeur.",
                        modifier = Modifier.padding(Space.lg),
                    )
                }
            } else {
                Text(
                    if (viewModel.isSpectator) {
                        stringResource(R.string.tu_regardes_la_partie_la_saisie_se_fait_sur_les_appareils)
                    } else {
                        stringResource(R.string.tu_observes_cette_partie_seul_le_createur_saisit_les_scores)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Space.lg),
                )
            }
        }
    }
}

@Composable
private fun StandingsSection(viewModel: SharedMatchViewModel) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        for (standing in viewModel.currentStandings) {
            val participant = viewModel.participants.firstOrNull { it.id == standing.participantID } ?: continue
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(participant.displayName, color = colors.textPrimary)
                ProfileBadgeView(viewModel.profileBadges[participant.id], modifier = Modifier.padding(start = Space.sm))
                Spacer(modifier = Modifier.weight(1f))
                Text("${standing.score}", color = colors.textSecondary)
            }
        }
    }
}

/** Doc 16, phase D — qui je suis dans cette partie, et comment en changer. */
@Composable
private fun IdentityRow(viewModel: SharedMatchViewModel) {
    val colors = LocalAppColors.current
    val seat = viewModel.mySeat
    val (text, action) =
        when {
            viewModel.isSpectator ->
                stringResource(R.string.tu_regardes_la_partie) to
                    stringResource(R.string.je_joue_aussi)
            seat != null ->
                stringResource(R.string.tu_joues_value1, seat.displayName) to
                    (if (viewModel.canChangeSeat) stringResource(R.string.changer) else null)
            else -> return
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        action?.let { TextButton(onClick = { viewModel.chooseAgain() }) { Text(it) } }
    }
}

/** Doc 16, phase D — « Qui es-tu dans cette partie ? », à l'arrivée dans une session : toucher sa
 * place la revendique (premier arrivé, premier servi), ou « Je regarde seulement ». Un ami déjà
 * lié par le créateur n'y passe jamais : sa place est reconnue d'office. Miroir de
 * `WhoAreYouView` (Swift). */
@Composable
private fun WhoAreYou(viewModel: SharedMatchViewModel) {
    val colors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item {
            Text(
                stringResource(R.string.qui_es_tu_dans_cette_partie),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(vertical = Space.sm),
            )
        }
        items(viewModel.participants, key = { it.id }) { participant ->
            val taken = viewModel.seatStatus(participant) == SharedMatchViewModel.SeatStatus.Taken
            val enabled = !taken && viewModel.me != null && !viewModel.isClaiming
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { viewModel.claim(participant) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    AvatarView(Avatar.generated(participant.displayName), size = AvatarSize.Medium)
                    Text(
                        participant.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (taken) colors.textTertiary else colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (taken) {
                        Text(
                            stringResource(R.string.deja_prise),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.ton_profil_est_lie_a_cette_place_chez_le_createur_qui_en),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        viewModel.identityMessage?.let { message ->
            item { Banner(message = message) }
        }
        item {
            SecondaryButton(
                text = stringResource(R.string.je_regarde_seulement),
                onClick = { viewModel.watchOnly() },
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
            )
        }
        item {
            Text(
                stringResource(R.string.tu_suis_la_partie_sans_saisir_de_score),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
