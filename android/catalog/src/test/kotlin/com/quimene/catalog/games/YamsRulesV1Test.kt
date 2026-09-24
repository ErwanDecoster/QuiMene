package com.quimene.catalog.games

import com.quimene.domain.engine.MatchEngine
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.Category
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.End
import com.quimene.domain.rules.EndCondition
import com.quimene.domain.rules.EndConditionType
import com.quimene.domain.rules.EntryKind
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.LocalizedText
import com.quimene.domain.rules.Players
import com.quimene.domain.rules.Scope
import com.quimene.domain.rules.ScoreEntrySpec
import com.quimene.domain.rules.Scoring
import com.quimene.domain.rules.TieBreakRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `YamsRulesV1Tests.swift` — complète le golden `yams-01-bonus-et-grille-complete`
 * (qui ne teste pas d'égalité) : doc 05 « Départage : total de section basse, puis ex æquo ».
 * `higherSecondaryScore` n'est pas résolu par le classement générique, c'est
 * `YamsRulesV1.standings` qui le fait.
 */
class YamsRulesV1Test {
    private fun definition(): GameDefinition =
        GameDefinition(
            id = "yams-test",
            specVersion = 1,
            rulesVersion = 1,
            name = LocalizedText(fr = "Test"),
            symbol = "circle",
            players = Players(min = 2, max = 8),
            scoring =
                Scoring(
                    direction = Direction.HighestWins,
                    entry =
                        ScoreEntrySpec(
                            kind = EntryKind.CategorySheet,
                            categories =
                                listOf(
                                    Category(
                                        id = "catUp",
                                        label = LocalizedText(fr = "Haut"),
                                        section = Category.Section.Upper,
                                        scoring = Category.Scoring(kind = Category.Scoring.Kind.MultipleOf, value = 5),
                                    ),
                                    Category(
                                        id = "catA",
                                        label = LocalizedText(fr = "A"),
                                        section = Category.Section.Lower,
                                        scoring = Category.Scoring(kind = Category.Scoring.Kind.Fixed, value = 10),
                                    ),
                                    Category(
                                        id = "catB",
                                        label = LocalizedText(fr = "B"),
                                        section = Category.Section.Lower,
                                        scoring = Category.Scoring(kind = Category.Scoring.Kind.Fixed, value = 20),
                                    ),
                                ),
                        ),
                ),
            engine = YamsRulesV1.ENGINE_ID,
            end =
                End(
                    conditions =
                        listOf(
                            EndCondition(
                                type = EndConditionType.AllSheetsComplete,
                                scope = Scope.AllPlayers,
                                completeRound = false,
                            ),
                        ),
                ),
            tieBreak = listOf(TieBreakRule.HigherSecondaryScore, TieBreakRule.Shared),
        )

    @Test
    fun `a tie on the overall total is broken by the lower section`() {
        val definition = definition()
        val rules = YamsRulesV1()
        val alice = Participant(displayName = "Alice", seatIndex = 0)
        val bob = Participant(displayName = "Bob", seatIndex = 1)

        var state =
            MatchState.create(
                matchID = UUID.randomUUID(),
                gameID = "yams-test",
                rulesVersion = 1,
                variants = VariantSelection(),
                participants = listOf(alice, bob),
            )
        val engine = MatchEngine(now = { Instant.EPOCH })

        // Alice : catUp=5 (1 dé), catA obtenu (10), catB raté (0) -> total 15, section basse 10.
        state =
            engine.reduce(
                state,
                MatchEvent.RoundCommitted(
                    RoundDraft(index = 0, inputs = listOf(ScoreInput(alice.id, 1, yamsCategoryScoreDetail("catUp")))),
                ),
                rules,
                definition,
            )
        state =
            engine.reduce(
                state,
                MatchEvent.RoundCommitted(
                    RoundDraft(index = 1, inputs = listOf(ScoreInput(alice.id, 1, yamsCategoryScoreDetail("catA")))),
                ),
                rules,
                definition,
            )
        state =
            engine.reduce(
                state,
                MatchEvent.RoundCommitted(
                    RoundDraft(index = 2, inputs = listOf(ScoreInput(alice.id, 0, yamsCategoryScoreDetail("catB")))),
                ),
                rules,
                definition,
            )

        // Bob : catUp=15 (3 dés), catA raté (0), catB raté (0) -> total 15, section basse 0.
        state =
            engine.reduce(
                state,
                MatchEvent.RoundCommitted(
                    RoundDraft(index = 3, inputs = listOf(ScoreInput(bob.id, 3, yamsCategoryScoreDetail("catUp")))),
                ),
                rules,
                definition,
            )
        state =
            engine.reduce(
                state,
                MatchEvent.RoundCommitted(
                    RoundDraft(index = 4, inputs = listOf(ScoreInput(bob.id, 0, yamsCategoryScoreDetail("catA")))),
                ),
                rules,
                definition,
            )
        state =
            engine.reduce(
                state,
                MatchEvent.RoundCommitted(
                    RoundDraft(index = 5, inputs = listOf(ScoreInput(bob.id, 0, yamsCategoryScoreDetail("catB")))),
                ),
                rules,
                definition,
            )

        state.status shouldBe MatchStatus.Ended
        state.total(alice.id) shouldBe 15
        state.total(bob.id) shouldBe 15

        val standings = rules.standings(state, definition)
        val aliceStanding = standings.first { it.participantID == alice.id }
        val bobStanding = standings.first { it.participantID == bob.id }

        // Alice a la meilleure section basse (10 > 0) malgré l'égalité au total.
        aliceStanding.rank shouldBe 1
        bobStanding.rank shouldBe 2
        aliceStanding.sharedWith.shouldBeEmpty()
    }
}
