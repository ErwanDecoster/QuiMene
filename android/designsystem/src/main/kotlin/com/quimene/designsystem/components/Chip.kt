package com.quimene.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Radius
import com.quimene.designsystem.tokens.Touch

/**
 * Miroir de `Chip.swift`. L'état sélectionné est doublé par le fond, le libellé **et** la
 * bordure — jamais la seule couleur (charte §13). Zone tactile découplée du visuel : le dessin
 * reste à 32 dp, la cible s'étend à [Touch.minimum] (48 dp) via `defaultMinSize` sur le
 * conteneur cliquable, sans agrandir le dessin.
 */
@Composable
fun Chip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Radius.sm)

    val backgroundColor = if (isSelected) colors.brandInk.copy(alpha = 0.12f) else colors.neutralFill
    val labelColor = if (isSelected) colors.brandInk else colors.textSecondary

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = Touch.minimum)
                .selectable(selected = isSelected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .height(32.dp)
                    .clip(shape)
                    .background(backgroundColor)
                    .let {
                        if (isSelected) it.border(1.5.dp, colors.brandInk, shape) else it
                    }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
    }
}
