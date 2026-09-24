package com.quimene.app.livesync

import android.net.Uri

/**
 * Miroir de `JoinLink.swift` — le lien encodé dans le QR de partage (`QrCodeView` côté hôte) et
 * décodé au scan (`QrScannerView` côté pair). Contenu minimal : le code d'appairage lui-même,
 * `SupabaseSessionBackend.resolve(code)`
 * résout tout le reste — ce lien ne fait que remplacer la frappe des 6 chiffres. Même schéma
 * personnalisé `quimene://join?code=XXXXXX` qu'Apple, pas un lien universel `https://` (même
 * raison : pas de nom de domaine à posséder/vérifier) — un code généré par l'un ou l'autre app
 * reste scannable par l'autre plateforme.
 */
object JoinLink {
    private const val SCHEME = "quimene"
    private const val HOST = "join"

    fun url(pairingCode: String): String = "$SCHEME://$HOST?code=$pairingCode"

    fun parse(raw: String): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        return uri.getQueryParameter("code")
    }
}
