package com.quimene.catalog.games

import com.quimene.catalog.testing.SeededGenerator
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.End
import com.quimene.domain.rules.EndCondition
import com.quimene.domain.rules.EndConditionType
import com.quimene.domain.rules.EntryKind
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.LocalizedText
import com.quimene.domain.rules.Players
import com.quimene.domain.rules.ScoreEntrySpec
import com.quimene.domain.rules.Scoring
import com.quimene.domain.rules.TieBreakRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID

/**
 * Miroir de `TarotRulesV1Tests.swift` — « Somme des scores d'une donne = 0 », invariant du
 * tableau doc 10, testé ici jamais par une assertion à l'exécution dans `score()` (arithmétique
 * entière déterministe). Complète aussi les golden Tarot avec des cas trop fins pour y tenir
 * (bornes de multiplicateur, chelem).
 */
class TarotRulesV1Test {
    private fun definition(): GameDefinition =
        GameDefinition(
            id = "tarot-test",
            specVersion = 1,
            rulesVersion = 1,
            name = LocalizedText(fr = "Test"),
            symbol = "circle",
            players = Players(min = 3, max = 5),
            scoring =
                Scoring(
                    direction = Direction.HighestWins,
                    entry = ScoreEntrySpec(kind = EntryKind.Structured, min = 0, max = 91),
                ),
            engine = TarotRulesV1.ENGINE_ID,
            end = End(conditions = listOf(EndCondition(type = EndConditionType.RoundLimit, value = 100))),
            tieBreak = listOf(TieBreakRule.Shared),
        )

    private fun participants(count: Int): List<Participant> =
        (0 until count).map {
            Participant(displayName = "J$it", seatIndex = it)
        }

    private fun state(participants: List<Participant>): MatchState =
        MatchState.create(
            matchID = UUID.randomUUID(),
            gameID = "tarot-test",
            rulesVersion = 1,
            variants = VariantSelection(),
            participants = participants,
        )

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `the sum of a hand's scores is always zero`(seed: Int) {
        val generator = SeededGenerator(seed)
        val playerCount = generator.randomElement(listOf(3, 4, 5))
        val participants = participants(playerCount)
        val definition = definition()
        val rules = TarotRulesV1()
        val state = state(participants)

        val taker = generator.randomElement(participants)
        val others = participants.filterNot { it.id == taker.id }
        val partner = if (playerCount == 5) generator.randomElement(others) else null

        val modifiers = mutableSetOf(ModifierID("isTaker"))
        if (generator.nextBoolean()) modifiers += ModifierID("petitAuBout")
        if (generator.nextBoolean()) modifiers += ModifierID("chelemAnnounced")
        if (generator.nextBoolean()) modifiers += ModifierID("chelemAchieved")

        val inputs =
            mutableListOf(
                ScoreInput(
                    participantID = taker.id,
                    rawValue = generator.nextInt(0..91),
                    detail =
                        tarotHandScoreDetail(
                            contract = generator.nextInt(0..3),
                            bouts = generator.nextInt(0..3),
                            poignee = generator.nextInt(0..3),
                        ),
                    modifiers = modifiers,
                ),
            )
        if (partner != null) {
            inputs += ScoreInput(participantID = partner.id, rawValue = 0, modifiers = setOf(ModifierID("isPartner")))
        }

        val draft = RoundDraft(index = 0, inputs = inputs)
        val entries = rules.score(draft, state, definition)

        entries.size shouldBe playerCount
        entries.sumOf { it.computedValue } shouldBe 0
    }

    @Test
    fun `a passed hand scores zero for everyone`() {
        val participants = participants(4)
        val definition = definition()
        val rules = TarotRulesV1()
        val state = state(participants)

        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    listOf(
                        ScoreInput(
                            participantID = participants[0].id,
                            rawValue = 0,
                            modifiers = setOf(ModifierID("passed")),
                        ),
                    ),
            )
        val entries = rules.score(draft, state, definition)

        entries.size shouldBe 4
        entries.all { it.computedValue == 0 } shouldBe true
    }

    @Test
    fun `guard sans le chien versus guard contre le chien at the same margin`() {
        val participants = participants(4)
        val definition = definition()
        val rules = TarotRulesV1()
        val state = state(participants)

        // bouts: 0 => requis 56 ; points 70 => écart +14 => base = 25 + 14 = 39.
        fun score(contract: Int) =
            rules.score(
                RoundDraft(
                    index = 0,
                    inputs =
                        listOf(
                            ScoreInput(
                                participantID = participants[0].id,
                                rawValue = 70,
                                detail = tarotHandScoreDetail(contract = contract, bouts = 0, poignee = 0),
                                modifiers = setOf(ModifierID("isTaker")),
                            ),
                        ),
                ),
                state,
                definition,
            )

        // Garde sans le chien : magnitude = 39 x 4 = 156, preneur (4 joueurs) = 3 x 156 = 468.
        val sansLeChien = score(2)
        sansLeChien.first { it.participantID == participants[0].id }.computedValue shouldBe 468
        sansLeChien.first { it.participantID == participants[1].id }.computedValue shouldBe -156

        // Garde contre le chien : magnitude = 39 x 6 = 234, preneur = 3 x 234 = 702.
        val contreLeChien = score(3)
        contreLeChien.first { it.participantID == participants[0].id }.computedValue shouldBe 702
        contreLeChien.first { it.participantID == participants[1].id }.computedValue shouldBe -234
    }

    @Test
    fun `chelem bonus combinations`() {
        val participants = participants(4)
        val definition = definition()
        val rules = TarotRulesV1()
        val state = state(participants)

        // bouts: 0 => requis 56 ; points 70 => écart +14 ; contrat Petite (x1) => base x mult = 39.
        fun takerScore(
            announced: Boolean,
            achieved: Boolean,
        ): Int {
            val modifiers = mutableSetOf(ModifierID("isTaker"))
            if (announced) modifiers += ModifierID("chelemAnnounced")
            if (achieved) modifiers += ModifierID("chelemAchieved")
            val draft =
                RoundDraft(
                    index = 0,
                    inputs =
                        listOf(
                            ScoreInput(
                                participantID = participants[0].id,
                                rawValue = 70,
                                detail = tarotHandScoreDetail(contract = 0, bouts = 0, poignee = 0),
                                modifiers = modifiers,
                            ),
                        ),
                )
            return rules.score(draft, state, definition).first { it.participantID == participants[0].id }.computedValue
        }

        takerScore(announced = true, achieved = true) shouldBe 3 * (39 + 400)
        takerScore(announced = false, achieved = true) shouldBe 3 * (39 + 200)
        takerScore(announced = true, achieved = false) shouldBe 3 * (39 - 200)
        takerScore(announced = false, achieved = false) shouldBe 3 * 39
    }

    @Test
    fun `three player distribution on a failed contract`() {
        val participants = participants(3)
        val definition = definition()
        val rules = TarotRulesV1()
        val state = state(participants)

        // bouts: 2 => requis 41 ; points 30 => écart -11 (chuté) => base = 25 + 11 = 36, contrat
        // Petite (x1) => magnitude 36, signé négatif (écart < 0) => S = -36.
        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    listOf(
                        ScoreInput(
                            participantID = participants[0].id,
                            rawValue = 30,
                            detail = tarotHandScoreDetail(contract = 0, bouts = 2, poignee = 0),
                            modifiers = setOf(ModifierID("isTaker")),
                        ),
                    ),
            )
        val entries = rules.score(draft, state, definition)

        entries.first { it.participantID == participants[0].id }.computedValue shouldBe -72
        entries.first { it.participantID == participants[1].id }.computedValue shouldBe 36
        entries.first { it.participantID == participants[2].id }.computedValue shouldBe 36
    }

    @Test
    fun `five player distribution with a partner`() {
        val participants = participants(5)
        val definition = definition()
        val rules = TarotRulesV1()
        val state = state(participants)

        // bouts: 1 => requis 51 ; points 60 => écart +9 => base = 34, contrat Garde (x2) => S = 68.
        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    listOf(
                        ScoreInput(
                            participantID = participants[0].id,
                            rawValue = 60,
                            detail = tarotHandScoreDetail(contract = 1, bouts = 1, poignee = 0),
                            modifiers = setOf(ModifierID("isTaker")),
                        ),
                        ScoreInput(
                            participantID = participants[1].id,
                            rawValue = 0,
                            modifiers = setOf(ModifierID("isPartner")),
                        ),
                    ),
            )
        val entries = rules.score(draft, state, definition)

        entries.first { it.participantID == participants[0].id }.computedValue shouldBe 136
        entries.first { it.participantID == participants[1].id }.computedValue shouldBe 68
        for (defender in participants.drop(2)) {
            entries.first { it.participantID == defender.id }.computedValue shouldBe -68
        }
    }

    companion object {
        @JvmStatic
        fun seeds(): List<Int> = (0 until 50).toList()
    }
}
