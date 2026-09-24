package com.quimene.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Radius

/**
 * Action secondaire — fond transparent, bordure 1,5 dp (charte §5.3). Miroir de
 * `SecondaryButtonStyle.swift` : pas d'effet d'échelle au pressé (contrairement au primaire).
 */
@Composable
fun SecondaryButton(
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
            isPressed && enabled -> colors.brandInk.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    val borderColor =
        when {
            !enabled -> colors.neutralBorder
            isPressed -> colors.brandInk
            else -> colors.neutralBorderStrong
        }
    val labelColor = if (!enabled) colors.textDisabled else colors.brandInk

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(size.height)
                .clip(RoundedCornerShape(Radius.md))
                .background(backgroundColor)
                .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(Radius.md))
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
