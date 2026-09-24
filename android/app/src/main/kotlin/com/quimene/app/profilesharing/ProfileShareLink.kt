package com.quimene.app.profilesharing

import android.net.Uri
import java.util.UUID

/**
 * Miroir de `ProfileShareLink.swift` (doc 14) — lien qui transporte l'identifiant permanent
 * liant deux fiches joueur sur deux appareils différents. Contrairement à
 * [com.quimene.app.livesync.JoinLink] (code d'appairage éphémère, valable une soirée),
 * l'identifiant transporté ici ne périme jamais : une fois liée, une fiche reste liée jusqu'à
 * délier explicitement.
 *
 * Transporte aussi l'avatar (pas seulement le pseudo) : celui qui lie peut choisir d'adopter le
 * pseudo et l'avatar de la personne représentée. Une photo ne peut pas transiter par un QR
 * (poids, densité de scan) — `avatarKind` est transporté tel quel, mais l'adoption d'avatar
 * n'est proposée que si ce n'est pas `"photo"` (voir l'écran de confirmation).
 */
object ProfileShareLink {
    private const val SCHEME = "quimene"
    private const val HOST = "claim-profile"

    data class Payload(
        val id: UUID,
        val name: String,
        val avatarKind: String,
        val avatarValue: String,
        val paletteID: String,
    )

    fun url(
        id: UUID,
        name: String,
        avatarKind: String,
        avatarValue: String,
        paletteID: String,
    ): String =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("id", id.toString())
            .appendQueryParameter("name", name)
            .appendQueryParameter("avatarKind", avatarKind)
            .appendQueryParameter("avatarValue", avatarValue)
            .appendQueryParameter("paletteID", paletteID)
            .build()
            .toString()

    fun parse(raw: String): Payload? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        val id = uri.getQueryParameter("id")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return Payload(
            id = id,
            name = uri.getQueryParameter("name").orEmpty(),
            avatarKind = uri.getQueryParameter("avatarKind") ?: "symbol",
            avatarValue = uri.getQueryParameter("avatarValue").orEmpty(),
            paletteID = uri.getQueryParameter("paletteID") ?: "1",
        )
    }
}
