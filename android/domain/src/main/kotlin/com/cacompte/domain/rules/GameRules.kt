package com.cacompte.domain.rules

import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.ModifierID
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.model.ValidationError
import com.cacompte.domain.model.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Miroir de `EndReason.swift`. */
@Serializable
enum class EndReason {
    @SerialName("scoreThreshold")
    ScoreThreshold,

    @SerialName("roundLimit")
    RoundLimit,

    @SerialName("targetReached")
    TargetReached,

    @SerialName("allSheetsComplete")
    AllSheetsComplete,

    @SerialName("elimination")
    Elimination,

    @SerialName("manualStop")
    ManualStop,
}

/** Miroir de `EndCheck` (déclaré dans `GameRules.swift`). */
sealed interface EndCheck {
    data object Continue : EndCheck

    data class FinalRound(
        val triggeredBy: UUID,
        val reason: EndReason,
    ) : EndCheck

    data class Ended(
        val reason: EndReason,
    ) : EndCheck
}

/** Miroir de `Standing.swift`. */
data class Standing(
    val participantID: UUID,
    val rank: Int,
    val score: Int,
    val sharedWith: List<UUID> = emptyList(),
)

/**
 * Miroir de `GameRules.swift` (protocole) + son extension par défaut, réunis ici — Kotlin permet
 * des corps de méthode par défaut directement dans l'interface, contrairement à Swift qui sépare
 * protocole et extension. `GenericSumRules` (`:catalog`) hérite de tout sans rien redéfinir,
 * comme son homologue Swift.
 */
interface GameRules {
    val engineID: String

    fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult {
        val entry = definition.scoring.entry
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()

        for (input in draft.inputs) {
            val min = entry.min
            val max = entry.max
            if (min != null && input.rawValue < min) {
                errors +=
                    ValidationError(
                        ValidationError.Field.ParticipantField(input.participantID),
                        "Score sous le minimum autorisé ($min).",
                    )
            }
            if (max != null && input.rawValue > max) {
                errors +=
                    ValidationError(
                        ValidationError.Field.ParticipantField(input.participantID),
                        "Score au-dessus du maximum autorisé ($max).",
                    )
            }
            val warnBelow = entry.warnBelow
            if (warnBelow != null && input.rawValue < warnBelow) {
                warnings += "Score inhabituel, à vérifier."
            }
            val warnAbove = entry.warnAbove
            if (warnAbove != null && input.rawValue > warnAbove) {
                warnings += "Score inhabituel, à vérifier."
            }
        }

        return when {
            errors.isNotEmpty() -> ValidationResult.Invalid(errors)
            warnings.isNotEmpty() -> ValidationResult.Warning(warnings)
            else -> ValidationResult.Valid
        }
    }

    fun score(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): List<ScoreEntry> =
        draft.inputs.map { input ->
            ScoreEntry(
                participantID = input.participantID,
                rawValue = input.rawValue,
                computedValue = input.rawValue, // identité, aucune règle appliquée
                explanation = null,
                detail = input.detail,
                modifiers = input.modifiers,
            )
        }

    fun endCheck(
        state: MatchState,
        definition: GameDefinition,
    ): EndCheck {
        val totals = state.totals()
        for (condition in definition.end.conditions) {
            val threshold = condition.resolvedValue(state.variants)
            when (condition.type) {
                EndConditionType.ScoreThreshold -> {
                    val reached =
                        state.participants.any {
                            condition.comparison.evaluate(totals[it.id] ?: 0, threshold)
                        }
                    if (reached) return EndCheck.Ended(EndReason.ScoreThreshold)
                }

                EndConditionType.RoundLimit -> {
                    if (condition.comparison.evaluate(state.rounds.size, threshold)) {
                        return EndCheck.Ended(EndReason.RoundLimit)
                    }
                }

                // Nécessitent des données propres au jeu, pas gérées génériquement.
                EndConditionType.TargetReached,
                EndConditionType.AllSheetsComplete,
                EndConditionType.Elimination,
                EndConditionType.ManualStop,
                -> Unit
            }
        }
        return EndCheck.Continue
    }

    fun standings(
        state: MatchState,
        definition: GameDefinition,
    ): List<Standing> {
        val totals = state.totals()
        val direction = definition.scoring.direction

        val groupedByTotal = state.participants.map { it.id }.groupBy { totals[it] ?: 0 }
        val orderedTotals =
            if (direction == Direction.HighestWins) {
                groupedByTotal.keys.sortedDescending()
            } else {
                groupedByTotal.keys.sorted()
            }

        val result = mutableListOf<Standing>()
        var rank = 1
        for (total in orderedTotals) {
            val tied = groupedByTotal.getValue(total)
            val subgroups = resolveTieBreakGroups(tied, definition.tieBreak, state, direction)
            for (group in subgroups) {
                for (id in group) {
                    result +=
                        Standing(
                            participantID = id,
                            rank = rank,
                            score = total,
                            sharedWith = group.filterNot { it == id },
                        )
                }
                rank += group.size
            }
        }
        return result
    }
}

/**
 * Récursion de départage — miroir de `resolveTieBreakGroups`/`groupByKey`/
 * `bestSingleRoundValue`/`roundsClosedCount` (extension de `GameRules.swift`). **Le
 * comportement `.worstSingleRound` réutilisant `bestSingleRoundValue` (le "meilleur" round quel
 * que soit le cas déclenchant, seul le sens du tri change) est un quirk du source Swift, porté
 * tel quel — les golden files (étape C) le figent, ce n'est pas une réinterprétation à corriger
 * ici.**
 */
private fun resolveTieBreakGroups(
    ids: List<UUID>,
    rules: List<TieBreakRule>,
    state: MatchState,
    direction: Direction,
): List<List<UUID>> {
    if (ids.size <= 1 || rules.isEmpty()) return listOf(ids)
    return when (rules.first()) {
        TieBreakRule.Shared -> listOf(ids)

        TieBreakRule.BestSingleRound -> {
            val values = ids.associateWith { bestSingleRoundValue(it, state, direction) }
            groupByKey(ids, values, ascending = direction != Direction.HighestWins, rules.drop(1), state, direction)
        }

        TieBreakRule.WorstSingleRound -> {
            val values = ids.associateWith { bestSingleRoundValue(it, state, direction) }
            groupByKey(ids, values, ascending = direction == Direction.HighestWins, rules.drop(1), state, direction)
        }

        TieBreakRule.FewestRoundsClosed -> {
            val values = ids.associateWith { roundsClosedCount(it, state) }
            groupByKey(ids, values, ascending = true, rules.drop(1), state, direction)
        }

        TieBreakRule.MostRoundsWon,
        TieBreakRule.LowerSecondaryScore,
        TieBreakRule.HigherSecondaryScore,
        TieBreakRule.HeadToHead,
        -> resolveTieBreakGroups(ids, rules.drop(1), state, direction)
    }
}

private fun groupByKey(
    ids: List<UUID>,
    values: Map<UUID, Int>,
    ascending: Boolean,
    rules: List<TieBreakRule>,
    state: MatchState,
    direction: Direction,
): List<List<UUID>> {
    val grouped = ids.groupBy { values.getValue(it) }
    val orderedKeys = if (ascending) grouped.keys.sorted() else grouped.keys.sortedDescending()
    return orderedKeys.flatMap { key -> resolveTieBreakGroups(grouped.getValue(key), rules, state, direction) }
}

private fun bestSingleRoundValue(
    id: UUID,
    state: MatchState,
    direction: Direction,
): Int {
    val values =
        state.rounds.mapNotNull { round ->
            round.entries.firstOrNull { it.participantID == id }?.computedValue
        }
    if (values.isEmpty()) return 0
    return if (direction == Direction.HighestWins) values.max() else values.min()
}

private fun roundsClosedCount(
    id: UUID,
    state: MatchState,
): Int =
    state.rounds.count { round ->
        round.entries
            .firstOrNull { it.participantID == id }
            ?.modifiers
            ?.contains(ModifierID.closedRound) == true
    }
