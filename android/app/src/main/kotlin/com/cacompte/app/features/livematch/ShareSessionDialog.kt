package com.cacompte.app.features.livematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.components.SecondaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.DeviceIdentity
import com.cacompte.sync.Role

/**
 * Feuille de partage d'une partie en direct — miroir de `ShareSessionView.swift` (sans le QR
 * code : la saisie manuelle du code à 6 chiffres suffit pour cette version, voir
 * [com.cacompte.app.features.join.JoinScreen]). Code de pairage bien visible, bascule « Autoriser
 * les contributeurs », liste des appareils connectés, bouton « Arrêter le partage ».
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShareSessionDialog(
    viewModel: LiveMatchViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var isStarting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel.isSharing) {
        if (!viewModel.isSharing) {
            isStarting = true
            startError = null
            viewModel.startSharing(
                deviceName = DeviceIdentity.name(context),
                allowsContributors = true,
                onError = { error ->
                    isStarting = false
                    startError = error.message ?: "Le partage n'a pas pu démarrer."
                },
            )
        }
    }
    LaunchedEffect(viewModel.pairingCode) {
        if (viewModel.pairingCode != null) isStarting = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            when {
                startError != null ->
                    Banner(message = startError.orEmpty())
                isStarting || viewModel.pairingCode == null ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text("Démarrage du partage…", modifier = Modifier.padding(top = Space.md))
                    }
                else -> SharingContent(viewModel)
            }
        }
    }
}

@Composable
private fun SharingContent(viewModel: LiveMatchViewModel) {
    val colors = LocalAppColors.current
    val code = viewModel.pairingCode.orEmpty()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
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
            "À communiquer à qui veut rejoindre — aucun réseau Wi-Fi commun n'est nécessaire, juste une connexion Internet des deux côtés.",
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
            Text("Autoriser les contributeurs", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(
                "Désactivé, les appareils qui rejoignent ne peuvent qu'observer la partie.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Switch(checked = viewModel.allowsContributors, onCheckedChange = viewModel::setAllowsContributors)
    }

    HorizontalDivider()

    Text("Appareils connectés", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
    if (viewModel.connectedPeers.isEmpty()) {
        Text(
            "En attente d'un appareil qui rejoint…",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
    } else {
        for (peer in viewModel.connectedPeers) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(peer.deviceName, color = colors.textPrimary)
                Text(roleLabel(peer.role), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }

    SecondaryButton(text = "Arrêter le partage", onClick = viewModel::stopSharing, modifier = Modifier.fillMaxWidth())
}

private fun roleLabel(role: Role): String =
    when (role) {
        Role.Host -> "Hôte"
        Role.Contributor -> "Contributeur"
        Role.Observer -> "Observateur"
    }
