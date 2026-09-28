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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.ui.GameRequestMail
import com.quimene.designsystem.components.BackButton
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space

/**
 * Miroir de `SettingsView.swift` (doc 11, étape E) — langue et demande d'ajout d'un jeu.
 * **`iCloudSyncEnabled` n'est délibérément pas repris** : aucun réglage Android ne le justifie
 * sans équivalent CloudKit (doc 11 « Synchronisation »).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
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
                    BackButton(onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
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
                    stringResource(R.string.ouvre_les_reglages_systeme_pour_choisir_la_langue_de_l_app),
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
