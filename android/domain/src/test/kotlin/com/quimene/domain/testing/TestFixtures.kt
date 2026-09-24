package com.quimene.domain.testing

import com.quimene.domain.model.MatchState
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreEntry
import com.quimene.domain.model.ValidationResult
import com.quimene.domain.model.VariantSelection
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
import com.quimene.domain.rules.TieBreakRule
import java.util.UUID

/** Fixtures partagées par les tests `:domain` — évite de reconstruire à la main une
 * `GameDefinition` minimale et un `GameRules` de test dans chaque fichier. */

internal fun testParticipant(
    name: String,
    seat: Int,
    team: String? = null,
): Participant = Participant(displayName = name, seatIndex = seat, teamID = team)

internal fun testDefinition(
    id: String = "test-game",
    direction: Direction = Direction.HighestWins,
    tieBreak: List<TieBreakRule> = listOf(TieBreakRule.Shared),
    end: End = End(conditions = emptyList()),
    statsProfiles: List<String> = listOf("standard"),
    engine: String = "test.v1",
): GameDefinition =
    GameDefinition(
        id = id,
        specVersion = 1,
        rulesVersion = 1,
        name = LocalizedText(fr = id),
        symbol = "circle",
        players = Players(min = 2, max = 8),
        scoring = Scoring(direction = direction, entry = ScoreEntrySpec(kind = EntryKind.Integer)),
        engine = engine,
        end = end,
        tieBreak = tieBreak,
        statsProfiles = statsProfiles,
    )

/** N'override rien — exerce les méthodes par défaut de [GameRules], comme `GenericSumRules`
 * (`:catalog`) côté production. Vit ici plutôt que de dépendre de `:catalog` (mauvais sens de
 * dépendance : `:catalog → :domain`, jamais l'inverse). */
internal class TestGameRules(
    override val engineID: String = "test.v1",
) : GameRules

internal fun testCatalog(
    definition: GameDefinition,
    rules: GameRules = TestGameRules(definition.engine),
): GameCatalog = GameCatalog(definitions = listOf(definition), engineTable = mapOf(definition.engine to { rules }))

internal fun testMatchState(
    definition: GameDefinition,
    participants: List<Participant>,
    matchID: UUID = UUID.randomUUID(),
): MatchState =
    MatchState(
        matchID = matchID,
        gameID = definition.id,
        rulesVersion = definition.rulesVersion,
        variants = VariantSelection(),
        participants = participants,
    )

internal fun roundDraft(
    index: Int,
    vararg scores: Pair<UUID, Int>,
): RoundDraft =
    RoundDraft(
        index = index,
        inputs =
            scores.map { (id, value) ->
                com.quimene.domain.model
                    .ScoreInput(id, value)
            },
    )

/** Ajoute une manche directement en `Round`/`ScoreEntry` (score = valeur brute, comme
 * `GameRules.score` par défaut), sans passer par `GameRules.score` — pratique pour construire un
 * historique de manches dans les tests de `StatsEngine`, indépendamment du moteur. */
internal fun MatchState.withRound(
    index: Int,
    vararg scores: Pair<UUID, Int>,
): MatchState {
    val entries = scores.map { (id, value) -> ScoreEntry(participantID = id, rawValue = value, computedValue = value) }
    val round =
        com.quimene.domain.model.Round(
            index = index,
            entries = entries,
            committedAt =
                java.time.Instant.EPOCH
                    .plusSeconds(index.toLong()),
        )
    return copy(rounds = (rounds.filterNot { it.index == index } + round).sortedBy { it.index })
}

internal fun validationResultKind(result: ValidationResult): String =
    when (result) {
        is ValidationResult.Valid -> "valid"
        is ValidationResult.Warning -> "warning"
        is ValidationResult.Invalid -> "invalid"
    }
