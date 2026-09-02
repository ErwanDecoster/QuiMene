package com.cacompte.catalog.games

import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreEntry
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.rules.EndCheck
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.domain.rules.Standing

/**
 * Réservation de place pour le moteur Tarot (contrat, chute du preneur, poignées, 3/4/5 joueurs)
 * — **portage réel à l'étape C**, hors périmètre de cette session. Voir la note complète sur
 * [SkyjoRulesV1] : échec bruyant volontaire plutôt qu'un repli silencieux sur la logique
 * générique, qui calculerait un score Tarot faux.
 */
class TarotRulesV1 : GameRules {
    override val engineID: String = ENGINE_ID

    override fun validate(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): ValidationResult = notPortedYet()

    override fun score(
        draft: RoundDraft,
        state: MatchState,
        definition: GameDefinition,
    ): List<ScoreEntry> = notPortedYet()

    override fun endCheck(
        state: MatchState,
        definition: GameDefinition,
    ): EndCheck = notPortedYet()

    override fun standings(
        state: MatchState,
        definition: GameDefinition,
    ): List<Standing> = notPortedYet()

    private fun notPortedYet(): Nothing =
        TODO("TarotRulesV1 — porté à l'étape C (docs/11-portage-android.md), pas cette session")

    companion object {
        const val ENGINE_ID = "tarot.v1"
    }
}
