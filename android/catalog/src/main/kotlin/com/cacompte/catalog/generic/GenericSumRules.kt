package com.cacompte.catalog.generic

import com.cacompte.domain.rules.GameRules

/**
 * Miroir de `GenericSumRules.swift` — corps vide, hérite entièrement des méthodes par défaut de
 * [GameRules] (14 des 20 jeux du catalogue, voir `spec/games (fichiers .json)`, champ `"engine"`).
 */
class GenericSumRules : GameRules {
    override val engineID: String = "generic.sum.v1"

    companion object {
        const val ENGINE_ID = "generic.sum.v1"
    }
}
