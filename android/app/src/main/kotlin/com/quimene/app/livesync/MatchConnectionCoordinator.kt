package com.quimene.app.livesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.rules.GameCatalog
import com.quimene.store.MatchRepository
import com.quimene.store.PlayerRepository
import com.quimene.sync.OnlineSession
import com.quimene.sync.OnlineSessionError
import com.quimene.sync.ProfileCard
import com.quimene.sync.Role
import com.quimene.sync.SessionPresence
import com.quimene.sync.SupabaseSessionBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Doc 16, phase C — miroir de `MatchConnectionCoordinator.swift` : côté participant d'une session
 * en ligne. Rejoindre, c'est résoudre le code, rattraper le journal serveur, puis écouter le canal :
 * plus de poignée de main avec un hôte, qui n'a plus besoin d'être allumé. Retient la session
 * ([PersistedOnlineSession]) : après un arrêt complet du processus, la partie suivie reprend sans
 * redemander le code.
 */
class MatchConnectionCoordinator(
    private val catalog: GameCatalog,
    private val context: Context,
    private val resolveDeviceID: suspend () -> String,
    private val scope: CoroutineScope,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    /** Une partie vient d'être enregistrée : à déposer tout de suite chez mes amis liés. */
    private val onMatchKept: suspend () -> Unit,
) {
    private val backend = SupabaseSessionBackend()

    var sharedMatch: SharedMatchViewModel? by mutableStateOf(null)
        private set

    init {
        PersistedOnlineSession.load(context, PersistedOnlineSession.Role.Participant)?.let { persisted ->
            scope.launch {
                runCatching {
                    connect(persisted.pairingCode, persisted.deviceName, persisted.profile, persisted.isSpectator)
                }
            }
        }
    }

    /** Point d'entrée unique de [com.quimene.app.features.join.JoinScreen]. Renvoie le rôle
     * accordé : contributeur, ou observateur si le créateur n'autorise pas les contributeurs. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun join(
        code: String,
        deviceName: String,
        profile: ProfileCard?,
        appVersion: String,
    ): Role = connect(code, deviceName, profile, isSpectator = false)

    /** « Réessayer » : un rattrapage immédiat. */
    suspend fun reconnectNow(): Boolean {
        val link = sharedMatch?.link ?: return false
        link.refresh()
        return link.isReachable
    }

    /** Retour au premier plan ([com.quimene.app.QuiMeneApplication]) : rattraper. */
    suspend fun onForeground() {
        sharedMatch?.link?.refresh()
    }

    private suspend fun connect(
        code: String,
        deviceName: String,
        profile: ProfileCard?,
        isSpectator: Boolean,
    ): Role {
        val info = backend.resolve(code) ?: throw OnlineSessionError.SessionNotFound
        sharedMatch?.link?.stop()

        val deviceID = resolveDeviceID()
        val session = OnlineSession(info.sessionID, code, deviceID, backend)
        val link =
            SessionLink(
                session,
                code,
                SessionPresence(deviceID, deviceName, isOwner = false),
                context,
                scope,
                ownerDeviceID = info.ownerDeviceID,
            )
        val role = if (info.allowsContributors) Role.Contributor else Role.Observer
        var persisted =
            PersistedOnlineSession(
                info.sessionID,
                code,
                PersistedOnlineSession.Role.Participant,
                deviceName,
                profile,
                isSpectator,
            )
        persisted.save(context)
        sharedMatch =
            SharedMatchViewModel(
                link = link,
                role = role,
                catalog = catalog,
                scope = scope,
                me = profile,
                isSpectator = isSpectator,
                onSpectatorChange = { spectator ->
                    persisted = persisted.copy(isSpectator = spectator)
                    persisted.save(context)
                },
            ) {
                sharedMatch = null
                PersistedOnlineSession.clear(context, PersistedOnlineSession.Role.Participant)
            }
        sharedMatch?.let { model -> model.keepMatch = { matchID, events -> keep(matchID, events, model) } }
        link.start()
        return role
    }

    /** Doc 16, phase E — une partie terminée de la session où j'ai une place : enregistrée dans mon
     * historique, complète, comme chez le créateur. Ma place est reliée à ma fiche, celles de mes
     * amis à leurs fiches ; les autres gardent leur pseudo et un avatar dérivé. Déjà enregistrée
     * (reçue par ailleurs) : mise à jour si le journal de la session est plus long. Miroir de
     * `MatchConnectionCoordinator.keep` (Swift). */
    private suspend fun keep(
        matchID: UUID,
        events: List<StampedEvent>,
        model: SharedMatchViewModel,
    ): Boolean {
        val me = model.me ?: return false
        val created = events.firstOrNull()?.event as? MatchEvent.MatchCreated ?: return false
        val mySeat = model.link.identities.seatOf(me.id) ?: return false
        if (created.participants.none { SharedMatchViewModel.seatOf(it) == mySeat }) return false
        matchRepository.match(matchID)?.let { existing ->
            if (events.size > matchRepository.currentLog(existing).size) {
                matchRepository.replaceLog(events, existing, catalog)
            }
            return true
        }
        val myFiche = playerRepository.myOwnSharedPlayer()
        val fiches =
            created.participants.associate { participant ->
                val seat = SharedMatchViewModel.seatOf(participant)
                participant.id to
                    if (seat == mySeat) {
                        myFiche
                    } else {
                        model.link.identities
                            .occupant(seat)
                            ?.let { playerRepository.player(it) }
                    }
            }
        val kept =
            matchRepository.createMirroredMatch(matchID, events, catalog) { participant ->
                val fiche = fiches[participant.id]
                if (fiche == null) {
                    LiveShareCoordinator.generatedSeed(participant.displayName)
                } else {
                    MatchRepository.ParticipantSeed(
                        player = fiche,
                        nickname = participant.displayName,
                        avatarKind = fiche.avatarKind,
                        avatarValue = fiche.avatarValue,
                        paletteID = fiche.paletteID,
                        teamID = participant.teamID,
                    )
                }
            }
        if (kept != null) scope.launch { runCatching { onMatchKept() } }
        return kept != null
    }

    /** « Quitter la partie » : départ volontaire. */
    fun stop() {
        sharedMatch?.stop()
        sharedMatch = null
        PersistedOnlineSession.clear(context, PersistedOnlineSession.Role.Participant)
    }
}
