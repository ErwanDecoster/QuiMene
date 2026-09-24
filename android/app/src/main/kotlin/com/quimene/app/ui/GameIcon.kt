package com.quimene.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Filter6
import androidx.compose.material.icons.filled.Filter7
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `GameDefinition.symbol` (`:domain`) porte un nom de SF Symbol (`apple/QuiMeneKit/Sources/
 * Catalog`, ex. `"suit.club.fill"`) — sans équivalent direct côté Android, où les icônes se
 * réfèrent par symbole Compose, pas par chaîne. Cette table associe donc chaque jeu (par `id`,
 * pas par `symbol`) à l'icône Material la plus proche visuellement/sémantiquement du symbole SF
 * utilisé par l'app Apple pour ce même jeu, vérifiée une à une contre `GamesTabView.swift`.
 *
 * **Deux jeux n'ont pas d'équivalent honnête** : `belote` (`suit.club.fill`) et `tarot`
 * (`suit.spade.fill`) utilisent une couleur/symbole de carte à jouer — Material Icons (le jeu
 * d'icônes historique de Google, seul disponible ici) n'a aucune icône de couleur de carte
 * (trèfle/pique/cœur/carreau). [Diamond] et [Extension] sont les meilleurs repères disponibles,
 * pas une tentative de faire croire à une parité pixel avec Apple sur ces deux jeux précis.
 */
private val gameIconsByID: Map<String, ImageVector> =
    mapOf(
        "belote" to Icons.Filled.Diamond, // suit.club.fill — pas d'icône de couleur de carte côté Material.
        "cornhole" to Icons.Filled.GpsFixed, // circle.grid.cross.fill
        "flip7" to Icons.Filled.Filter7, // 7.circle.fill
        "jeu-libre" to Icons.Filled.EditNote, // square.and.pencil
        "molkky" to Icons.Filled.Adjust, // target
        "odin" to Icons.Filled.Shield, // shield.fill
        "petanque" to Icons.Filled.Circle, // smallcircle.circle.fill
        "pictionary" to Icons.Filled.Brush, // paintbrush.fill
        "qwixx" to Icons.Filled.Numbers, // number.square.fill
        "rami" to Icons.Filled.Layers, // rectangle.stack.fill
        "rummikub" to Icons.Filled.GridView, // square.stack.3d.up.fill
        "scrabble" to Icons.Filled.Abc, // textformat.abc
        "six-qui-prend" to Icons.Filled.Filter6, // 6.circle.fill
        "skyjo" to Icons.Filled.GridOn, // square.grid.3x3.fill
        "tarot" to Icons.Filled.Extension, // suit.spade.fill — pas d'icône de couleur de carte côté Material.
        "times-up" to Icons.Filled.HourglassEmpty, // hourglass
        "triominos" to Icons.Filled.ChangeHistory, // triangle.fill
        "trivial-pursuit" to Icons.Filled.Help, // questionmark.circle.fill
        "wizard" to Icons.Filled.AutoAwesome, // wand.and.stars
        "yams" to Icons.Filled.Casino, // dice.fill
    )

/** Repli sur [Icons.Filled.Casino] pour un `id` absent de la table — ne devrait arriver que si
 * `spec/games/` gagne un nouveau jeu sans mise à jour de cette table (échec silencieux mais sans
 * conséquence : juste une icône générique, jamais un score erroné). */
fun gameIcon(gameID: String): ImageVector = gameIconsByID[gameID] ?: Icons.Filled.Casino
