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
 * Réservation de place pour le moteur Yams (13 catégories, bonus de 35 si la section haute
 * atteint le seuil de variante) — **portage réel à l'étape C**, hors périmètre de cette session.
 * Voir la note complète sur [SkyjoRulesV1] : échec bruyant volontaire plutôt qu'un repli
 * silencieux sur la logique générique, qui calculerait un score Yams faux.
 */
class YamsRulesV1 : GameRules {
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
        TODO("YamsRulesV1 — porté à l'étape C (docs/11-portage-android.md), pas cette session")

    companion object {
        const val ENGINE_ID = "yams.v1"
    }
}
