package com.quimene.app.ui

import androidx.annotation.StringRes
import com.quimene.app.R
import com.quimene.designsystem.components.PlayerPalette

/** Nom de la couleur, annoncé par TalkBack dans le choix de couleur d'une fiche — traduit comme
 * `PlayerPalette.accessibilityName` côté Apple (`:designsystem` n'a pas accès aux textes de l'app). */
@get:StringRes
val PlayerPalette.nameRes: Int
    get() =
        when (index) {
            1 -> R.string.azur
            2 -> R.string.ambre
            3 -> R.string.emeraude
            4 -> R.string.magenta
            5 -> R.string.ardoise
            6 -> R.string.cyan
            7 -> R.string.vermillon
            8 -> R.string.violet
            9 -> R.string.olive
            else -> R.string.rose
        }
