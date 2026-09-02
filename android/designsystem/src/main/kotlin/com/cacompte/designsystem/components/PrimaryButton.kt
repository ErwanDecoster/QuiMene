package com.cacompte.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.MotionDuration
import com.cacompte.designsystem.tokens.Radius

/**
 * Action principale d'un écran — une seule visible à la fois (charte §5.3). Miroir de
 * `PrimaryButtonStyle.swift` : fond plein `brand/ink`, libellé `onBrandInk` (blanc en clair,
 * `neutral/bg` en sombre — jamais blanc pur sur un fond clair côté sombre), échelle 0,97 au
 * pressé, largeur pleine, chargement = libellé invisible + indicateur, largeur figée.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: AppButtonSize = AppButtonSize.Large,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = LocalAppColors.current

    val backgroundColor =
        when {
            !enabled -> colors.neutralFill
            isPressed -> colors.brandInkPressed
            else -> colors.brandInk
        }
    val labelColor = if (!enabled) colors.textDisabled else colors.onBrandInk

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = MotionDuration.fast, easing = LinearEasing),
        label = "primaryButtonScale",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.fillMaxWidth()
                .height(size.height)
                .clip(RoundedCornerShape(Radius.md))
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
                color = labelColor,
                strokeWidth = 2.dp,
            )
        }
    }
}
