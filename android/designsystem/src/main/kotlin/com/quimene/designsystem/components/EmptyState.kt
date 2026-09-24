package com.quimene.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.quimene.designsystem.tokens.IconSize
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space

/**
 * Un message + une action (charte §5.5). Miroir d'`EmptyState.swift` — `icon` prend un
 * `ImageVector` (Material Symbols) plutôt qu'un nom SF Symbol, seule différence de type liée à
 * la plateforme. Pas de contrainte de hauteur maximale rigide : Compose ne tronque pas un
 * `Text` par défaut (contrairement à SwiftUI sans `.fixedSize`), donc pas besoin de reproduire
 * le `maxHeight: 160` — non contraignant côté Swift lui-même une fois `fixedSize` posé.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(IconSize.xl),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionTitle != null && onAction != null) {
            PrimaryButton(
                text = actionTitle,
                onClick = onAction,
                size = AppButtonSize.Medium,
            )
        }
    }
}
