package com.quimene.app.features.livematch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quimene.app.R
import com.quimene.designsystem.tokens.IconSize
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space

/** Doc 16 — « Moi » sur la place qui correspond à mon profil, un lien sur celles de mes amis.
 * Miroir de `ScoreBoardView.ProfileBadge` (Swift). */
enum class ProfileBadge { Me, Friend }

/** « Moi » accolé au pseudo, partout où une partie liste ses joueurs. Miroir de `MeBadge`. */
@Composable
fun MeBadge(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Text(
        stringResource(R.string.moi),
        style = MaterialTheme.typography.labelMedium,
        color = colors.brandInk,
        maxLines = 1,
        modifier =
            modifier
                .clip(CircleShape)
                .background(colors.brandInk.copy(alpha = 0.12f))
                .padding(horizontal = Space.xs, vertical = 2.dp),
    )
}

@Composable
fun ProfileBadgeView(
    badge: ProfileBadge?,
    modifier: Modifier = Modifier,
) {
    when (badge) {
        ProfileBadge.Me -> MeBadge(modifier)
        // Même signe que dans la liste des joueurs : une fiche liée à un ami.
        ProfileBadge.Friend ->
            Icon(
                Icons.Filled.Link,
                contentDescription = stringResource(R.string.ami_lie),
                tint = LocalAppColors.current.textTertiary,
                modifier = modifier.size(IconSize.sm),
            )
        null -> Unit
    }
}
