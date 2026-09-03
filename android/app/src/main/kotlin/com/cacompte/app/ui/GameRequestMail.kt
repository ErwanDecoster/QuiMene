package com.cacompte.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Doc utilisateur — demande d'ajout d'un jeu : pas de formulaire ni de backend dédié, un e-mail
 * préempli suffit. Miroir de `GameRequestMail.swift`, adapté à l'idiome Android (`ACTION_SENDTO`
 * + extras plutôt qu'une URL `mailto:` reconstruite à la main).
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
            context.startActivity(intent(searchTerm))
            true
        } catch (error: ActivityNotFoundException) {
            false
        }

    private fun intent(searchTerm: String?): Intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, "Demande d'ajout d'un jeu — Ça Compte")
            putExtra(Intent.EXTRA_TEXT, body(searchTerm))
        }

    private fun body(searchTerm: String?): String {
        val intro =
            if (!searchTerm.isNullOrEmpty()) {
                "Le jeu que je cherche : $searchTerm"
            } else {
                "Le jeu que je souhaite voir ajouté :"
            }
        return "$intro\n\n\nRègles ou lien vers les règles (facultatif) :\n\n"
    }
}
