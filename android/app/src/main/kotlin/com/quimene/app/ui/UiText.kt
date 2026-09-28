package com.quimene.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Texte produit hors composition (ViewModel, coordinateur) et résolu seulement à l'affichage,
 * dans la langue courante — plutôt qu'une `String` rédigée d'avance en français, ou un `Context`
 * gardé par un ViewModel pour la traduire.
 */
sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /** Texte déjà rédigé ailleurs (message d'erreur transmis tel quel). */
    data class Verbatim(
        val text: String,
    ) : UiText
}

@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.Resource -> stringResource(id, *args.toTypedArray())
        is UiText.Verbatim -> text
    }

fun UiText.asString(context: Context): String =
    when (this) {
        is UiText.Resource -> context.getString(id, *args.toTypedArray())
        is UiText.Verbatim -> text
    }
