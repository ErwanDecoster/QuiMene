package com.cacompte.designsystem.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cacompte.designsystem.tokens.LocalAppColors

/** Miroir de `AvatarSize` (`AvatarView.swift`) — petit en liste, moyen en tableau, grand en
 * détail/profil. */
enum class AvatarSize(
    val diameter: Dp,
) {
    Small(28.dp),
    Medium(44.dp),
    Large(96.dp),
}

/**
 * Miroir de `AvatarView.swift`. Toujours `clearAndSetSemantics {}` (équivalent de
 * `.accessibilityHidden(true)`) : décoratif, le nom du joueur est porté ailleurs. La bordure de
 * la couleur du joueur n'apparaît que pour un avatar photo.
 */
@Composable
fun AvatarView(
    avatar: Avatar,
    size: AvatarSize,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val playerColor = avatar.palette.color()

    Box(
        modifier =
            modifier
                .size(size.diameter)
                .clip(CircleShape)
                .let { base ->
                    if (avatar.kind is AvatarKind.Photo) base.border(2.dp, playerColor, CircleShape) else base
                }.clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        when (val kind = avatar.kind) {
            // Conservé pour compatibilité (sélection manuelle historique, doc 08) — aucune icône
            // Material Symbols n'est câblée dans cette étape, seul l'emoji généré (Emoji) est
            // exercé par le reste de l'app.
            is AvatarKind.Symbol -> {
                Box(
                    modifier = Modifier.size(size.diameter).background(playerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = kind.name, color = Color.White)
                }
            }

            is AvatarKind.Emoji -> {
                Box(
                    modifier = Modifier.size(size.diameter).background(playerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = kind.character, fontSize = (size.diameter.value * 0.6f).sp)
                }
            }

            is AvatarKind.Photo -> {
                val bitmap =
                    remember(kind.data) {
                        runCatching {
                            BitmapFactory.decodeByteArray(kind.data, 0, kind.data.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(size.diameter),
                    )
                } else {
                    Box(modifier = Modifier.size(size.diameter).background(colors.neutralFill))
                }
            }
        }
    }
}
