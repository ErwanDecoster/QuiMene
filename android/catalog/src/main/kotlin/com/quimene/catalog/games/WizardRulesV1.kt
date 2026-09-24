package com.quimene.catalog.games

import com.quimene.catalog.decodeDetail
import com.quimene.catalog.toScoreDetail
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreDetail
import com.quimene.domain.model.ScoreEntry
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.ValidationError
import com.quimene.domain.model.ValidationResult
import com.quimene.domain.rules.EndCheck
import com.quimene.domain.rules.EndReason
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Miroir de `WizardBidDetail` (`WizardRulesV1.swift`) — payload de [ScoreDetail] portant
 * l'annonce, le résultat réel vivant dans `ScoreInput.rawValue`.
 */
@Serializable
data class WizardBidDetail(
    val bid: Int,
)

/**
 * Miroir de `WizardRulesV1.swift` — contrairement à Yams, une manche est simultanée : autant de
 * [ScoreInput] que de participants. `EndCondition.valueExpression` (« 60 / playerCount ») existe
 * dans le schéma mais n'est lu par aucun code de `:domain` — `endCheck` calcule donc directement
 * la limite, même précédent que [MolkkyRulesV1]/[YamsRulesV1], qui ignorent déjà
 * `definition.end.conditions`.
 */
class WizardRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    /** Doc 05 : « la somme des plis réalisés doit égaler le numéro de la manche — contrôle
     * bloquant ». `MatchEngine.reduce` n'appelle jamais `validate` (seule la couche modèle de
     * l'app le fait avant `commitRound`) : ce rejet ne peut donc jamais être exercé par un golden
     * file, uniquement par un test direct sur [validate]. */
    override fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult {
        val roundNumber = state.rounds.size + 1
        for (input in draft.inputs) {
            if (input.rawValue !in 0..roundNumber) {
                return ValidationResult.Invalid(
                    listOf(
                        ValidationError(
                            ValidationError.Field.ParticipantField(input.participantID),
                            "Résultat invalide (0 à $roundNumber).",
                        ),
                    ),
                )
            }
            val bid = bid(input)
            if (bid == null || bid !in 0..roundNumber) {
                return ValidationResult.Invalid(
                    listOf(
                        ValidationError(
                            ValidationError.Field.ParticipantField(input.participantID),
                            "Annonce invalide (0 à $roundNumber).",
                        ),
                    ),
                )
            }
        }
        val totalTricks = draft.inputs.sumOf { it.rawValue }
        if (totalTricks != roundNumber) {
            return ValidationResult.Invalid(
                listOf(
                    ValidationError(
                        ValidationError.Field.General,
                        "Le total des plis réalisés ($totalTricks) doit égaler $roundNumber.",
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
    ): List<ScoreEntry> =
        draft.inputs.map { input ->
            val bid = bid(input) ?: 0
            val computedValue = if (bid == input.rawValue) 20 + 10 * bid else -10 * abs(bid - input.rawValue)
            ScoreEntry(
                participantID = input.participantID,
                rawValue = input.rawValue,
                computedValue = computedValue,
                detail = input.detail,
                modifiers = input.modifiers,
            )
        }

    override fun endCheck(
        state: MatchState,
        definition: GameDefinition,
    ): EndCheck {
        val target = 60 / state.participants.size
        return if (state.rounds.size >= target) EndCheck.Ended(EndReason.RoundLimit) else EndCheck.Continue
    }

    private fun bid(input: ScoreInput): Int? = input.detail.decodeDetail<WizardBidDetail>()?.bid

    companion object {
        const val ENGINE_ID = "wizard.v1"
    }
}

internal fun wizardBidScoreDetail(bid: Int): ScoreDetail = WizardBidDetail(bid).toScoreDetail()
