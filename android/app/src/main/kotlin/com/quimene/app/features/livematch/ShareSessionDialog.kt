package com.quimene.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quimene.app.R
import com.quimene.app.livesync.JoinLink
import com.quimene.app.livesync.LiveShareCoordinator
import com.quimene.designsystem.components.Banner
import com.quimene.designsystem.components.QrCodeView
import com.quimene.designsystem.components.SecondaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.sync.SessionPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Feuille de partage d'une partie en direct — miroir de `ShareSessionView.swift` (sans le QR
 * code : la saisie manuelle du code à 6 chiffres suffit pour cette version, voir
 * [com.quimene.app.features.join.JoinScreen]). Code de pairage bien visible, bascule « Autoriser
 * les contributeurs », liste des appareils connectés, bouton « Arrêter le partage ».
 *
 * Pilotée directement par [LiveShareCoordinator] (durée de vie applicative) plutôt que par un
 * `LiveMatchViewModel` particulier — cette même feuille sert aussi bien depuis
 * [com.quimene.app.features.livematch.LiveMatchScreen] (partage/gestion d'*une* partie donnée,
 * [isAttached] = « c'est bien la mienne ») que depuis
 * [com.quimene.app.features.play.GamesCatalogScreen] (simple observation d'une session déjà en
 * cours, [startAction] `null` — miroir de `ShareSessionView(startAction: nil)`).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShareSessionDialog(
    coordinator: LiveShareCoordinator,
    isAttached: Boolean,
    onDismiss: () -> Unit,
    startAction: (suspend () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var isStarting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    // Une seule tentative par ouverture de la feuille : après « Arrêter le partage », la partie
    // n'est plus rattachée, et sans ce garde une nouvelle session s'ouvrait aussitôt.
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(isAttached) {
        if (!isAttached && startAction != null && !hasStarted) {
            hasStarted = true
            isStarting = true
            startError = null
            try {
                startAction()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                isStarting = false
                startError = error.message ?: "Le partage n'a pas pu démarrer."
            }
        }
    }
    LaunchedEffect(coordinator.pairingCode) {
        if (coordinator.pairingCode != null) isStarting = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            when {
                startError != null ->
                    Banner(message = startError.orEmpty())
                isStarting || coordinator.pairingCode == null ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.demarrage_du_partage), modifier = Modifier.padding(top = Space.md))
                    }
                else ->
                    SharingContent(
                        coordinator = coordinator,
                        onStop = {
                            scope.launch {
                                coordinator.stopSharing()
                                onDismiss()
                            }
                        },
                    )
            }
        }
    }
}

@Composable
private fun SharingContent(
    coordinator: LiveShareCoordinator,
    onStop: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val code = coordinator.pairingCode.orEmpty()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        QrCodeView(content = JoinLink.url(code), modifier = Modifier.size(180.dp))
        Text(
            code,
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                ),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.a_scanner_bouton_scanner_un_code_sur_l_appareil_qui_rejoint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm),
        )
    }

    HorizontalDivider()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.autoriser_les_contributeurs),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Text(
                if (coordinator.allowsContributors) {
                    stringResource(R.string.les_personnes_qui_rejoignent_peuvent_choisir_d_observer_ou)
                } else {
                    stringResource(R.string.les_personnes_qui_rejoignent_ne_peuvent_qu_observer_quel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = coordinator.allowsContributors,
            onCheckedChange = { allowed -> scope.launch { coordinator.setAllowsContributors(allowed) } },
        )
    }

    HorizontalDivider()

    Text(
        stringResource(R.string.appareils_connectes),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary,
    )
    if (coordinator.connectedPeers.isEmpty()) {
        Text(
            stringResource(R.string.en_attente_d_un_appareil_qui_rejoint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
    } else {
        for (peer in coordinator.connectedPeers) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(peer.deviceName, color = colors.textPrimary)
                Text(
                    roleLabel(peer, coordinator.allowsContributors),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }

    SecondaryButton(
        text = stringResource(R.string.arreter_le_partage),
        onClick = onStop,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun roleLabel(
    peer: SessionPresence,
    allowsContributors: Boolean,
): String =
    when {
        peer.isOwner -> stringResource(R.string.createur)
        allowsContributors -> stringResource(R.string.contributeur)
        else -> stringResource(R.string.observateur)
    }
