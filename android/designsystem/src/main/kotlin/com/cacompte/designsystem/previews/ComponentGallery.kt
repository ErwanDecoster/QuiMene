package com.cacompte.designsystem.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cacompte.designsystem.components.Banner
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.Chip
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.theme.CaCompteTheme
import com.cacompte.designsystem.tokens.LocalAppColors

/**
 * Galerie `Card`/`Chip`/`Banner`/`EmptyState` sur fond `neutral/bg` — équivalent de
 * `ComponentGallery.swift`.
 */
@Composable
private fun ComponentGalleryContent() {
    val colors = LocalAppColors.current
    var selected by remember { mutableStateOf(false) }

    Surface(color = colors.neutralBg) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Skyjo", style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(title = "4 joueurs", isSelected = selected, onClick = { selected = !selected })
                Chip(title = "Seuil 100", isSelected = !selected, onClick = { selected = !selected })
            }
            Banner(
                message = "Manche validée",
                actionTitle = "Annuler",
                onAction = {},
            )
            EmptyState(
                icon = Icons.Filled.Star,
                message = "Aucune partie enregistrée",
                actionTitle = "Commencer une partie",
                onAction = {},
            )
        }
    }
}

@Preview(name = "Composants — clair", showBackground = true)
@Composable
private fun ComponentGalleryLightPreview() {
    CaCompteTheme(darkTheme = false) { ComponentGalleryContent() }
}

@Preview(name = "Composants — sombre", showBackground = true)
@Composable
private fun ComponentGalleryDarkPreview() {
    CaCompteTheme(darkTheme = true) { ComponentGalleryContent() }
}

@Preview(name = "Composants — AX5 (fontScale 1.6)", showBackground = true, fontScale = 1.6f)
@Composable
private fun ComponentGalleryAx5Preview() {
    CaCompteTheme(darkTheme = false) { ComponentGalleryContent() }
}
