package com.quimene.app.profilesharing

import com.quimene.app.livesync.LiveShareCoordinator
import com.quimene.domain.model.SharedMatchPackage
import com.quimene.domain.rules.GameCatalog
import com.quimene.store.MatchRepository
import com.quimene.store.ParticipantEntity
import com.quimene.store.PlayerRepository
import com.quimene.sync.MailboxCrypto
import com.quimene.sync.MailboxItem
import com.quimene.sync.MatchMailboxTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Doc 16, phase E — miroir de `SharedProfileSyncCoordinator.swift` : historique partagé. Chaque
 * partie terminée avec un ami lié lui est déposée, **complète** et chiffrée, dans sa boîte aux
 * lettres ([MatchMailboxTransport]) ; les parties que d'autres m'ont déposées sont enregistrées ici
 * comme si je les avais jouées. Remplace les résumés du doc 14. Déclenché au lancement et au retour
 * au premier plan (`com.quimene.app.QuiMeneApplication`), dès qu'une partie se termine (écran de
 * résultats) et à l'ouverture de l'Historique (avec « tirer pour actualiser »).
 */
class SharedProfileSyncCoordinator(
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val catalog: GameCatalog,
) {
    private val transport = MatchMailboxTransport()
    private val mutex = Mutex()
    private val needsAnotherPass = AtomicBoolean(false)

    /** Une demande pendant un passage en cours n'est pas perdue : un passage de plus suit. */
    suspend fun sync() {
        if (!mutex.tryLock()) {
            needsAnotherPass.set(true)
            return
        }
        try {
            do {
                needsAnotherPass.set(false)
                deposit()
                collect()
            } while (needsAnotherPass.get())
        } finally {
            mutex.unlock()
        }
    }

    /** Une partie terminée avec au moins un ami lié (pas moi) : déposée chez chacun, une fois.
     * Jamais retentée indéfiniment pour une partie qui n'en a plus (fiche déliée entre-temps). */
    private suspend fun deposit() {
        val pending = matchRepository.matchesPendingSharedProfileSync()
        if (pending.isEmpty()) return
        val playersByID = playerRepository.allPlayers().associateBy { it.id }
        val myID = playerRepository.myOwnSharedPlayer()?.sharedProfileID

        for (match in pending) {
            val participants = matchRepository.participants(match.id)
            val profileOf = { participant: ParticipantEntity ->
                participant.playerId?.let { playersByID[it]?.sharedProfileID }
            }
            val recipients = participants.mapNotNull(profileOf).toSet() - setOfNotNull(myID)
            val events = matchRepository.currentLog(match)
            if (match.isImportedSummary || recipients.isEmpty() || events.isEmpty()) {
                matchRepository.markSharedProfileSyncComplete(match)
                continue
            }
            val pkg =
                SharedMatchPackage(
                    matchID = match.id,
                    participants = participants.map { packaged(it, profileOf(it)) },
                    events = events,
                )
            val items =
                recipients.map { recipient ->
                    MailboxItem(MailboxCrypto.lookupKey(recipient), match.id, MailboxCrypto.seal(pkg, recipient))
                }
            try {
                transport.deposit(items)
                matchRepository.markSharedProfileSyncComplete(match)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Échec silencieux : pendingSharedProfileSync reste true, nouvelle tentative au
                // prochain déclenchement.
            }
        }
    }

    /** Ma boîte : chaque partie reçue est enregistrée (sans effet si je l'ai déjà, jouée ici ou
     * suivie dans une session), puis retirée. Un dépôt illisible est retiré aussi, pour ne pas
     * être relu indéfiniment. */
    private suspend fun collect() {
        val myID = playerRepository.myOwnSharedPlayer()?.sharedProfileID ?: return
        val mailboxKey = MailboxCrypto.lookupKey(myID)
        val items =
            try {
                transport.fetch(mailboxKey)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return
            }
        for (item in items) {
            val pkg = MailboxCrypto.open(item.ciphertext, myID)
            if (pkg != null) {
                // L'Historique observe la base : la partie y apparaît d'elle-même.
                runCatching { matchRepository.importSharedMatch(pkg, catalog) }.getOrNull() ?: continue
            }
            try {
                transport.remove(mailboxKey, item.matchID)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Best-effort : relue plus tard (idempotent), puis purgée côté serveur.
            }
        }
    }

    /** Un joueur tel que le destinataire l'affichera. Une photo ne voyage pas : repli sur l'emoji
     * dérivé du pseudo, comme pour toute nouvelle fiche. */
    private fun packaged(
        participant: ParticipantEntity,
        profileID: UUID?,
    ): SharedMatchPackage.Participant {
        val isPhoto = participant.avatarKindSnapshot == "photo"
        val seed = LiveShareCoordinator.generatedSeed(participant.nicknameSnapshot)
        return SharedMatchPackage.Participant(
            participantID = participant.id,
            sharedProfileID = profileID,
            nickname = participant.nicknameSnapshot,
            avatarKind = if (isPhoto) seed.avatarKind else participant.avatarKindSnapshot,
            avatarValue = if (isPhoto) seed.avatarValue else participant.avatarValueSnapshot,
            paletteID = participant.paletteIDSnapshot,
        )
    }
}
