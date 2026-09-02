package com.cacompte.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tailles d'affichage des icônes (charte §4). Rien sous [sm] (16 dp) — plus petit, un trait de
 * 1,75 px sur grille 24 tombe sous le pixel physique et devient flou. */
object IconSize {
    val sm: Dp = 16.dp
    val md: Dp = 20.dp
    val base: Dp = 24.dp
    val lg: Dp = 28.dp
    val xl: Dp = 32.dp
}

/**
 * Zone tactile (charte §6). **48 dp sur Android**, pas 44 pt comme iOS — la zone tactile est
 * découplée du visuel : on agrandit la cible via `Modifier.sizeIn`/`minimumInteractiveComponentSize`,
 * jamais le dessin.
 */
object Touch {
    val minimum: Dp = 48.dp
    val gap: Dp = 8.dp
}

/** Hauteurs de bouton (charte §5.3). */
object ButtonHeight {
    val large: Dp = 52.dp
    val medium: Dp = 44.dp
    val small: Dp = 32.dp
}

/** Pavé numérique (charte §6) — tokens de charte conservés même si aucun composant `ScoreField`
 * n'est construit à cette étape (voir plan : saisie à revalider à l'étape E, ADR-0013). */
object Keypad {
    val key: Dp = 56.dp
    val gap: Dp = 12.dp
}
