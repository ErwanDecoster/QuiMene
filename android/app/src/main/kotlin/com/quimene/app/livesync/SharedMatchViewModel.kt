package com.quimene.app.livesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quimene.app.R
import com.quimene.app.features.livematch.LiveRoundEntryState
import com.quimene.app.features.livematch.ProfileBadge
import com.quimene.app.features.results.MatchSummaryViewModel
import com.quimene.app.features.results.matchSummaryState
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.domain.engine.MatchEngine
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.engine.StampedEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.Round
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreDetail
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.ValidationResult
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.Standing
import com.quimene.store.ParticipantEntity
import com.quimene.sync.ProfileCard
import com.quimene.sync.Role
import com.quimene.sync.SeatRef
import com.quimene.sync.SessionEventRecord
import com.quimene.sync.SessionIdentityEvent
import com.quimene.sync.SessionIdentityRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Doc 16, phase C — miroir de `SharedMatchModel.swift` : la partie suivie par un participant.
 * Aucune copie en base : l'état est rejoué depuis le journal de la session ([SessionLink]), qui
 * fait foi pour tous. La partie courante est celle du `matchCreated` le plus récent : quand
 * quelqu'un en lance une nouvelle, l'écran la suit de lui-même.
 */
class SharedMatchViewModel(
    val link: SessionLink,
    val role: Role,
    private val catalog: GameCatalog,
    private val scope: CoroutineScope,
    /** Mon profil, tel que publié dans la session. `null` sans profil : seul « Je regarde
     * seulement » est alors possible (doc 16, phase D). */
    val me: ProfileCard?,
    isSpectator: Boolean,
    private val onSpectatorChange: (Boolean) -> Unit,
    private val onStopped: () -> Unit,
) : LiveRoundEntryState {
    private var stateInternal by mutableStateOf<MatchState?>(null)
    val stateOrNull get() = stateInternal
    private val state: MatchState get() = requireNotNull(stateInternal) { "Partie pas encore reçue" }
    private var currentMatchID: UUID? = null

    /** Seul un contributeur saisit, tant que la session est ouverte et qu'il a dit qui il est
     * dans la partie (doc 16, phase D) : un spectateur regarde. */
    val canPropose: Boolean get() = role == Role.Contributor && !link.isClosed && mySeat != null

    // Qui es-tu ? (doc 16, phase D)

    /** « Je regarde seulement » : choix local, retenu avec la session. */
    var isSpectator by mutableStateOf(isSpectator)
        private set

    /** Pourquoi il faut de nouveau dire qui on est (place prise, association annulée). */
    var identityMessage by mutableStateOf<String?>(null)
        private set
    var isClaiming by mutableStateOf(false)
        private set

    enum class SeatStatus { Free, Mine, Taken }

    /** Ma place dans la partie courante : reliée à mon profil par le créateur (ami déjà lié,
     * reconnu sans question), ou revendiquée. */
    val mySeat: SeatRef?
        get() {
            val me = me ?: return null
            val seat = link.identities.seatOf(me.id) ?: return null
            val current = stateInternal ?: return null
            return seat.takeIf { current.participants.any { seatOf(it) == seat } }
        }

    val myParticipantID: UUID?
        get() {
            val seat = mySeat ?: return null
            return stateInternal?.participants?.firstOrNull { seatOf(it) == seat }?.id
        }

    /** « Qui es-tu ? » à afficher : la partie est chargée, et je n'ai ni place ni choisi de
     * seulement regarder. */
    val needsIdentity: Boolean get() = stateInternal != null && !isSpectator && mySeat == null

    fun seatStatus(participant: Participant): SeatStatus {
        val seat = seatOf(participant)
        if (seat == mySeat) return SeatStatus.Mine
        return if (link.identities.occupant(seat) == null) SeatStatus.Free else SeatStatus.Taken
    }

    /** Ma place revendiquée (pas reliée par le créateur) : elle seule peut être rendue. */
    val canChangeSeat: Boolean get() = me?.let { link.identities.activeClaimOf(it.id) } != null

    /** Le créateur, à ajouter à mes amis une fois ma place retenue (liaison dans les deux sens). */
    val ownerToBefriend: ProfileCard?
        get() {
            if (mySeat == null) return null
            return link.identities.owner?.takeIf { it.id != me?.id }
        }

    /** Mes amis liés, lus par l'écran dans mes fiches. */
    var friendProfileIDs by mutableStateOf<Set<UUID>>(emptySet())

    override val profileBadges: Map<UUID, ProfileBadge> get() = profileBadges(friendProfileIDs)

    /** « Moi » sur ma place ; un lien sur les places occupées par un profil que je compte parmi
     * mes amis ([friendProfileIDs], lus par l'écran dans mes fiches). */
    fun profileBadges(friendProfileIDs: Set<UUID>): Map<UUID, ProfileBadge> {
        val current = stateInternal ?: return emptyMap()
        val mine = mySeat
        val badges = mutableMapOf<UUID, ProfileBadge>()
        for (participant in current.participants) {
            val seat = seatOf(participant)
            if (seat == mine) {
                badges[participant.id] = ProfileBadge.Me
            } else if (link.identities.occupant(seat)?.let { it in friendProfileIDs } == true) {
                badges[participant.id] = ProfileBadge.Friend
            }
        }
        return badges
    }

    /** « C'est moi » : revendique cette place. Premier arrivé, premier servi. */
    fun claim(participant: Participant) {
        val me = me ?: return
        val matchID = currentMatchID ?: return
        if (isClaiming) return
        scope.launch {
            isClaiming = true
            identityMessage = null
            try {
                val seat = seatOf(participant)
                val sent = link.submitIdentity(SessionIdentityEvent.claim(seat, me, link.session.deviceID), matchID)
                identityMessage =
                    when {
                        !sent && link.isClosed -> link.context.getString(R.string.le_createur_a_arrete_la_session)
                        !sent -> link.context.getString(R.string.hors_connexion_la_saisie_reprendra_au_retour_du_reseau)
                        mySeat != seat ->
                            link.context.getString(
                                R.string.cette_place_vient_d_etre_prise_par_quelqu_un_d_autre,
                            )
                        else -> null
                    }
            } finally {
                isClaiming = false
            }
        }
    }

    fun watchOnly() {
        identityMessage = null
        isSpectator = true
        onSpectatorChange(true)
    }

    /** Revenir à « Qui es-tu ? » : depuis « Je regarde seulement », ou pour changer de place (la
     * revendication précédente est retirée). */
    fun chooseAgain() {
        identityMessage = null
        if (isSpectator) {
            isSpectator = false
            onSpectatorChange(false)
            return
        }
        val me = me ?: return
        val claim = link.identities.activeClaimOf(me.id) ?: return
        val matchID = currentMatchID ?: return
        scope.launch { link.submitIdentity(SessionIdentityEvent.revoke(claim.claimID, link.session.deviceID), matchID) }
    }

    // Historique partagé (doc 16, phase E)

    /** Enregistre dans mon historique une partie terminée de la session où j'ai une place ; `true`
     * une fois faite. Fourni par [MatchConnectionCoordinator], qui a accès aux fiches. */
    var keepMatch: (suspend (UUID, List<StampedEvent>) -> Boolean)? = null
    private val keptMatchIDs = mutableSetOf<UUID>()

    /** Chaque partie terminée de la session, une fois : dans l'historique de chaque participant
     * ayant un profil, complète, comme chez le créateur. Retentée tant qu'elle n'a pas pu l'être
     * (place pas encore choisie, par exemple). */
    suspend fun keepConcludedMatches() {
        val keepMatch = keepMatch ?: return
        val matchIDs =
            link.session
                .records()
                .map { it.matchID }
                .distinct()
        for (matchID in matchIDs) {
            if (matchID in keptMatchIDs) continue
            val log = link.session.eventsForMatch(matchID)
            val replayed = runCatching { MatchEngine().replay(log, catalog) }.getOrNull() ?: continue
            if (replayed.status != MatchStatus.Ended && replayed.status != MatchStatus.Abandoned) continue
            if (keepMatch(matchID, log)) keptMatchIDs += matchID
        }
    }

    /** Le créateur a annulé mon association : « Qui es-tu ? » réapparaît, avec l'explication. */
    private suspend fun noticeRevocation(records: List<SessionIdentityRecord>) {
        val me = me ?: return
        val all = link.session.identities()
        val revokedMine =
            records.any { record ->
                record.event.kind == SessionIdentityEvent.Kind.Revoke &&
                    record.event.deviceID != link.session.deviceID &&
                    all.any { it.event.id == record.event.revokedClaimID && it.event.profile?.id == me.id }
            }
        if (revokedMine && mySeat == null) {
            identityMessage = link.context.getString(R.string.le_createur_a_annule_ton_association_a_cette_place)
        }
    }

    /** Joignable et session ouverte ; hors ligne, le tableau reste le dernier reçu (doc 16). */
    val isHostConnected: Boolean get() = link.isReachable && !link.isClosed
    val isSessionClosed: Boolean get() = link.isClosed
    val isConcluded: Boolean
        get() = stateInternal?.status == MatchStatus.Ended || stateInternal?.status == MatchStatus.Abandoned

    var latestRejectionReason by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    override val definition: GameDefinition get() = catalog.definition(state.gameID, state.rulesVersion)
    override val participants: List<Participant> get() = state.participants.sortedBy { it.seatIndex }
    override val totals: Map<UUID, Int> get() = state.totals()
    override val rounds: List<Round> get() = state.rounds
    override val currentRoundNumber: Int get() = state.rounds.size + 1
    override val requiresCloserSelection: Boolean get() = definition.requiresCloserSelection
    override val currentStandings: List<Standing>
        get() = catalog.rules(state.gameID, state.rulesVersion).standings(state, definition)

    override var pendingScores by mutableStateOf<Map<UUID, Int>>(emptyMap())
        private set
    override var closedParticipantID by mutableStateOf<UUID?>(null)
    override var validationErrorMessage by mutableStateOf<String?>(null)
        private set
    override var roundExplanationMessage by mutableStateOf<String?>(null)
        private set

    init {
        link.onNewRecords = { reload(it) }
        link.onNewIdentities = {
            noticeRevocation(it)
            keepConcludedMatches()
        }
    }

    /** Écran de résultats, avec des fiches en mémoire (avatar dérivé du pseudo). */
    fun summaryState(): MatchSummaryViewModel.UiState? {
        val current = stateInternal ?: return null
        val definition = catalog.definition(current.gameID, current.rulesVersion)
        val entities =
            current.participants.associate { participant ->
                val avatar = Avatar.generated(participant.displayName)
                participant.id to
                    ParticipantEntity(
                        id = participant.id,
                        playerId = null,
                        nicknameSnapshot = participant.displayName,
                        avatarKindSnapshot = "emoji",
                        avatarValueSnapshot = (avatar.kind as? AvatarKind.Emoji)?.character.orEmpty(),
                        paletteIDSnapshot = avatar.palette.index.toString(),
                        seatIndex = participant.seatIndex,
                        teamID = participant.teamID,
                        matchId = current.matchID,
                    )
            }
        return matchSummaryState(
            current,
            definition,
            catalog.rules(current.gameID, current.rulesVersion),
            entities,
            myParticipantID,
        )
    }

    override fun setScore(
        participantID: UUID,
        value: Int,
    ) {
        pendingScores = pendingScores + (participantID to value)
        validationErrorMessage = null
    }

    override fun clearScore(participantID: UUID) {
        pendingScores = pendingScores - participantID
    }

    override fun focus(participantID: UUID) = Unit

    override fun commitRound(detailByParticipant: Map<UUID, ScoreDetail>) {
        val inputs =
            participants.map { participant ->
                val modifiers = if (participant.id == closedParticipantID) setOf(ModifierID.closedRound) else emptySet()
                ScoreInput(
                    participantID = participant.id,
                    rawValue = pendingScores[participant.id] ?: 0,
                    detail = detailByParticipant[participant.id],
                    modifiers = modifiers,
                )
            }
        commitCustomRound(inputs) {
            pendingScores = emptyMap()
            closedParticipantID = null
        }
    }

    /** Valide localement, puis envoie au journal de la session. La saisie n'est effacée
     * ([onCommitted]) qu'une fois acceptée ; devancée ou hors ligne, elle reste en place. */
    override fun commitCustomRound(
        inputs: List<ScoreInput>,
        onCommitted: () -> Unit,
    ) {
        val matchID = currentMatchID
        if (!canPropose || isSubmitting || matchID == null) return
        val draft = RoundDraft(index = state.nextRoundIndex, inputs = inputs)
        val validation = catalog.rules(state.gameID, state.rulesVersion).validate(draft, state, definition)
        if (validation is ValidationResult.Invalid) {
            validationErrorMessage = validation.errors.firstOrNull()?.message
            return
        }
        validationErrorMessage = null
        latestRejectionReason = null
        scope.launch {
            if (submit(MatchEvent.RoundCommitted(draft), matchID)) {
                onCommitted()
                stateInternal?.let {
                    link.announceToLockScreens(
                        it,
                        definition,
                        catalog.rules(it.gameID, it.rulesVersion),
                    )
                }
            }
        }
    }

    /** Doc 16, phase C — « Partie suivante » lancée par ce participant : mêmes joueurs aux mêmes
     * places (nouveaux identifiants : un identifiant de participant ne sert qu'à une partie), mêmes
     * variantes si c'est le même jeu. */
    fun startNextMatch(next: GameDefinition) {
        val current = stateInternal ?: return
        if (!canPropose || isSubmitting) return
        val newParticipants =
            current.participants
                .sortedBy { it.seatIndex }
                .map { Participant(displayName = it.displayName, seatIndex = it.seatIndex) }
        val variants = if (next.id == current.gameID) current.variants else VariantSelection()
        val matchID = UUID.randomUUID()
        scope.launch {
            submit(
                MatchEvent.MatchCreated(next.id, next.rulesVersion, variants, newParticipants),
                matchID,
                eventID = matchID,
                overtakenMessage =
                    link.context.getString(
                        R.string.une_partie_vient_d_etre_lancee_sur_un_autre_appareil,
                    ),
            )
        }
    }

    private suspend fun submit(
        event: MatchEvent,
        matchID: UUID,
        eventID: UUID = UUID.randomUUID(),
        overtakenMessage: String? = null,
    ): Boolean {
        isSubmitting = true
        try {
            return when (val result = link.submit(event, matchID, eventID)) {
                is SessionLink.SubmitResult.Accepted -> {
                    reload(emptyList())
                    true
                }
                is SessionLink.SubmitResult.Overtaken -> {
                    latestRejectionReason = overtakenMessage ?: overtakenMessage(link.context, result.byDeviceName)
                    false
                }
                SessionLink.SubmitResult.Offline -> {
                    latestRejectionReason =
                        link.context.getString(R.string.hors_connexion_la_saisie_reprendra_au_retour_du_reseau)
                    false
                }
                SessionLink.SubmitResult.Closed -> {
                    latestRejectionReason = link.context.getString(R.string.le_createur_a_arrete_la_session)
                    false
                }
            }
        } finally {
            isSubmitting = false
        }
    }

    private suspend fun reload(fresh: List<SessionEventRecord>) {
        val matchID = link.session.currentMatchID() ?: return
        val replayed =
            runCatching { MatchEngine().replay(link.session.eventsForMatch(matchID), catalog) }.getOrNull() ?: return
        val isNewMatch = matchID != currentMatchID
        val previousRoundCount = if (isNewMatch) 0 else stateInternal?.rounds?.size ?: 0
        currentMatchID = matchID
        stateInternal = replayed
        if (isNewMatch) {
            latestRejectionReason = null
            pendingScores = emptyMap()
            closedParticipantID = null
        }
        keepConcludedMatches()
        if (replayed.rounds.size > previousRoundCount) {
            replayed.rounds
                .lastOrNull()
                ?.entries
                ?.firstNotNullOfOrNull { it.explanation }
                ?.let(::showRoundExplanation)
        }
    }

    private fun showRoundExplanation(message: String) {
        scope.launch {
            roundExplanationMessage = message
            delay(4_000)
            if (roundExplanationMessage == message) roundExplanationMessage = null
        }
    }

    fun stop() {
        scope.launch { link.stop() }
        onStopped()
    }

    companion object {
        fun seatOf(participant: Participant) = SeatRef(participant.seatIndex, participant.displayName)

        fun overtakenMessage(
            context: Context,
            deviceName: String?,
        ): String =
            if (deviceName != null) {
                context.getString(R.string.value1_vient_de_valider_une_manche_verifie_avant_de_valider, deviceName)
            } else {
                context.getString(R.string.une_autre_manche_vient_d_etre_validee_verifie_avant_de)
            }
    }
}
