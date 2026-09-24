package com.quimene.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.LocalIsDarkTheme
import com.quimene.designsystem.tokens.Radius
import com.quimene.designsystem.tokens.Space

/**
 * Miroir du `List`/`Section` système d'Apple (charte §5.5, « Ligne de liste ») — un seul panneau
 * `elev/1` (tonal 1 dp + ombre L1, comme [Card]) autour d'un groupe de lignes, séparées par
 * [ListRowDivider], plutôt qu'une carte individuellement élevée par ligne. Doc utilisateur —
 * remontée : le fond ajouté à [Card] devait vivre sur le conteneur de la liste, pas sur chaque
 * élément. Utilisé pour Joueurs, Jeux (parties en cours + catalogue) et Historique — les listes
 * denses d'un seul type de ligne ; [Card] reste pertinent pour du contenu isolé (podium, faits
 * marquants) qui n'est pas un groupe de lignes homogènes.
 */
@Composable
fun ListContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val isDark = LocalIsDarkTheme.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        color = colors.neutralSurface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = if (isDark) BorderStroke(1.dp, colors.neutralBorder) else null,
    ) {
        Column(content = content)
    }
}

/** Séparateur 0,5 pt `neutral/border`, en retrait de 16 pt à gauche (charte §5.5) — entre deux
 * lignes d'un même [ListContainer], jamais avant la première ni après la dernière. */
@Composable
fun ListRowDivider(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    HorizontalDivider(
        modifier = modifier.padding(start = Space.lg),
        thickness = 0.5.dp,
        color = colors.neutralBorder,
    )
}
