package com.cacompte.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Radius
import com.cacompte.designsystem.tokens.Space

/**
 * Bandeau flottant (`elev/3`), une action maximum. Miroir de `Banner.swift`, à une différence de
 * plateforme assumée : iOS utilise `.regularMaterial` (flou système, sans équivalent Compose
 * standard) ; Android n'a jamais utilisé de flou pour l'élévation — c'est le rendu tonal + ombre
 * déjà spécifié par la charte pour `elev/3` (§5.2 : `surfaceContainerHigh`, tonal 6 dp + ombre
 * L3), porté ici par le paramètre `shadowElevation` de [Surface]. La disparition automatique
 * (4 s, ou 8 s avec action) reste la responsabilité de l'appelant, comme côté Swift.
 */
@Composable
fun Banner(
    message: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = colors.neutralSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (actionTitle != null && onAction != null) {
                Text(
                    text = actionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.brandInk,
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }
    }
}
