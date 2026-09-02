package com.cacompte.catalog.games

import com.cacompte.domain.engine.MatchEngine
import com.cacompte.domain.engine.MatchEvent
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.ModifierID
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.VariantSelection
import com.cacompte.domain.rules.Direction
import com.cacompte.domain.rules.End
import com.cacompte.domain.rules.EndCondition
import com.cacompte.domain.rules.EndConditionType
import com.cacompte.domain.rules.EntryKind
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.LocalizedText
import com.cacompte.domain.rules.Players
import com.cacompte.domain.rules.ScoreEntrySpec
import com.cacompte.domain.rules.Scoring
import com.cacompte.domain.rules.TieBreakRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `BeloteRulesV1Tests.swift` — complète le golden `belote-01-equipes-et-seuil` (qui ne
 * couvre pas le capot) : doc 05 « Capot (250) ».
 */
class BeloteRulesV1Test {
    private fun definition(): GameDefinition =
        GameDefinition(
            id = "belote-test",
            specVersion = 1,
            rulesVersion = 1,
            name = LocalizedText(fr = "Test"),
            symbol = "circle",
            players = Players(min = 4, max = 4, teams = Players.Teams(size = 2)),
            scoring = Scoring(direction = Direction.HighestWins, entry = ScoreEntrySpec(kind = EntryKind.Structured)),
            engine = BeloteRulesV1.ENGINE_ID,
            end = End(conditions = listOf(EndCondition(type = EndConditionType.ScoreThreshold, value = 1000))),
            tieBreak = listOf(TieBreakRule.Shared),
        )

    @Test
    fun `capot awards a flat 250 to the team that achieves it, even the defense`() {
        val definition = definition()
        val rules = BeloteRulesV1()
        val alice = Participant(displayName = "Alice", seatIndex = 0, teamID = "A")
        val bob = Participant(displayName = "Bob", seatIndex = 1, teamID = "A")
        val chloe = Participant(displayName = "Chloé", seatIndex = 2, teamID = "B")
        val david = Participant(displayName = "David", seatIndex = 3, teamID = "B")

        var state =
            MatchState.create(
                matchID = UUID.randomUUID(),
                gameID = "belote-test",
                rulesVersion = 1,
                variants = VariantSelection(),
                participants = listOf(alice, bob, chloe, david),
            )
        val engine = MatchEngine(now = { Instant.EPOCH })

        // B (Chloé) prend mais c'est la défense (A) qui réalise le capot : A doit tout de même
        // encaisser 250, B (preneuse) 0 — le capot n'est pas réservé au preneur.
        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    listOf(
                        ScoreInput(participantID = chloe.id, rawValue = 40, modifiers = setOf(ModifierID("isTaker"))),
                        ScoreInput(participantID = alice.id, rawValue = 122, modifiers = setOf(ModifierID("capot"))),
                    ),
            )
        state = engine.reduce(state, MatchEvent.RoundCommitted(draft), rules, definition)

        state.total(alice.id) shouldBe 250
        state.total(bob.id) shouldBe 250
        state.total(chloe.id) shouldBe 0
        state.total(david.id) shouldBe 0
    }
}
