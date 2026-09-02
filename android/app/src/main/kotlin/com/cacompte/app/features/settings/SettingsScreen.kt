package com.cacompte.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.AppSettings
import kotlinx.coroutines.launch

/**
 * Miroir minimal de `SettingsView.swift` (doc 11 étape E) — seul le mode de tri des joueurs est
 * porté. **`iCloudSyncEnabled` n'est délibérément pas repris** : aucun réglage Android ne le
 * justifie sans équivalent CloudKit (voir [[android_port_progress]]), et le partage de profil/live
 * (doc 09/14) dépend de `:sync`, hors périmètre.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val sortMode by container.appSettings.playerSortMode.collectAsState()
    val colors = LocalAppColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(Space.lg)) {
            Text("Ordre des joueurs", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            for (mode in AppSettings.PlayerSortMode.entries) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = sortMode == mode,
                        onClick = { scope.launch { container.appSettings.setPlayerSortMode(mode) } },
                    )
                    Text(
                        text =
                            if (mode ==
                                AppSettings.PlayerSortMode.Automatic
                            ) {
                                "Automatique (habitués d'abord)"
                            } else {
                                "Manuel"
                            },
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
