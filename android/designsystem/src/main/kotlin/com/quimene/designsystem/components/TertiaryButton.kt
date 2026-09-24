package com.quimene.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Radius

/**
 * Action texte seule, sans fond au repos (charte §5.3). Miroir de `TertiaryButtonStyle.swift` :
 * taille par défaut **medium** (contrairement à primaire/secondaire, par défaut `large`),
 * contenu dimensionné (pas de largeur pleine), rayon `sm` au lieu de `md`, fond visible
 * uniquement au pressé.
 */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: AppButtonSize = AppButtonSize.Medium,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = LocalAppColors.current

    val backgroundColor = if (enabled && isPressed) colors.brandInk.copy(alpha = 0.08f) else Color.Transparent
    val labelColor = if (!enabled) colors.textDisabled else colors.brandInk

    Box(
        modifier =
            modifier
                .height(size.height)
                .clip(RoundedCornerShape(Radius.sm))
                .background(backgroundColor)
                .clickable(
                    enabled = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = size.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            modifier = Modifier.graphicsLayer { alpha = if (isLoading) 0f else 1f },
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.brandInk,
                strokeWidth = 2.dp,
            )
        }
    }
}
