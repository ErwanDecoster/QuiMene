package com.cacompte.catalog.games

import com.cacompte.domain.engine.MatchEngine
import com.cacompte.domain.engine.MatchEvent
import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.ValidationResult
import com.cacompte.domain.model.VariantSelection
import com.cacompte.domain.rules.Direction
import com.cacompte.domain.rules.End
import com.cacompte.domain.rules.EntryKind
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.LocalizedText
import com.cacompte.domain.rules.Players
import com.cacompte.domain.rules.ScoreEntrySpec
import com.cacompte.domain.rules.Scoring
import com.cacompte.domain.rules.TieBreakRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `WizardRulesV1Tests.swift` — « la somme des plis réalisés doit égaler le numéro de
 * la manche, contrôle bloquant ». `MatchEngine.reduce` n'appelle jamais `validate` : ce rejet ne
 * peut donc jamais être exercé par un golden file, uniquement par un test direct ici.
 */
class WizardRulesV1Test {
    private fun definition(): GameDefinition =
        GameDefinition(
            id = "wizard-test",
            specVersion = 1,
            rulesVersion = 1,
            name = LocalizedText(fr = "Test"),
            symbol = "circle",
            players = Players(min = 3, max = 6),
            scoring =
                Scoring(
                    direction = Direction.HighestWins,
                    entry = ScoreEntrySpec(kind = EntryKind.PredictionAndResult, min = 0, max = 20),
                ),
            engine = WizardRulesV1.ENGINE_ID,
            end = End(conditions = emptyList()),
            tieBreak = listOf(TieBreakRule.Shared),
        )

    private fun participants(count: Int): List<Participant> =
        (0 until count).map {
            Participant(displayName = "J$it", seatIndex = it)
        }

    private fun state(participants: List<Participant>): MatchState =
        MatchState.create(
            matchID = UUID.randomUUID(),
            gameID = "wizard-test",
            rulesVersion = 1,
            variants = VariantSelection(),
            participants = participants,
        )

    @Test
    fun `rejects a round whose realised tricks do not sum to the round number`() {
        val definition = definition()
        val rules = WizardRulesV1()
        val participants = participants(4)
        val state = state(participants)

        // Manche 1 (state.rounds.size == 0 => numéro de manche == 1) : les 4 joueurs déclarent
        // chacun 1 pli réalisé — la somme (4) ne peut jamais égaler 1, rejet garanti.
        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    participants.map {
                        ScoreInput(
                            participantID = it.id,
                            rawValue = 1,
                            detail = wizardBidScoreDetail(1),
                        )
                    },
            )

        rules.validate(draft, state, definition).shouldBeInstanceOf<ValidationResult.Invalid>()
    }

    @Test
    fun `accepts a round whose realised tricks sum matches the round number`() {
        val definition = definition()
        val rules = WizardRulesV1()
        val participants = participants(4)
        val state = state(participants)

        // Manche 1 : un seul pli disponible, un seul joueur le remporte — somme = 1.
        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    listOf(
                        ScoreInput(participantID = participants[0].id, rawValue = 1, detail = wizardBidScoreDetail(1)),
                        ScoreInput(participantID = participants[1].id, rawValue = 0, detail = wizardBidScoreDetail(0)),
                        ScoreInput(participantID = participants[2].id, rawValue = 0, detail = wizardBidScoreDetail(1)),
                        ScoreInput(participantID = participants[3].id, rawValue = 0, detail = wizardBidScoreDetail(0)),
                    ),
            )

        rules.validate(draft, state, definition).shouldBeInstanceOf<ValidationResult.Valid>()
    }

    @Test
    fun `score rewards an exact bid, penalises a missed bid`() {
        val definition = definition()
        val rules = WizardRulesV1()
        val participants = participants(3)
        val state = state(participants)

        val draft =
            RoundDraft(
                index = 0,
                inputs =
                    listOf(
                        // Annonce 2, réalisé 2 : 20 + 10x2 = 40.
                        ScoreInput(participantID = participants[0].id, rawValue = 2, detail = wizardBidScoreDetail(2)),
                        // Annonce 3, réalisé 1 : -10x|3-1| = -20.
                        ScoreInput(participantID = participants[1].id, rawValue = 1, detail = wizardBidScoreDetail(3)),
                        // Annonce 0, réalisé 1 : -10x|0-1| = -10.
                        ScoreInput(participantID = participants[2].id, rawValue = 1, detail = wizardBidScoreDetail(0)),
                    ),
            )
        val entries = rules.score(draft, state, definition)

        entries.first { it.participantID == participants[0].id }.computedValue shouldBe 40
        entries.first { it.participantID == participants[1].id }.computedValue shouldBe -20
        entries.first { it.participantID == participants[2].id }.computedValue shouldBe -10
    }

    @Test
    fun `endCheck stops at 60 divided by player count rounds, never before`() {
        val definition = definition()
        val rules = WizardRulesV1()
        val participants = participants(6)
        var state = state(participants)
        val engine = MatchEngine(now = { Instant.EPOCH })

        // 60 / 6 joueurs = 10 manches.
        for (index in 0 until 9) {
            val draft =
                RoundDraft(
                    index = index,
                    inputs =
                        participants.map {
                            ScoreInput(
                                participantID = it.id,
                                rawValue = 0,
                                detail = wizardBidScoreDetail(0),
                            )
                        },
                )
            state = engine.reduce(state, MatchEvent.RoundCommitted(draft), rules, definition)
            state.status shouldBe MatchStatus.InProgress
        }

        val lastDraft =
            RoundDraft(
                index = 9,
                inputs =
                    participants.map {
                        ScoreInput(
                            participantID = it.id,
                            rawValue = 0,
                            detail = wizardBidScoreDetail(0),
                        )
                    },
            )
        state = engine.reduce(state, MatchEvent.RoundCommitted(lastDraft), rules, definition)
        state.status shouldBe MatchStatus.Ended
    }
}
