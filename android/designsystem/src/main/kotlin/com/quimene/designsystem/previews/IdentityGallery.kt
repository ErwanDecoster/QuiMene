package com.quimene.designsystem.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.PlayerPalette
import com.quimene.designsystem.components.color
import com.quimene.designsystem.theme.QuiMeneTheme

/**
 * Galerie des 10 couleurs joueur et des 3 tailles d'`AvatarView` — équivalent de
 * `IdentityGallery.swift` (sans le logo, qui n'existe pas encore côté Android à cette étape).
 * Variantes clair/sombre uniquement, pas d'AX5 — même choix que la source Swift.
 */
@Composable
private fun IdentityGalleryContent() {
    Surface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (index in 1..10) {
                    val palette = PlayerPalette(index)
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(palette.color()),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarView(avatar = Avatar.generated("Alice"), size = AvatarSize.Small)
                AvatarView(avatar = Avatar.generated("Bob"), size = AvatarSize.Medium)
                AvatarView(avatar = Avatar.generated("Chloé"), size = AvatarSize.Large)
                AvatarView(
                    avatar = Avatar(kind = AvatarKind.Emoji("🎲"), palette = PlayerPalette(3)),
                    size = AvatarSize.Medium,
                )
            }
        }
    }
}

@Preview(name = "Identité — clair", showBackground = true)
@Composable
private fun IdentityGalleryLightPreview() {
    QuiMeneTheme(darkTheme = false) { IdentityGalleryContent() }
}

@Preview(name = "Identité — sombre", showBackground = true)
@Composable
private fun IdentityGalleryDarkPreview() {
    QuiMeneTheme(darkTheme = true) { IdentityGalleryContent() }
}
