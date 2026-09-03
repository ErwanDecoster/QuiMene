package com.cacompte.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.cacompte.designsystem.tokens.Space

/** `contentPadding` d'une liste défilante plein écran, sous la barre de navigation flottante
 * (voir [LocalFloatingNavBarHeight]) : le bas reçoit assez d'espace pour que le dernier élément
 * puisse remonter au-dessus de l'îlot plutôt que de rester durablement caché dessous — le reste
 * de la liste continue de défiler *derrière* elle (doc utilisateur). */
@Composable
fun floatingNavBarContentPadding(base: Dp = Space.lg): PaddingValues =
    PaddingValues(start = base, top = base, end = base, bottom = base + LocalFloatingNavBarHeight.current)
