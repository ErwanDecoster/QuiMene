package com.cacompte.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring

/**
 * Durées (charte §9.1), en millisecondes — mêmes valeurs que côté Swift (`Motion.swift`).
 * Rien entre [screen] et [celebrate] : l'entre-deux paraîtrait lent sans paraître intentionnel.
 * [celebrate] est réservé à l'écran de résultats (§9.2).
 */
object MotionDuration {
    val instant: Int = 0
    val fast: Int = 120
    val micro: Int = 180
    val standard: Int = 280
    val screen: Int = 350
    val celebrate: Int = 650
}

/**
 * Courbes (charte §9.2). Valeurs officielles du token Material 3 pour l'entrée/la sortie de
 * contenu — pas d'équivalent système "snappy"/"smooth"/"easeOut" iOS, ce sont deux jeux de
 * courbes distincts par plateforme, volontairement.
 */
object MotionEasing {
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}

/** Ressort de célébration (charte §9.2, tableau des courbes) — écran de résultats uniquement. */
fun celebrationSpring() =
    spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 300f,
    )
