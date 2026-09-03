package com.cacompte.sync

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Doc 09 / ADR-0014 — historiquement le seam qui rendait [LiveSession] indifférente à plusieurs
 * transports interchangeables (Wi-Fi/BLE). Depuis le passage à Supabase Realtime (seul transport
 * restant, voir `SupabaseTransport`), ce polymorphisme n'a plus d'utilité — mais [LiveSession] ne
 * connaît toujours que [TransportSession] (jamais `SupabaseTransport` directement), donc ce seam
 * reste : juste réduit à ce qui sert encore.
 *
 * Une connexion établie. Transporte des octets déjà chiffrés ([WireCodec] s'en charge dans
 * [LiveSession]) — cette interface ne connaît ni [WireMessage] ni le code d'appairage.
 */
interface TransportSession {
    val incoming: Flow<ByteArray>

    suspend fun send(data: ByteArray)

    suspend fun close()
}

/** Doc 09 — contexte d'annonce plafonné : ce qu'un pair voit avant de rejoindre, jamais
 * l'instantané de partie. Résolu via `cacompte_open_games` (Supabase) à partir d'un code
 * d'appairage, plutôt que découvert par scan réseau/Bluetooth. */
data class DiscoveredHost(
    /** L'identifiant de la **session de partage** ([WireMessage.sessionID]), pas de la partie
     * courante — c'est lui qui adresse le canal Realtime (`session:<id>`), stable même si l'hôte
     * enchaîne une autre partie après coup (doc 09 « Fin de partie »). */
    val id: UUID,
    val deviceName: String,
    val gameID: String,
    val participantCount: Int,
    val platform: WireMessage.Platform,
)
