package com.quimene.domain.engine

import com.quimene.domain.model.MatchState
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import java.time.Instant

/** Miroir de `MatchEngineError.swift`. */
sealed class MatchEngineError(
    message: String,
) : Exception(message) {
    data object MissingMatchCreated : MatchEngineError("Le journal ne commence pas par matchCreated")
}

/**
 * Miroir de `MatchEngine.swift`. [now] est une horloge injectée (jamais `Instant.now()` appelé
 * implicitement ailleurs dans le domaine — règle « aucune date implicite », doc 11) pour rendre
 * [reduce] testable de façon déterministe.
 */
class MatchEngine(
    private val now: () -> Instant = Instant::now,
) {
    fun reduce(
        state: MatchState,
        event: MatchEvent,
        rules: GameRules,
        definition: GameDefinition,
    ): MatchState = state.apply(event, rules, definition, occurredAt = now())

    /**
     * Rejeu — dédup par id (idempotent), tri `(lamport, deviceID)` (commutatif quel que soit
     * l'ordre d'arrivée du journal), le premier événement doit être `matchCreated`.
     * `matchID` vient de l'`id` du `StampedEvent`, pas d'un champ explicite de `matchCreated`.
     * `occurredAt` de chaque événement rejoué est **le sien**, jamais [now] — seul le premier
     * état construit (implicitement, à la création) n'a pas cette notion.
     */
    fun replay(
        log: List<StampedEvent>,
        catalog: GameCatalog,
    ): MatchState {
        val seen = HashSet<java.util.UUID>()
        val deduplicated = log.filter { seen.add(it.id) }
        val sorted = deduplicated.sortedWith(compareBy({ it.lamport }, { it.deviceID }))

        val first = sorted.firstOrNull()
        val created =
            (first?.event as? MatchEvent.MatchCreated)
                ?: throw MatchEngineError.MissingMatchCreated

        val definition = catalog.definition(created.gameID, created.rulesVersion)
        val rules = catalog.rules(created.gameID, created.rulesVersion)

        var state =
            MatchState(
                matchID = first.id,
                gameID = created.gameID,
                rulesVersion = created.rulesVersion,
                variants = created.variants,
                participants = created.participants,
            )

        for (stamped in sorted.drop(1)) {
            state = state.apply(stamped.event, rules, definition, occurredAt = stamped.occurredAt)
        }

        return state
    }
}
