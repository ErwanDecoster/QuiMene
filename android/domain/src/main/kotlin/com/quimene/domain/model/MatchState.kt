package com.quimene.domain.model

import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.rules.EndCheck
import com.quimene.domain.rules.EndReason
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `MatchState.swift`. `data class` immuable plutôt que `struct` + `mutating func` :
 * [apply] retourne un nouvel état plutôt que de muter une copie locale — la valeur observée par
 * l'appelant ([com.quimene.domain.engine.MatchEngine]) est la même "nouvelle" MatchState dans
 * les deux cas, seul le style diffère (idiomatique côté Kotlin, sans perte de fidélité).
 *
 * Constructeur primaire `internal` (pas `public`) : côté Swift, `rounds`/`status`/`endReason`
 * sont `private(set)` — lisibles partout, modifiables seulement via `apply` (ADR-0005, event
 * sourcing : « MatchState ne se mute jamais directement »). Un `data class` Kotlin public
 * exposerait `copy(rounds = ...)` à tout le monde et permettrait de contourner ce contrat depuis
 * `:store`/`:app`. [create] est l'unique point de construction public hors du module, miroir de
 * l'`init` public Swift (mêmes 5 paramètres, mêmes valeurs par défaut) ; toute évolution
 * ultérieure passe par [com.quimene.domain.engine.MatchEngine] (`reduce`/`replay`) — jamais un
 * état reconstruit à la main à partir d'un cache (doc 03 : la reprise rejoue toujours
 * `eventLogData`).
 */
@ConsistentCopyVisibility
@Serializable
data class MatchState internal constructor(
    @Serializable(with = UUIDSerializer::class)
    val matchID: UUID,
    val gameID: String,
    val rulesVersion: Int,
    val variants: VariantSelection,
    val participants: List<Participant>,
    val rounds: List<Round> = emptyList(),
    val status: MatchStatus = MatchStatus.InProgress,
    val endReason: EndReason? = null,
) {
    companion object {
        fun create(
            matchID: UUID,
            gameID: String,
            rulesVersion: Int,
            variants: VariantSelection,
            participants: List<Participant>,
        ): MatchState = MatchState(matchID, gameID, rulesVersion, variants, participants)
    }

    /** Numéro de la prochaine manche : un de plus que le plus grand numéro existant, jamais
     * `rounds.size`. Le reducer *remplace* une manche de même numéro ; une partie dont les
     * numéros ont un trou (manche d'un pair perdue en route, acceptée ensuite hors séquence par
     * un hôte d'avant le contrôle `LiveSession.hostCommitLocked`) voyait sinon chaque nouvelle
     * manche écraser la dernière — totaux qui bougent, « Manche 8 » figé. Miroir de
     * `MatchState.nextRoundIndex` côté Apple. */
    val nextRoundIndex: Int get() = (rounds.maxOfOrNull { it.index } ?: -1) + 1

    fun totals(): Map<UUID, Int> {
        val result = LinkedHashMap<UUID, Int>()
        for (participant in participants) result[participant.id] = 0
        for (round in rounds) {
            for (entry in round.entries) {
                result[entry.participantID] = (result[entry.participantID] ?: 0) + entry.computedValue
            }
        }
        return result
    }

    fun total(participantID: UUID): Int = totals()[participantID] ?: 0

    /** Visibilité `internal`, comme côté Swift (pas `public`) : seul
     * [com.quimene.domain.engine.MatchEngine], dans le même module, y accède. */
    internal fun apply(
        event: MatchEvent,
        rules: GameRules,
        definition: GameDefinition,
        occurredAt: Instant,
    ): MatchState =
        when (event) {
            // Construction gérée par MatchEngine.replay, pas ici.
            is MatchEvent.MatchCreated -> this

            is MatchEvent.RoundCommitted -> commitRound(event.draft, rules, definition, occurredAt)

            // L'index de l'événement lui-même n'est pas utilisé : celui du brouillon prime, comme
            // côté Swift (même chemin que roundCommitted, qui remplace la manche existante).
            is MatchEvent.RoundAmended -> commitRound(event.draft, rules, definition, occurredAt)

            is MatchEvent.RoundRemoved ->
                copy(rounds = rounds.filterNot { it.index == event.index }).refreshStatus(rules, definition)

            is MatchEvent.MatchAbandoned -> copy(status = MatchStatus.Abandoned, endReason = null)

            is MatchEvent.MatchEndedManually -> copy(status = MatchStatus.Ended, endReason = EndReason.ManualStop)

            is MatchEvent.NoteAdded ->
                copy(
                    rounds =
                        rounds.map { round ->
                            if (round.index == event.roundIndex) round.copy(note = event.text) else round
                        },
                )
        }

    private fun commitRound(
        draft: RoundDraft,
        rules: GameRules,
        definition: GameDefinition,
        occurredAt: Instant,
    ): MatchState {
        val entries = rules.score(draft, this, definition)
        val round = Round(index = draft.index, entries = entries, committedAt = occurredAt, note = draft.note)
        val withoutExisting = rounds.filterNot { it.index == draft.index }
        val updatedRounds = (withoutExisting + round).sortedBy { it.index }
        return copy(rounds = updatedRounds).refreshStatus(rules, definition)
    }

    private fun refreshStatus(
        rules: GameRules,
        definition: GameDefinition,
    ): MatchState {
        // Terminal — jamais recalculé, comme côté Swift.
        if (status == MatchStatus.Abandoned) return this
        return when (val check = rules.endCheck(this, definition)) {
            is EndCheck.Continue -> copy(status = MatchStatus.InProgress, endReason = null)
            is EndCheck.FinalRound -> copy(status = MatchStatus.FinalRound, endReason = check.reason)
            is EndCheck.Ended -> copy(status = MatchStatus.Ended, endReason = check.reason)
        }
    }
}
