package com.quimene.store

import com.quimene.domain.model.RoundDraft
import com.quimene.domain.model.ScoreInput
import com.quimene.domain.model.VariantSelection
import com.quimene.store.testing.testCatalog
import io.kotest.matchers.shouldBe
import org.junit.Test

/** Couverture représentative (pas exhaustive) de `LeaderboardRepository.swift`/
 * `ProfileRepository.swift` — un scénario par comportement documenté, pas chaque branche. */
class LeaderboardAndProfileRepositoryTest : RoomTestBase() {
    private val catalog = testCatalog()
    private val gameID = catalog.allGames.first().id

    private val playerRepository by lazy { PlayerRepository(db.playerDao()) }
    private val matchRepository by lazy {
        MatchRepository(db.matchDao(), db.participantDao(), db.playerDao())
    }
    private val leaderboardRepository by lazy {
        LeaderboardRepository(
            db.matchDao(),
            db.participantDao(),
            db.playerDao(),
        )
    }
    private val profileRepository by lazy { ProfileRepository(db.matchDao(), db.participantDao()) }

    private suspend fun playFinishedMatch(
        alice: PlayerEntity,
        bob: PlayerEntity,
        aliceScore: Int,
        bobScore: Int,
    ) {
        val match =
            matchRepository.createMatch(
                gameID = gameID,
                rulesVersion = 1,
                variants = VariantSelection(),
                seeds =
                    listOf(
                        MatchRepository.ParticipantSeed(
                            alice,
                            alice.nickname,
                            alice.avatarKind,
                            alice.avatarValue,
                            alice.paletteID,
                        ),
                        MatchRepository.ParticipantSeed(
                            bob,
                            bob.nickname,
                            bob.avatarKind,
                            bob.avatarValue,
                            bob.paletteID,
                        ),
                    ),
            )
        val state = matchRepository.loadState(match, catalog)
        val (aliceID, bobID) = state.participants.map { it.id }
        val inputs = listOf(ScoreInput(aliceID, aliceScore), ScoreInput(bobID, bobScore))
        matchRepository.commitRound(RoundDraft(index = 0, inputs = inputs), match, catalog)
        val reloaded = db.matchDao().get(match.id)!!
        matchRepository.endMatchManually(reloaded, catalog)
    }

    @Test
    fun `leaderboard ranks the player with more wins first`() =
        runTest {
            val alice = playerRepository.create("Alice", "emoji", "🦊")
            val bob = playerRepository.create("Bob", "emoji", "🐻")

            // highestWins par défaut du fixture (testGameDefinition) : le score le plus haut gagne.
            playFinishedMatch(alice, bob, aliceScore = 20, bobScore = 5) // Alice gagne
            playFinishedMatch(alice, bob, aliceScore = 5, bobScore = 20) // Bob gagne

            val board = leaderboardRepository.leaderboard(gameID)

            board.size shouldBe 2
            board.first().winRate shouldBe 0.5
            board.first().played shouldBe 2
        }

    @Test
    fun `profile stats are empty for a player who never finished a match`() =
        runTest {
            val alice = playerRepository.create("Alice", "emoji", "🦊")

            val stats = profileRepository.stats(alice, catalog)

            stats shouldBe ProfileStats.empty
        }

    @Test
    fun `profile stats count a win and report a perfect win rate`() =
        runTest {
            val alice = playerRepository.create("Alice", "emoji", "🦊")
            val bob = playerRepository.create("Bob", "emoji", "🐻")

            playFinishedMatch(alice, bob, aliceScore = 20, bobScore = 5)

            val stats = profileRepository.stats(alice, catalog)

            stats.played shouldBe 1
            stats.wins shouldBe 1
            stats.winRate shouldBe 1.0
            stats.byGame.single().gameID shouldBe gameID
        }
}
