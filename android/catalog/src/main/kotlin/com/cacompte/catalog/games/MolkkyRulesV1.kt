package com.cacompte.catalog.games

import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.Round
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.rules.EndCheck
import com.cacompte.domain.rules.EndReason
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.domain.rules.Standing
import java.util.UUID
import kotlin.math.abs

/**
 * Miroir de `MolkkyRulesV1.swift` — la seule règle de score « non monotone » du catalogue :
 * dépasser 50 ramène le total à 25 plutôt que de continuer à grimper. `computedValue` porte,
 * pour la manche qui fait déborder, le delta nécessaire pour atterrir exactement sur 25 plutôt
 * que la valeur brute lancée — le cumul générique ([MatchState.totals], simple somme des
 * `computedValue`) retombe alors juste sans logique spéciale côté lecture.
 *
 * `endCheck`/`standings` sont entièrement propres à Mölkky : la fin est immédiate (pas de
 * « dernier tour », `completeRound: false`) dès qu'un total vaut exactement 50, ou dès qu'il ne
 * reste plus qu'un joueur non éliminé (trois lancers à 0 d'affilée). `targetExact` n'est pas géré
 * par le classement par défaut de [GameRules] (conçu pour `lowestWins`/`highestWins`), d'où le
 * classement par distance à 50.
 */
class MolkkyRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    override fun score(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): List<ScoreEntry> =
        draft.inputs.map { input ->
            val priorTotal = state.total(input.participantID)
            val candidate = priorTotal + input.rawValue
            val bust = candidate > 50
            ScoreEntry(
                participantID = input.participantID,
                rawValue = input.rawValue,
                computedValue = if (bust) 25 - priorTotal else input.rawValue,
                explanation = if (bust) "Dépassement de 50 : retour à 25." else null,
                detail = input.detail,
                modifiers = input.modifiers,
            )
        }

    override fun endCheck(
        state: MatchState,
        definition: GameDefinition,
    ): EndCheck {
        val totals = state.totals()
        if (state.participants.any { (totals[it.id] ?: 0) == 50 }) {
            return EndCheck.Ended(EndReason.TargetReached)
        }
        if (state.participants.size > 1) {
            val survivors = state.participants.filterNot { isEliminated(it.id, state) }
            if (survivors.size == 1) {
                return EndCheck.Ended(EndReason.Elimination)
            }
        }
        return EndCheck.Continue
    }

    override fun standings(
        state: MatchState,
        definition: GameDefinition,
    ): List<Standing> {
        val totals = state.totals()
        val groupedByDistance = state.participants.map { it.id }.groupBy { abs(50 - (totals[it] ?: 0)) }
        val orderedDistances = groupedByDistance.keys.sorted()

        val result = mutableListOf<Standing>()
        var rank = 1
        for (distance in orderedDistances) {
            val group = groupedByDistance.getValue(distance)
            for (id in group) {
                result +=
                    Standing(
                        participantID = id,
                        rank = rank,
                        score = totals[id] ?: 0,
                        sharedWith = group.filterNot { it == id },
                    )
            }
            rank += group.size
        }
        return result
    }

    /** Trois lancers d'affilée à 0 éliminent — dérivé de l'historique à chaque appel, jamais
     * stocké (doc 04, event sourcing). */
    private fun isEliminated(
        id: UUID,
        state: MatchState,
    ): Boolean {
        val values =
            state.rounds
                .mapNotNull { round: Round ->
                    round.entries.firstOrNull { it.participantID == id }
                }.map { it.rawValue }
        if (values.size < 3) return false
        return values.takeLast(3).all { it == 0 }
    }

    companion object {
        const val ENGINE_ID = "molkky.v1"
    }
}
