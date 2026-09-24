package com.quimene.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.Space

/** `contentPadding` d'une liste défilante plein écran, sous la barre de navigation flottante
 * (voir [LocalFloatingNavBarHeight]) : le bas reçoit assez d'espace pour que le dernier élément
 * puisse remonter au-dessus de l'îlot plutôt que de rester durablement caché dessous — le reste
 * de la liste continue de défiler *derrière* elle (doc utilisateur).
 *
 * [systemBottomInset] doit venir du `innerPadding.calculateBottomPadding()` du `Scaffold` propre
 * à l'écran appelant — cette fonction ne le lit pas elle-même car [LocalFloatingNavBarHeight] ne
 * porte déjà que la hauteur de l'îlot *seul* (marge + contenu, sans l'inset système, déjà
 * compté une fois par ce `Scaffold`). L'appelant doit aussi retirer le bas de son propre
 * `innerPadding` de son `Modifier` extérieur (ne garder que le haut) — sinon la zone défilante
 * elle-même s'arrête avant d'atteindre l'îlot, empêchant tout défilement réellement « derrière ». */
@Composable
fun floatingNavBarContentPadding(
    systemBottomInset: Dp = 0.dp,
    base: Dp = Space.lg,
): PaddingValues =
    PaddingValues(
        start = base,
        top = base,
        end = base,
        bottom = base + systemBottomInset + LocalFloatingNavBarHeight.current,
    )
