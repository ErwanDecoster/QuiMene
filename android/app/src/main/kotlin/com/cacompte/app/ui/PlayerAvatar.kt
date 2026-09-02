package com.cacompte.app.ui

import com.cacompte.designsystem.components.Avatar
import com.cacompte.designsystem.components.AvatarKind
import com.cacompte.designsystem.components.PlayerPalette
import com.cacompte.store.LeaderboardEntry
import com.cacompte.store.ParticipantEntity
import com.cacompte.store.PlayerEntity

/** [PlayerEntity] (`:store`) → [Avatar] (`:designsystem`) — vit dans `:app`, le seul module qui
 * dépend des deux ; ni l'un ni l'autre ne doit connaître le type de l'autre. */
fun PlayerEntity.toAvatar(): Avatar {
    val kind =
        when (avatarKind) {
            "photo" -> avatarPhoto?.let { AvatarKind.Photo(it) } ?: AvatarKind.Emoji(Avatar.curatedEmoji.first())
            "symbol" -> AvatarKind.Symbol(avatarValue)
            else -> AvatarKind.Emoji(avatarValue.ifEmpty { Avatar.curatedEmoji.first() })
        }
    return Avatar(kind = kind, palette = PlayerPalette(paletteID.toIntOrNull() ?: 1))
}

/** Même conversion, à partir du snapshot figé sur un participant plutôt que de la fiche joueur
 * (l'un ou l'autre n'est pas toujours disponible selon l'écran — historique/résultats/partie en
 * cours n'ont que le snapshot). */
fun ParticipantEntity.toAvatar(): Avatar =
    PlayerEntity(
        nickname = nicknameSnapshot,
        avatarKind = avatarKindSnapshot,
        avatarValue = avatarValueSnapshot,
        paletteID = paletteIDSnapshot,
    ).toAvatar()

/** Même conversion, à partir d'une ligne de classement. */
fun LeaderboardEntry.toAvatar(): Avatar =
    PlayerEntity(
        nickname = name,
        avatarKind = avatarKind,
        avatarValue = avatarValue,
        avatarPhoto = avatarPhoto,
        paletteID = paletteID,
    ).toAvatar()
