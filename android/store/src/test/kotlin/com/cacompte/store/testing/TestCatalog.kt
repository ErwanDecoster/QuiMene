package com.cacompte.store.testing

import com.cacompte.domain.rules.Direction
import com.cacompte.domain.rules.End
import com.cacompte.domain.rules.EntryKind
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.GameRules
import com.cacompte.domain.rules.LocalizedText
import com.cacompte.domain.rules.Players
import com.cacompte.domain.rules.ScoreEntrySpec
import com.cacompte.domain.rules.Scoring

/** Miroir de `TestFixtures.kt` (`:domain` tests) — `:store` ne dépend pas de `:catalog`
 * (mauvais sens de dépendance), donc pas de `GenericSumRules` réel disponible ici ; un
 * [GameRules] sans surcharge exerce les mêmes méthodes par défaut. */
private class TestGameRules(
    override val engineID: String,
) : GameRules

fun testGameDefinition(
    id: String = "test-game",
    direction: Direction = Direction.HighestWins,
): GameDefinition =
    GameDefinition(
        id = id,
        specVersion = 1,
        rulesVersion = 1,
        name = LocalizedText(fr = id),
        symbol = "circle",
        players = Players(min = 2, max = 8),
        scoring = Scoring(direction = direction, entry = ScoreEntrySpec(kind = EntryKind.Integer)),
        engine = "test.v1",
        end = End(conditions = emptyList()),
    )

fun testCatalog(definition: GameDefinition = testGameDefinition()): GameCatalog =
    GameCatalog(
        definitions = listOf(definition),
        engineTable = mapOf(definition.engine to { TestGameRules(definition.engine) }),
    )
