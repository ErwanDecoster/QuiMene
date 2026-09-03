package com.cacompte.app.features.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.cacompte.app.BuildConfig
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.livesync.JoinLink
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.DeviceIdentity
import com.cacompte.sync.LiveSession
import com.cacompte.sync.SupabaseTransportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Miroir de `JoinTabView.swift` (doc 09) — l'appareil photo s'ouvre immédiatement (comme Apple,
 * caméra par défaut) avec un bouton flottant « Saisir un code » par-dessus, qui ouvre la saisie
 * manuelle en repli (doc utilisateur — voir [QrScannerView] pour pourquoi CameraX plutôt que le
 * Play Services Code Scanner utilisé un temps : celui-ci ne permet pas de bouton par-dessus son
 * propre écran de scan). Une fois connecté, remplacé par [SharedMatchScreen].
 */
@Composable
fun JoinScreen() {
    val container = LocalAppContainer.current
    val coordinator = container.matchConnectionCoordinator
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sharedMatch = coordinator.sharedMatch
    if (sharedMatch != null) {
        SharedMatchScreen(
            sharedMatch,
            onQuit = { scope.launch { coordinator.stop() } },
            onReconnect = { scope.launch { coordinator.reconnectNow() } },
        )
        return
    }

    var pairingCode by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isManualEntryVisible by remember { mutableStateOf(false) }
    // Une image analysée par seconde peut contenir un QR plusieurs fois de suite tant qu'il reste
    // dans le champ — n'agir qu'une fois par code détecté, jusqu'à ce qu'une erreur autorise à
    // réessayer.
    var hasScanned by remember { mutableStateOf(false) }

    fun join(code: String) {
        isConnecting = true
        connectionError = null
        scope.launch {
            try {
                coordinator.join(
                    code = code,
                    deviceName = DeviceIdentity.name(context),
                    appVersion = BuildConfig.VERSION_NAME,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                connectionError = describe(error)
                hasScanned = false
            } finally {
                isConnecting = false
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Rejoindre") }) }) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            QrScannerView(
                onScan = { raw ->
                    if (hasScanned || isConnecting) return@QrScannerView
                    val code = JoinLink.parse(raw) ?: raw.filter(Char::isDigit).take(6)
                    if (code.length == 6) {
                        hasScanned = true
                        pairingCode = code
                        join(code)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            connectionError?.let {
                Banner(message = it, modifier = Modifier.align(Alignment.TopCenter).padding(Space.lg))
            }

            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            PrimaryButton(
                text = "Saisir un code",
                enabled = !isConnecting,
                onClick = { isManualEntryVisible = true },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Space.lg)
                        .padding(bottom = LocalFloatingNavBarHeight.current),
            )
        }
    }

    if (isManualEntryVisible) {
        ManualCodeEntrySheet(
            code = pairingCode,
            onCodeChange = { pairingCode = it },
            isConnecting = isConnecting,
            onDismiss = { isManualEntryVisible = false },
            onJoin = {
                isManualEntryVisible = false
                hasScanned = true
                join(pairingCode)
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ManualCodeEntrySheet(
    code: String,
    onCodeChange: (String) -> Unit,
    isConnecting: Boolean,
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text(
                "Saisis le code à 6 chiffres affiché sur l'appareil qui partage la partie.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = code,
                onValueChange = { raw -> onCodeChange(raw.filter(Char::isDigit).take(6)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp,
                    ),
                singleLine = true,
                enabled = !isConnecting,
            )
            PrimaryButton(
                text = "Rejoindre",
                enabled = code.length == 6 && !isConnecting,
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun describe(error: Throwable): String =
    when (error) {
        is SupabaseTransportError.GameNotFound ->
            "Aucune partie ne correspond à ce code. Vérifie qu'il est bien à jour."
        is LiveSession.SessionError.NoResponseFromHost -> "L'hôte n'a pas répondu — vérifie le code."
        else -> "Connexion impossible. Réessaie."
    }
