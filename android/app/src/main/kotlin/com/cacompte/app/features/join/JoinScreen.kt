package com.cacompte.app.features.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.DeviceIdentity
import com.cacompte.sync.LiveSession
import com.cacompte.sync.SupabaseTransportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Miroir de `JoinTabView.swift` (doc 09) — saisie manuelle du code à 6 chiffres uniquement (pas
 * de scan caméra dans cette version : le code reste la voie de repli d'Apple elle-même, suffisant
 * pour une première parité fonctionnelle). Une fois connecté, remplacé par [SharedMatchScreen].
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

    Scaffold(topBar = { TopAppBar(title = { Text("Rejoindre") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text(
                "Saisis le code à 6 chiffres affiché sur l'appareil qui partage la partie.",
                style = MaterialTheme.typography.bodyLarge,
                color = LocalAppColors.current.textSecondary,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { raw -> pairingCode = raw.filter(Char::isDigit).take(6) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp,
                    ),
                singleLine = true,
                enabled = !isConnecting,
            )
            connectionError?.let { Banner(message = it) }
            if (isConnecting) {
                CircularProgressIndicator()
            } else {
                PrimaryButton(
                    text = "Rejoindre",
                    enabled = pairingCode.length == 6,
                    onClick = {
                        isConnecting = true
                        connectionError = null
                        scope.launch {
                            try {
                                coordinator.join(
                                    code = pairingCode,
                                    deviceName = DeviceIdentity.name(context),
                                    appVersion = BuildConfig.VERSION_NAME,
                                )
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Exception) {
                                connectionError = describe(error)
                            } finally {
                                isConnecting = false
                            }
                        }
                    },
                )
            }
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
