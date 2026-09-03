package com.cacompte.app.features.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space

/**
 * Miroir de `JoinTabView.swift` (doc 09) — repli honnête plutôt qu'un flux de scan QR qui
 * n'aurait rien à rejoindre : le transport temps réel (`:sync`, Supabase Realtime, ADR-0016)
 * n'est pas encore construit (étape F du portage). L'onglet reste visible, à la même position
 * que côté Apple, pour que la barre de navigation corresponde dès maintenant.
 */
@Composable
fun JoinScreen() {
    val colors = LocalAppColors.current
    Scaffold(topBar = { TopAppBar(title = { Text("Rejoindre") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "Le partage de partie en direct arrive dans une prochaine version.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
