package com.quimene.designsystem.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.ButtonHeight

/** Miroir de `ButtonSize.swift`. `small` réutilise le padding horizontal de `medium` (16) — la
 * charte ne spécifie de padding que pour large/medium (§3.3). */
enum class AppButtonSize {
    Large,
    Medium,
    Small,
    ;

    val height: Dp
        get() =
            when (this) {
                Large -> ButtonHeight.large
                Medium -> ButtonHeight.medium
                Small -> ButtonHeight.small
            }

    val horizontalPadding: Dp
        get() =
            when (this) {
                Large -> 24.dp
                Medium, Small -> 16.dp
            }
}
