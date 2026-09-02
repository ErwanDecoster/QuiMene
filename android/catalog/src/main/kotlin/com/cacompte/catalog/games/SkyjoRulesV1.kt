package com.cacompte.catalog.games

import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.ModifierID
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.model.ValidationError
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules

/**
 * Miroir de `SkyjoRulesV1.swift` — doc 04 « Le cas Skyjo, en détail ». Seuls `validate`
 * (exclusivité du joueur qui ferme) et `score` (le doublement) sont propres à Skyjo ;
 * `endCheck`/`standings` restent ceux de l'extension par défaut de [GameRules], parce que `end`
 * et `tieBreak` de `skyjo.json` suffisent à les décrire.
 */
class SkyjoRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    override fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult {
        val closers = draft.inputs.filter { ModifierID.closedRound in it.modifiers }
        if (closers.size != 1) {
            return ValidationResult.Invalid(
                listOf(
                    ValidationError(
                        ValidationError.Field.ModifierField(ModifierID.closedRound),
                        "Un seul joueur ferme la manche.",
                    ),
                ),
            )
        }

        val extremes = draft.inputs.filter { it.rawValue < -24 || it.rawValue > 156 }
        return if (extremes.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Warning(
                listOf("Score inhabituel, à vérifier."),
            )
        }
    }

    override fun score(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): List<ScoreEntry> {
        val doublingEnabled = state.variants.bool("doublePenalty", default = true)
        val lowest = draft.inputs.minOfOrNull { it.rawValue } ?: 0
        val names = state.participants.associate { it.id to it.displayName }

        return draft.inputs.map { input ->
            val closed = ModifierID.closedRound in input.modifiers
            val isStrictlyLowest =
                input.rawValue == lowest && draft.inputs.count { it.rawValue == lowest } == 1
            val penalised = doublingEnabled && closed && !isStrictlyLowest && input.rawValue > 0

            ScoreEntry(
                participantID = input.participantID,
                rawValue = input.rawValue,
                computedValue = if (penalised) input.rawValue * 2 else input.rawValue,
                explanation =
                    if (penalised) {
                        "Score doublé : ${names[input.participantID] ?: "ce joueur"} a fermé la manche sans le score le plus bas."
                    } else {
                        null
                    },
                detail = null,
                modifiers = input.modifiers,
            )
        }
    }

    companion object {
        const val ENGINE_ID = "skyjo.v1"
    }
}
