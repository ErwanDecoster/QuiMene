package com.quimene.catalog.golden

import com.quimene.catalog.GameCatalogEmbedded
import com.quimene.catalog.games.tarotHandScoreDetail
import com.quimene.catalog.games.wizardBidScoreDetail
import com.quimene.catalog.games.yamsCategoryScoreDetail
import com.quimene.domain.engine.MatchEngine
import com.quimene.domain.engine.MatchEvent
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.model.ModifierID
import com.quimene.domain.model.Participant
import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreDetail
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.stats.StatsEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * Miroir de `GoldenFileTests.swift` — un test paramétré : ajouter un golden au dossier ajoute un
 * cas, sans toucher au code de test. Vérifié après CHAQUE manche, pas seulement à la fin.
 *
 * Différence assumée avec Swift Testing : JUnit5 s'arrête à la première assertion en échec dans
 * un cas donné (`#expect` de Swift Testing continue et rapporte tous les échecs) — un golden en
 * échec pointe donc la première divergence trouvée, pas la liste complète. Suffisant pour la
 * détection de régression (le but de ce jalon), la richesse diagnostique en moins.
 */
class GoldenFileTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    fun replay(golden: GoldenFile) {
        val catalog = GameCatalogEmbedded.embedded
        val definition = catalog.definition(golden.gameId, golden.rulesVersion)
        val rules = catalog.rules(golden.gameId, golden.rulesVersion)

        val idByToken = golden.participants.associate { it.id to UUID.randomUUID() }
        val tokenByID = idByToken.entries.associate { (token, id) -> id to token }

        val participants =
            golden.participants.map {
                Participant(
                    id = idByToken.getValue(it.id),
                    displayName = it.name,
                    seatIndex = it.seatIndex,
                    teamID = it.teamID,
                )
            }

        var state =
            MatchState.create(
                matchID = UUID.randomUUID(),
                gameID = golden.gameId,
                rulesVersion = golden.rulesVersion,
                variants = golden.variants,
                participants = participants,
            )

        // Horloge figée : seul (lamport, deviceID) fait foi dans le vrai moteur, mais ici on
        // appelle reduce directement — une horloge fixe rend committedAt reproductible sans
        // intervenir dans la logique testée.
        val engine = MatchEngine(now = { Instant.EPOCH })

        for (roundInput in golden.rounds) {
            val draft =
                RoundDraft(
                    index = roundInput.index,
                    inputs =
                        roundInput.inputs.map { input ->
                            ScoreInput(
                                participantID = idByToken.getValue(input.participant),
                                rawValue = input.rawValue,
                                detail = decodeGoldenDetail(input.detail),
                                modifiers = input.modifiers.map { ModifierID(it) }.toSet(),
                            )
                        },
                )
            state = engine.reduce(state, MatchEvent.RoundCommitted(draft), rules, definition)

            val expectedRound = golden.expected.roundResults.firstOrNull { it.index == roundInput.index } ?: continue
            val actualRound = state.rounds.firstOrNull { it.index == roundInput.index }
            assertTrue(
                actualRound != null,
                "${golden.goldenId} : manche ${roundInput.index} absente de l'état après reduce",
            )
            checkNotNull(actualRound)

            for (entry in actualRound.entries) {
                val token = tokenByID.getValue(entry.participantID)
                assertEquals(
                    expectedRound.computed[token],
                    entry.computedValue,
                    "${golden.goldenId} manche ${roundInput.index} — score calculé de $token",
                )
                val wasDoubled = entry.computedValue != entry.rawValue
                assertEquals(
                    expectedRound.doubled.contains(token),
                    wasDoubled,
                    "${golden.goldenId} manche ${roundInput.index} — doublement de $token",
                )
            }

            val cumulative = state.totals()
            for ((token, expectedTotal) in expectedRound.cumulative) {
                assertEquals(
                    expectedTotal,
                    cumulative[idByToken.getValue(token)],
                    "${golden.goldenId} manche ${roundInput.index} — cumul de $token",
                )
            }

            assertEquals(
                expectedRound.status,
                state.status.wireValue,
                "${golden.goldenId} manche ${roundInput.index} — statut",
            )
        }

        assertEquals(golden.expected.final.status, state.status.wireValue, "${golden.goldenId} — statut final")

        val standings = rules.standings(state, definition)
        val standingsByToken = standings.associateBy { tokenByID.getValue(it.participantID) }

        for (expected in golden.expected.final.standings) {
            val actual = standingsByToken[expected.participant]
            assertTrue(actual != null, "${golden.goldenId} : classement manquant pour ${expected.participant}")
            checkNotNull(actual)
            assertEquals(expected.rank, actual.rank, "${golden.goldenId} — rang de ${expected.participant}")
            assertEquals(expected.score, actual.score, "${golden.goldenId} — score de ${expected.participant}")

            val expectedShared = (expected.sharedWith ?: emptyList()).toSet()
            val actualShared = actual.sharedWith.map { tokenByID.getValue(it) }.toSet()
            assertEquals(
                expectedShared,
                actualShared,
                "${golden.goldenId} — partage de rang de ${expected.participant}",
            )
        }

        val candidates = StatsEngine().candidates(state, definition)
        val candidatesByID = candidates.associateBy { it.id.rawValue }

        for (expected in golden.expected.insights) {
            val actual = candidatesByID[expected.id]
            assertTrue(actual != null, "${golden.goldenId} : insight manquant : ${expected.id}")
            checkNotNull(actual)
            when (val value = actual.value) {
                is com.quimene.domain.stats.Insight.Value.Single -> {
                    expected.participant?.let {
                        assertEquals(
                            it,
                            value.participantID?.let { id -> tokenByID.getValue(id) },
                            "${golden.goldenId} — participant de l'insight ${expected.id}",
                        )
                    }
                    expected.value?.let {
                        assertTrue(
                            abs(value.value - it) < 0.01,
                            "${golden.goldenId} — valeur de l'insight ${expected.id} : ${value.value} ≠ $it",
                        )
                    }
                    expected.round?.let {
                        assertEquals(it, value.round, "${golden.goldenId} — manche de l'insight ${expected.id}")
                    }
                }

                is com.quimene.domain.stats.Insight.Value.PerParticipant -> {
                    expected.values?.forEach { (token, expectedValue) ->
                        assertEquals(
                            expectedValue,
                            value.values[idByToken.getValue(token)],
                            "${golden.goldenId} — insight ${expected.id} pour $token : attendu $expectedValue",
                        )
                    }
                }
            }
        }
    }

    /** Traduit le payload générique `Map<String, String>` des golden files vers un [ScoreDetail]
     * concret (Yams/Tarot/Wizard) — même dispatch que la closure inline de `GoldenFileTests.swift`. */
    private fun decodeGoldenDetail(detail: Map<String, String>?): ScoreDetail? {
        if (detail == null) return null
        detail["categoryID"]?.let { return yamsCategoryScoreDetail(it) }
        detail["bid"]?.toIntOrNull()?.let { return wizardBidScoreDetail(it) }
        val contract = detail["contract"]?.toIntOrNull()
        val bouts = detail["bouts"]?.toIntOrNull()
        val poignee = detail["poignee"]?.toIntOrNull()
        if (contract != null && bouts != null && poignee != null) {
            return tarotHandScoreDetail(contract, bouts, poignee)
        }
        return null
    }

    companion object {
        @JvmStatic
        fun goldenFiles(): List<GoldenFile> = GoldenFile.all
    }
}

/** Valeur JSON du statut (miroir du raw value `String` de `MatchStatus.swift`) — les golden files
 * comparent contre cette forme, pas le nom d'entrée Kotlin (`InProgress` vs `"inProgress"`). */
private val MatchStatus.wireValue: String
    get() =
        when (this) {
            MatchStatus.InProgress -> "inProgress"
            MatchStatus.FinalRound -> "finalRound"
            MatchStatus.Ended -> "ended"
            MatchStatus.Abandoned -> "abandoned"
        }
