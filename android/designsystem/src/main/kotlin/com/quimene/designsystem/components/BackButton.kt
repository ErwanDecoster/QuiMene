package com.quimene.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quimene.designsystem.R

/**
 * Bouton de navigation « retour » de la barre du haut, commun à tous les écrans poussés : icône
 * auto-mirrorée (langues de droite à gauche) et description TalkBack traduite, plutôt qu'un
 * `IconButton` + `Icon` réécrit à chaque écran. [contentDescription] ne se précise que lorsque
 * le bouton ferme quelque chose plutôt que de revenir en arrière (« Fermer la recherche »).
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String = stringResource(R.string.ds_back),
) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription)
    }
}
