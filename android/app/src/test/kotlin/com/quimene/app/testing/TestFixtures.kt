package com.quimene.app.testing

import com.quimene.domain.model.VariantValue
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.End
import com.quimene.domain.rules.EntryKind
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import com.quimene.domain.rules.LocalizedText
import com.quimene.domain.rules.Players
import com.quimene.domain.rules.ScoreEntrySpec
import com.quimene.domain.rules.Scoring
import com.quimene.domain.rules.Variant
import com.quimene.domain.rules.VariantKind

/** Miroir de `TestGameRules` (`:store` tests) — aucune surcharge, exerce les méthodes par défaut
 * de [GameRules]. */
class TestGameRules(
    override val engineID: String,
) : GameRules

fun testCatalog(definition: GameDefinition): GameCatalog =
    GameCatalog(
        definitions = listOf(definition),
        engineTable = mapOf(definition.engine to { TestGameRules(definition.engine) }),
    )

/** Miroir de `TestCatalog.kt` (`:store` tests) — indépendant, `:app` ne peut pas dépendre du
 * source set de test d'un autre module. */
fun testGameDefinition(
    id: String = "test-game",
    direction: Direction = Direction.HighestWins,
    players: Players = Players(min = 2, max = 8),
    variants: List<Variant> = emptyList(),
): GameDefinition =
    GameDefinition(
        id = id,
        specVersion = 1,
        rulesVersion = 1,
        name = LocalizedText(fr = id),
        symbol = "circle",
        players = players,
        scoring = Scoring(direction = direction, entry = ScoreEntrySpec(kind = EntryKind.Integer)),
        engine = "test.v1",
        end = End(conditions = emptyList()),
        variants = variants,
    )

/** Doc 05 « Belote » — deux équipes de 2, pour exercer `teamAssignment`/`teamsAreValid`. */
fun testTeamGameDefinition(id: String = "test-team-game"): GameDefinition =
    testGameDefinition(
        id = id,
        players = Players(min = 4, max = 4, teams = Players.Teams(size = 2)),
    )

fun testIntVariant(
    id: String = "target",
    default: Int = 100,
): Variant =
    Variant(
        id = id,
        label = LocalizedText(fr = id),
        kind = VariantKind.IntegerChoice,
        defaultValue = VariantValue.IntValue(default),
    )
