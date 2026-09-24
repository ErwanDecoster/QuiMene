package com.quimene.app.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.ui.GameRequestMail
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.store.AppSettings
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sortMode by container.appSettings.playerSortMode.collectAsState()
    val colors = LocalAppColors.current
    var isPresentingMailFallback by remember { mutableStateOf(false) }

    // Locale effectivement appliquée (reflète déjà un réglage par app posé via Réglages système
    // > Qui Mène ? > Langue, pas seulement la langue système) — lue via LocalConfiguration
    // (observable en composition), pas Locale.getDefault() (lint NonObservableLocale).
    val currentLocale = LocalConfiguration.current.locales[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reglages)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Column {
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
                                    stringResource(R.string.manuel)
                                },
                            color = colors.textPrimary,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Card(modifier = Modifier.fillMaxWidth().clickable { openAppLocaleSettings(context) }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.langue), color = colors.textPrimary)
                        Text(
                            currentLocale.getDisplayName(currentLocale).replaceFirstChar { it.uppercase() },
                            color = colors.textSecondary,
                        )
                    }
                }
                Text(
                    // Doc utilisateur — le contenu des jeux (noms, descriptions) est déjà
                    // disponible en 5 langues (spec/games/*.json) et suit ce réglage
                    // immédiatement ; le reste de l'interface reste en français quelle que soit
                    // la langue choisie ici (étape G du portage, pas encore faite).
                    "Ouvre les réglages système pour choisir la langue des jeux (noms, descriptions). " +
                        "Le reste de l'application reste en français pour l'instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            if (!GameRequestMail.open(context)) isPresentingMailFallback = true
                        },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.demander_l_ajout_d_un_jeu), color = colors.textPrimary)
                        Icon(Icons.Filled.Email, contentDescription = null, tint = colors.textSecondary)
                    }
                }
                Text(
                    stringResource(R.string.suggere_un_jeu_a_ajouter_a_l_app_par_e_mail),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }

    if (isPresentingMailFallback) {
        AlertDialog(
            onDismissRequest = { isPresentingMailFallback = false },
            title = { Text(stringResource(R.string.aucune_messagerie_configuree)) },
            text = {
                Text(
                    stringResource(
                        R.string.envoie_ta_demande_a_value1_depuis_l_application_de_ton_choix,
                        GameRequestMail.RECIPIENT,
                    ),
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                TextButton(onClick = { isPresentingMailFallback = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

private fun openAppLocaleSettings(context: Context) {
    val action =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Settings.ACTION_APP_LOCALE_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
    val intent = Intent(action, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent)
}
