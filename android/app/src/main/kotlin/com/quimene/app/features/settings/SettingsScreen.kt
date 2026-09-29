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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.ui.GameRequestMail
import com.quimene.designsystem.components.BackButton
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.ListContainer
import com.quimene.designsystem.components.ListRowDivider
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space

/**
 * Miroir de `SettingsView.swift` (doc 11, étape E) — langue, demande d'ajout d'un jeu, assistance et
 * politique de confidentialité.
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

            // Play Console (règles sur les données utilisateur), comme l'App Store (5.1.1(i)) : la
            // politique de confidentialité doit être accessible depuis l'app elle-même.
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                val isFrench = currentLocale.language == "fr"
                ListContainer(modifier = Modifier.fillMaxWidth()) {
                    WebsiteRow(
                        title = stringResource(R.string.assistance),
                        icon = Icons.AutoMirrored.Filled.Help,
                        url = if (isFrench) SUPPORT_URL_FR else SUPPORT_URL_EN,
                    )
                    ListRowDivider()
                    WebsiteRow(
                        title = stringResource(R.string.politique_de_confidentialite),
                        icon = Icons.Filled.PrivacyTip,
                        url = if (isFrench) PRIVACY_URL_FR else PRIVACY_URL_EN,
                    )
                }
                Text(
                    stringResource(R.string.ouvre_la_page_dans_ton_navigateur),
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

// Le site (`website/`) n'existe qu'en français et en anglais : les autres langues de l'app ouvrent
// la version anglaise. Mêmes adresses que `SettingsView.swift`.
private const val SUPPORT_URL_FR = "https://quimene.vercel.app/assistance"
private const val SUPPORT_URL_EN = "https://quimene.vercel.app/en/support"
private const val PRIVACY_URL_FR = "https://quimene.vercel.app/confidentialite"
private const val PRIVACY_URL_EN = "https://quimene.vercel.app/en/privacy"

@Composable
private fun WebsiteRow(
    title: String,
    icon: ImageVector,
    url: String,
) {
    val colors = LocalAppColors.current
    val uriHandler = LocalUriHandler.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Aucun navigateur (appareil verrouillé par une entreprise) : le tap reste sans
                // effet plutôt que de fermer l'app.
                .clickable { runCatching { uriHandler.openUri(url) } }
                .padding(Space.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.textPrimary)
        Icon(icon, contentDescription = null, tint = colors.textSecondary)
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
