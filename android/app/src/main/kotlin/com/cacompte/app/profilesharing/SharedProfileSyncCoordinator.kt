package com.cacompte.app.profilesharing

import com.cacompte.domain.model.SharedMatchSummaryPayload
import com.cacompte.store.MatchRepository
import com.cacompte.store.PlayerRepository
import com.cacompte.sync.SharedMatchSummaryRow
import com.cacompte.sync.SharedProfileTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Miroir de `SharedProfileSyncCoordinator.swift` (doc 14, phase 2) — pousse le résumé d'une
 * partie tout juste conclue vers chaque participant lié à l'installation d'un ami, et récupère
 * les résumés que d'autres ont poussés vers l'une de mes propres fiches liées. Déclenché au
 * lancement et au retour au premier plan (voir `com.cacompte.app.CaCompteApplication`), pas par
 * un minuteur propre — même discipline que `com.cacompte.app.livesync.MatchConnectionCoordinator`.
 */
class SharedProfileSyncCoordinator(
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
) {
    private val transport = SharedProfileTransport()
    private var isSyncing = false

    suspend fun sync() {
        if (isSyncing) return
        isSyncing = true
        try {
            push()
            pull()
        } finally {
            isSyncing = false
        }
    }

    /** Un résumé par partie conclue avec au moins un participant lié — jamais retenté
     * indéfiniment pour une partie qui n'en a plus (fiche déliée entre-temps, par exemple). */
    private suspend fun push() {
        val pending = matchRepository.matchesPendingSharedProfileSync()
        if (pending.isEmpty()) return
        val playersByID = playerRepository.observeAll().first().associateBy { it.id }

        for (match in pending) {
            val participants = matchRepository.participants(match.id)
            val linkedIDs =
                participants
                    .mapNotNull { participant -> participant.playerId?.let { playersByID[it]?.sharedProfileID } }
                    .toSet()
            val standings =
                participants.mapNotNull { participant ->
                    val rank = participant.finalRank ?: return@mapNotNull null
                    val score = participant.finalScore ?: return@mapNotNull null
                    SharedMatchSummaryPayload.Entry(
                        sharedProfileID = participant.playerId?.let { playersByID[it]?.sharedProfileID },
                        nickname = participant.nicknameSnapshot,
                        avatarKind = participant.avatarKindSnapshot,
                        avatarValue = participant.avatarValueSnapshot,
                        paletteID = participant.paletteIDSnapshot,
                        rank = rank,
                        score = score,
                    )
                }

            if (linkedIDs.isEmpty() || standings.isEmpty()) {
                matchRepository.markSharedProfileSyncComplete(match)
                continue
            }

            val payload =
                SharedMatchSummaryPayload(
                    gameID = match.gameID,
                    rulesVersion = match.rulesVersion,
                    playedAt = match.endedAt ?: match.startedAt,
                    standings = standings,
                )
            val rows =
                linkedIDs.map {
                    SharedMatchSummaryRow(
                        matchID = match.id,
                        sharedProfileID = it,
                        payload = payload,
                    )
                }

            try {
                transport.push(rows)
                matchRepository.markSharedProfileSyncComplete(match)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Échec silencieux : pendingSharedProfileSync reste true, nouvelle tentative au
                // prochain déclenchement (même patron que MatchConnectionCoordinator).
            }
        }
    }

    /** `materializeSharedSummary` est déjà un no-op si cette partie est connue localement (c'est
     * cet appareil qui l'a jouée et poussée). */
    private suspend fun pull() {
        val linkedIDs = playerRepository.allSharedProfileIDs()
        if (linkedIDs.isEmpty()) return
        val rows =
            try {
                transport.fetchPending(linkedIDs)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return
            }

        val myOwnID = playerRepository.myOwnSharedPlayer()?.sharedProfileID

        for (row in rows) {
            val isMyOwnIdentity = row.sharedProfileID == myOwnID
            // Doc 14, phase 4 — suivre un ami donne accès à *toutes* ses parties seulement si
            // j'y étais moi-même (mon propre identifiant apparaît alors parmi les *autres*
            // participants du résumé) ; ma propre fiche partagée, elle, reçoit tout sans filtre.
            val isRelevant = isMyOwnIdentity || row.payload.standings.any { it.sharedProfileID == myOwnID }
            if (isRelevant) {
                matchRepository.materializeSharedSummary(row.payload, row.matchID)
            }

            // Doc 14, phase 4 — seul l'appareil qui fait autorité sur cet identifiant (le sien)
            // nettoie la boîte aux lettres ; plusieurs amis peuvent suivre la même personne, et
            // supprimer après chaque lecture ferait perdre la partie aux autres.
            if (isMyOwnIdentity) {
                try {
                    transport.delete(row.matchID, row.sharedProfileID)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // Best-effort — une ligne non nettoyée sera relue (idempotent) puis purgée côté serveur.
                }
            }
        }
    }
}
