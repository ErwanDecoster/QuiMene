package com.cacompte.catalog.games

import com.cacompte.catalog.decodeDetail
import com.cacompte.catalog.toScoreDetail
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreDetail
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.ValidationError
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.rules.Category
import com.cacompte.domain.rules.EndCheck
import com.cacompte.domain.rules.EndReason
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.domain.rules.Standing
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Miroir de `YamsCategoryDetail` (`YamsRulesV1.swift`) — payload de [ScoreDetail], opaque pour
 * le moteur générique et Skyjo. Identifie la catégorie remplie par cette entrée ; `:domain` reste
 * agnostique du jeu, c'est donc `:catalog` (qui connaît Yams) qui porte ce type.
 */
@Serializable
data class YamsCategoryDetail(
    val categoryID: String,
)

/**
 * Miroir de `YamsRulesV1.swift`. Contrairement aux autres jeux du catalogue, une « manche »
 * n'est pas un tour de table homogène : chaque [RoundDraft] ne contient qu'une seule entrée,
 * celle du joueur qui remplit une catégorie. Le bonus de section haute est ajouté à l'entrée qui
 * fait franchir le seuil plutôt que via une entrée synthétique séparée.
 *
 * Simplification assumée (identique à Swift) : la règle du « Yams supplémentaire » (+100, joker
 * officiel) n'est pas implémentée — une catégorie ne peut être remplie qu'une fois par joueur.
 */
class YamsRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    override fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult {
        val input =
            draft.inputs.singleOrNull()
                ?: return ValidationResult.Invalid(
                    listOf(ValidationError(ValidationError.Field.General, "Une seule catégorie est remplie par tour.")),
                )
        val categoryID = categoryID(input)
        val category =
            categoryID?.let { category(it, definition) }
                ?: return ValidationResult.Invalid(
                    listOf(
                        ValidationError(
                            ValidationError.Field.ParticipantField(input.participantID),
                            "Catégorie inconnue.",
                        ),
                    ),
                )
        if (categoryID in filledCategories(input.participantID, state)) {
            return ValidationResult.Invalid(
                listOf(
                    ValidationError(
                        ValidationError.Field.ParticipantField(input.participantID),
                        "Cette catégorie est déjà remplie.",
                    ),
                ),
            )
        }

        when (category.scoring.kind) {
            Category.Scoring.Kind.MultipleOf ->
                if (input.rawValue !in 0..5) {
                    return ValidationResult.Invalid(
                        listOf(
                            ValidationError(
                                ValidationError.Field.ParticipantField(input.participantID),
                                "Nombre de dés invalide (0 à 5).",
                            ),
                        ),
                    )
                }

            Category.Scoring.Kind.Fixed ->
                if (input.rawValue != 0 && input.rawValue != 1) {
                    return ValidationResult.Invalid(
                        listOf(
                            ValidationError(
                                ValidationError.Field.ParticipantField(input.participantID),
                                "Valeur invalide.",
                            ),
                        ),
                    )
                }

            Category.Scoring.Kind.SumOfDice -> {
                val max = category.scoring.max ?: 30
                if (input.rawValue != 0 && input.rawValue !in 5..max) {
                    return ValidationResult.Invalid(
                        listOf(
                            ValidationError(
                                ValidationError.Field.ParticipantField(input.participantID),
                                "Somme invalide.",
                            ),
                        ),
                    )
                }
            }
        }
        return ValidationResult.Valid
    }

    override fun score(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): List<ScoreEntry> {
        val input = draft.inputs.firstOrNull() ?: return emptyList()
        val categoryID = categoryID(input) ?: return emptyList()
        val category = category(categoryID, definition) ?: return emptyList()

        val base = baseValue(input.rawValue, category.scoring.kind, category)

        var bonus = 0
        var explanation: String? = null
        if (category.section == Category.Section.Upper) {
            val threshold = state.variants.int("upperBonusThreshold", default = 63)
            val priorUpperTotal = sectionTotal(input.participantID, state, definition, Category.Section.Upper)
            if (priorUpperTotal < threshold && priorUpperTotal + base >= threshold) {
                bonus = 35
                explanation = "Bonus de section haute (+35)"
            }
        }

        return listOf(
            ScoreEntry(
                participantID = input.participantID,
                rawValue = input.rawValue,
                computedValue = base + bonus,
                explanation = explanation,
                detail = input.detail,
                modifiers = input.modifiers,
            ),
        )
    }

    override fun endCheck(
        state: MatchState,
        definition: GameDefinition,
    ): EndCheck {
        val categoryCount =
            definition.scoring.entry.categories
                ?.size ?: 13
        val allComplete = state.participants.all { filledCategories(it.id, state).size == categoryCount }
        return if (allComplete) EndCheck.Ended(EndReason.AllSheetsComplete) else EndCheck.Continue
    }

    /** Doc 05 : « Départage : total de section basse, puis ex æquo » — `higherSecondaryScore`
     * n'est pas résolu par le classement générique (données non modélisées), donc surchargé
     * intégralement ici plutôt que de composer avec la récursion de départage générique. */
    override fun standings(
        state: MatchState,
        definition: GameDefinition,
    ): List<Standing> {
        val totals = state.totals()
        val lowerTotals =
            state.participants.associate {
                it.id to sectionTotal(it.id, state, definition, Category.Section.Lower)
            }

        val groupedByTotal = state.participants.map { it.id }.groupBy { totals[it] ?: 0 }
        val orderedTotals = groupedByTotal.keys.sortedDescending() // Yams : le plus haut gagne, toujours.

        val result = mutableListOf<Standing>()
        var rank = 1
        for (total in orderedTotals) {
            val tied = groupedByTotal.getValue(total)
            val groupedBySecondary = tied.groupBy { lowerTotals[it] ?: 0 }
            for (secondary in groupedBySecondary.keys.sortedDescending()) {
                val group = groupedBySecondary.getValue(secondary)
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

    private fun categoryID(input: ScoreInput): String? = input.detail.decodeDetail<YamsCategoryDetail>()?.categoryID

    private fun category(
        id: String,
        definition: GameDefinition,
    ): Category? =
        definition.scoring.entry.categories
            ?.firstOrNull { it.id == id }

    private fun baseValue(
        rawValue: Int,
        kind: Category.Scoring.Kind,
        category: Category,
    ): Int =
        when (kind) {
            Category.Scoring.Kind.MultipleOf -> rawValue * (category.scoring.value ?: 0)
            Category.Scoring.Kind.Fixed -> if (rawValue == 1) (category.scoring.value ?: 0) else 0
            Category.Scoring.Kind.SumOfDice -> rawValue
        }

    private fun filledCategories(
        participantID: UUID,
        state: MatchState,
    ): Set<String> =
        state.rounds
            .flatMap { round ->
                round.entries.filter { it.participantID == participantID }.mapNotNull { categoryIDFrom(it.detail) }
            }.toSet()

    private fun categoryIDFrom(detail: ScoreDetail?): String? = detail.decodeDetail<YamsCategoryDetail>()?.categoryID

    /** Recalculée depuis `rawValue` (jamais `computedValue`) : la section haute peut déjà porter
     * le bonus sur l'une de ses entrées, ce qui fausserait un cumul basé sur les valeurs
     * calculées pour déterminer le franchissement du seuil par l'entrée suivante. */
    private fun sectionTotal(
        participantID: UUID,
        state: MatchState,
        definition: GameDefinition,
        section: Category.Section,
    ): Int {
        var total = 0
        for (round in state.rounds) {
            for (entry in round.entries) {
                if (entry.participantID != participantID) continue
                val id = categoryIDFrom(entry.detail) ?: continue
                val category = category(id, definition) ?: continue
                if (category.section != section) continue
                total +=
                    if (section == Category.Section.Upper) {
                        baseValue(entry.rawValue, category.scoring.kind, category)
                    } else {
                        entry.computedValue
                    }
            }
        }
        return total
    }

    companion object {
        const val ENGINE_ID = "yams.v1"
    }
}

internal fun yamsCategoryScoreDetail(categoryID: String): ScoreDetail = YamsCategoryDetail(categoryID).toScoreDetail()
