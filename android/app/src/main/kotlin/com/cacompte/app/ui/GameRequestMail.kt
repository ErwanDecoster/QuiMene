package com.cacompte.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cacompte.app.R

/**
 * Doc utilisateur — demande d'ajout d'un jeu : pas de formulaire ni de backend dédié, un e-mail
 * préempli suffit. Miroir de `GameRequestMail.swift`, adapté à l'idiome Android (`ACTION_SENDTO`
 * + extras plutôt qu'une URL `mailto:` reconstruite à la main). `context.getString(...)`, pas
 * `stringResource()` (Compose) — cet objet n'est jamais appelé depuis un contexte composable.
 */
object GameRequestMail {
    const val RECIPIENT = "contact@erwan-decoster.com"

    /** Retourne `false` si aucune application ne peut traiter l'intention (aucun client mail
     * installé) — laisse l'appelant proposer un repli plutôt qu'un tap sans effet visible. */
    fun open(
        context: Context,
        searchTerm: String? = null,
    ): Boolean =
        try {
            context.startActivity(intent(context, searchTerm))
            true
        } catch (error: ActivityNotFoundException) {
            false
        }

    private fun intent(
        context: Context,
        searchTerm: String?,
    ): Intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.demande_d_ajout_d_un_jeu_ca_compte))
            putExtra(Intent.EXTRA_TEXT, body(context, searchTerm))
        }

    private fun body(
        context: Context,
        searchTerm: String?,
    ): String {
        val intro =
            if (!searchTerm.isNullOrEmpty()) {
                context.getString(R.string.le_jeu_que_je_cherche_value1, searchTerm)
            } else {
                context.getString(R.string.le_jeu_que_je_souhaite_voir_ajoute)
            }
        val rulesPrompt = context.getString(R.string.regles_ou_lien_vers_les_regles_facultatif)
        return "$intro\n\n\n$rulesPrompt\n\n"
    }
}
