package com.quimene.catalog.games

import com.quimene.domain.model.MatchState
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreEntry
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.ValidationError
import com.quimene.domain.model.ValidationResult
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules

/**
 * Miroir de `BeloteRulesV1.swift` — premier jeu par équipes. Simplification assumée pour cette
 * V1 : « Belote classique », sans annonce de contrat chiffrée — le preneur réussit s'il dépasse
 * 81 points (majorité de 162), sinon la défense encaisse 162 à plat. La coinche/contrée reste
 * une variante future.
 *
 * Astuce d'implémentation : chaque coéquipier reçoit une entrée **identique** (même
 * `computedValue`, le score de l'équipe) plutôt qu'une entrée par équipe — `endCheck`/
 * `standings` de l'extension par défaut de [GameRules] opèrent déjà sur `state.totals()` par
 * participant, dupliquer l'entrée suffit à leur faire produire le bon comportement pour une
 * équipe sans surcharger ni l'un ni l'autre.
 */
class BeloteRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    override fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult {
        if (draft.inputs.size != 2) {
            return ValidationResult.Invalid(
                listOf(ValidationError(ValidationError.Field.General, "Deux équipes attendues par donne.")),
            )
        }
        val takers = draft.inputs.filter { ModifierID("isTaker") in it.modifiers }
        val takerInput =
            takers.singleOrNull()
                ?: return ValidationResult.Invalid(
                    listOf(ValidationError(ValidationError.Field.General, "Une seule équipe preneuse par donne.")),
                )
        if (takerInput.rawValue !in 0..162) {
            return ValidationResult.Invalid(
                listOf(
                    ValidationError(
                        ValidationError.Field.ParticipantField(takerInput.participantID),
                        "Points invalides (0 à 162).",
                    ),
                ),
            )
        }
        return ValidationResult.Valid
    }

    override fun score(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): List<ScoreEntry> {
        if (draft.inputs.size != 2) return emptyList()
        val takerInput = draft.inputs.firstOrNull { ModifierID("isTaker") in it.modifiers } ?: return emptyList()
        val defenderInput =
            draft.inputs.firstOrNull { it.participantID != takerInput.participantID }
                ?: return emptyList()

        val capotInput = draft.inputs.firstOrNull { ModifierID("capot") in it.modifiers }
        var takerScore: Int
        var defenderScore: Int

        if (capotInput != null) {
            val takerAchievedCapot = capotInput.participantID == takerInput.participantID
            takerScore = if (takerAchievedCapot) 250 else 0
            defenderScore = if (takerAchievedCapot) 0 else 250
        } else if (takerInput.rawValue > 81) {
            takerScore = takerInput.rawValue
            defenderScore = 162 - takerInput.rawValue
        } else {
            // Le preneur chute : la défense encaisse 162 à plat (pas de bonus de contrat, aucun
            // montant n'étant annoncé dans cette version simplifiée).
            takerScore = 0
            defenderScore = 162
        }

        if (ModifierID("beloteRebelote") in takerInput.modifiers) takerScore += 20
        if (ModifierID("beloteRebelote") in defenderInput.modifiers) defenderScore += 20

        return teamEntries(takerInput, takerScore, state) + teamEntries(defenderInput, defenderScore, state)
    }

    private fun teamEntries(
        input: ScoreInput,
        computedValue: Int,
        state: MatchState,
    ): List<ScoreEntry> {
        val representative = state.participants.firstOrNull { it.id == input.participantID } ?: return emptyList()
        return state.participants
            .filter { it.teamID == representative.teamID }
            .map { participant ->
                ScoreEntry(
                    participantID = participant.id,
                    rawValue = input.rawValue,
                    computedValue = computedValue,
                    modifiers = input.modifiers,
                )
            }
    }

    companion object {
        const val ENGINE_ID = "belote.v1"
    }
}
