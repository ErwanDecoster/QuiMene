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
 * Réservation de place pour le moteur Skyjo (doublement du score si le fermeur n'a pas le score
 * strictement le plus bas, seuil configurable) — **portage réel à l'étape C**
 * (docs/11-portage-android.md), hors périmètre de cette session.
 *
 * `engineID` correct et enregistré dans [com.cacompte.catalog.GameCatalogEmbedded] pour que le
 * catalogue charge les 20 définitions sans exception (`skyjo.json` référence `"skyjo.v1"`) —
 * mais chaque méthode échoue bruyamment plutôt que de retomber silencieusement sur la logique
 * générique, qui calculerait un score Skyjo **faux** (pas de doublement) sans le signaler. Un
 * score faux est la seule faute grave de ce projet (doc 10) : un échec explicite vaut mieux
 * qu'un mauvais calcul silencieux.
 */
class SkyjoRulesV1 : GameRules {
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
        TODO("SkyjoRulesV1 — porté à l'étape C (docs/11-portage-android.md), pas cette session")

    companion object {
        const val ENGINE_ID = "skyjo.v1"
    }
}
