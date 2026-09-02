package com.cacompte.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Rayons de bordure (charte §5.1). Règle des coins concentriques (documentaire, pas encore un
 * point d'appel dans ce module) : un élément imbriqué utilise `rayon_parent − padding`, jamais
 * une valeur fixe — calculé au point d'usage, comme côté Swift (`Radius.swift` n'a pas non plus
 * de fonction dédiée ; iOS 26+ le fait automatiquement via `.rect(corners: .concentric)`).
 */
object Radius {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val full: Dp = 999.dp
}
