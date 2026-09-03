package com.cacompte.app.livesync

import android.net.Uri

/**
 * Miroir de `JoinLink.swift` — le lien encodé dans le QR de partage (`QrCodeView` côté hôte) et
 * décodé au scan (`com.google.mlkit.vision.codescanner.GmsBarcodeScanning` côté pair). Contenu
 * minimal : le code d'appairage lui-même, `SupabaseTransport.resolveGame(code)`
 * résout tout le reste — ce lien ne fait que remplacer la frappe des 6 chiffres. Même schéma
 * personnalisé `cacompte://join?code=XXXXXX` qu'Apple, pas un lien universel `https://` (même
 * raison : pas de nom de domaine à posséder/vérifier) — un code généré par l'un ou l'autre app
 * reste scannable par l'autre plateforme.
 */
object JoinLink {
    private const val SCHEME = "cacompte"
    private const val HOST = "join"

    fun url(pairingCode: String): String = "$SCHEME://$HOST?code=$pairingCode"

    fun parse(raw: String): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        return uri.getQueryParameter("code")
    }
}
