package com.cacompte.catalog.games

import com.cacompte.catalog.decodeDetail
import com.cacompte.catalog.toScoreDetail
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.ModifierID
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreDetail
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.ValidationError
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.rules.EndCheck
import com.cacompte.domain.rules.EndReason
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Miroir de `TarotHandDetail` (`TarotRulesV1.swift`) — payload de [ScoreDetail], opaque au
 * moteur générique et aux autres jeux. `contract`/`bouts`/`poignee` sont des entiers, pas des
 * `flag`/`exclusiveFlag` : un `Set<ModifierID>` ne porte que des étiquettes, jamais de valeur
 * associée.
 */
@Serializable
data class TarotHandDetail(
    /** 0 Petite, 1 Garde, 2 Garde sans le chien, 3 Garde contre le chien. */
    val contract: Int,
    /** Nombre de bouts (0 à 3) capturés par le preneur — détermine le seuil requis. */
    val bouts: Int,
    /** 0 aucune, 1 simple, 2 double, 3 triple. */
    val poignee: Int,
)

/**
 * Miroir de `TarotRulesV1.swift` — le calcul le plus dense du catalogue. Une donne est
 * représentée par 1 ou 2 [ScoreInput] (jamais un par participant) : celui du preneur (toujours),
 * et celui du partenaire à 5 joueurs (« roi appelé », révélé à chaque donne). Les défenseurs ne
 * soumettent jamais rien, leur part est calculée et distribuée par [score].
 *
 * La somme des scores d'une donne est nulle par construction (invariant testé, pas assertion à
 * l'exécution — arithmétique entière déterministe).
 */
class TarotRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    override fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult {
        if (draft.inputs.size == 1 && ModifierID("passed") in draft.inputs[0].modifiers) {
            return ValidationResult.Valid
        }

        val takerInput =
            draft.inputs.filter { ModifierID("isTaker") in it.modifiers }.singleOrNull()
                ?: return ValidationResult.Invalid(
                    listOf(
                        ValidationError(
                            ValidationError.Field.General,
                            "Une donne doit avoir un preneur, ou être marquée passée.",
                        ),
                    ),
                )

        if (state.participants.size == 5) {
            val partnerInput = draft.inputs.filter { ModifierID("isPartner") in it.modifiers }.singleOrNull()
            if (partnerInput == null || partnerInput.participantID == takerInput.participantID) {
                return ValidationResult.Invalid(
                    listOf(
                        ValidationError(
                            ValidationError.Field.General,
                            "Un partenaire (roi appelé) est requis à 5 joueurs.",
                        ),
                    ),
                )
            }
        }

        if (takerInput.rawValue !in 0..91) {
            return ValidationResult.Invalid(
                listOf(
                    ValidationError(
                        ValidationError.Field.ParticipantField(takerInput.participantID),
                        "Points invalides (0 à 91).",
                    ),
                ),
            )
        }

        val detail = handDetail(takerInput)
        if (detail == null || detail.contract !in 0..3 || detail.bouts !in 0..3 || detail.poignee !in 0..3) {
            return ValidationResult.Invalid(
                listOf(
                    ValidationError(
                        ValidationError.Field.ParticipantField(takerInput.participantID),
                        "Contrat, bouts ou poignée invalides.",
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
        if (draft.inputs.size == 1 && ModifierID("passed") in draft.inputs[0].modifiers) {
            return state.participants.map { ScoreEntry(participantID = it.id, rawValue = 0, computedValue = 0) }
        }

        val takerInput = draft.inputs.firstOrNull { ModifierID("isTaker") in it.modifiers } ?: return emptyList()
        val detail = handDetail(takerInput) ?: return emptyList()
        val partnerID = draft.inputs.firstOrNull { ModifierID("isPartner") in it.modifiers }?.participantID

        val required = REQUIRED_POINTS[detail.bouts]
        val margin = takerInput.rawValue - required
        val petitAuBout = ModifierID("petitAuBout") in takerInput.modifiers
        val announced = ModifierID("chelemAnnounced") in takerInput.modifiers
        val achieved = ModifierID("chelemAchieved") in takerInput.modifiers

        val base = 25 + abs(margin) + (if (petitAuBout) 10 else 0)
        val chelemBonus = if (achieved) (if (announced) 400 else 200) else (if (announced) -200 else 0)
        val magnitude = base * MULTIPLIERS[detail.contract] + POIGNEE_BONUSES[detail.poignee] + chelemBonus
        val signedScore = if (margin >= 0) magnitude else -magnitude

        val entries =
            mutableListOf(
                ScoreEntry(
                    participantID = takerInput.participantID,
                    rawValue = takerInput.rawValue,
                    computedValue = takerScore(signedScore, state.participants.size),
                    detail = takerInput.detail,
                    modifiers = takerInput.modifiers,
                ),
            )
        if (partnerID != null) {
            entries += ScoreEntry(participantID = partnerID, rawValue = 0, computedValue = signedScore)
        }
        for (participant in state.participants) {
            if (participant.id == takerInput.participantID || participant.id == partnerID) continue
            entries += ScoreEntry(participantID = participant.id, rawValue = 0, computedValue = -signedScore)
        }
        return entries
    }

    /** Doc 05 : preneur seul (3/4 joueurs) → `2S`/`3S` ; preneur avec partenaire (5 joueurs) →
     * `2S`, le partenaire recevant `S` séparément (voir [score]). */
    private fun takerScore(
        signedScore: Int,
        playerCount: Int,
    ): Int =
        if (playerCount ==
            4
        ) {
            signedScore * 3
        } else {
            signedScore * 2
        }

    override fun endCheck(
        state: MatchState,
        definition: GameDefinition,
    ): EndCheck {
        val handsPerPlayer = state.variants.int("handsPerPlayer", default = 2)
        val target = state.participants.size * handsPerPlayer
        return if (state.rounds.size >= target) EndCheck.Ended(EndReason.RoundLimit) else EndCheck.Continue
    }

    private fun handDetail(input: ScoreInput): TarotHandDetail? = input.detail.decodeDetail<TarotHandDetail>()

    companion object {
        const val ENGINE_ID = "tarot.v1"

        /** Points requis selon le nombre de bouts capturés par le preneur (doc 05). */
        private val REQUIRED_POINTS = listOf(56, 51, 41, 36)

        /** Multiplicateur selon le contrat (0 Petite … 3 Garde contre le chien). */
        private val MULTIPLIERS = listOf(1, 2, 4, 6)

        /** Bonus de poignée (0 aucune … 3 triple). */
        private val POIGNEE_BONUSES = listOf(0, 20, 30, 40)
    }
}

internal fun tarotHandScoreDetail(
    contract: Int,
    bouts: Int,
    poignee: Int,
): ScoreDetail = TarotHandDetail(contract, bouts, poignee).toScoreDetail()
