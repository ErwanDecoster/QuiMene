package com.quimene.designsystem.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.components.SecondaryButton
import com.quimene.designsystem.components.TertiaryButton
import com.quimene.designsystem.theme.QuiMeneTheme

/**
 * Galerie des 3 styles de bouton (défaut/désactivé/chargement) — équivalent de
 * `ButtonGallery.swift`, variantes clair/sombre/AX5 (`fontScale`) via les 3 `@Preview` ci-dessous.
 */
@Composable
private fun ButtonGalleryContent() {
    Surface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Valider la manche", onClick = {})
            PrimaryButton(text = "Valider la manche", onClick = {}, enabled = false)
            PrimaryButton(text = "Valider la manche", onClick = {}, isLoading = true)
            SecondaryButton(text = "Annuler", onClick = {})
            SecondaryButton(text = "Annuler", onClick = {}, enabled = false)
            TertiaryButton(text = "Ignorer", onClick = {})
            TertiaryButton(text = "Ignorer", onClick = {}, enabled = false)
        }
    }
}

@Preview(name = "Boutons — clair", showBackground = true)
@Composable
private fun ButtonGalleryLightPreview() {
    QuiMeneTheme(darkTheme = false) { ButtonGalleryContent() }
}

@Preview(name = "Boutons — sombre", showBackground = true)
@Composable
private fun ButtonGalleryDarkPreview() {
    QuiMeneTheme(darkTheme = true) { ButtonGalleryContent() }
}

@Preview(name = "Boutons — AX5 (fontScale 1.6)", showBackground = true, fontScale = 1.6f)
@Composable
private fun ButtonGalleryAx5Preview() {
    QuiMeneTheme(darkTheme = false) { ButtonGalleryContent() }
}
