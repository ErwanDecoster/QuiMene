package com.cacompte.store

import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.model.RoundDraft
import com.cacompte.domain.model.ScoreInput
import com.cacompte.domain.model.VariantSelection
import com.cacompte.store.testing.testCatalog
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Miroir de la logique de `MatchRepository.swift` — `eventLogData` reste la source de vérité
 * (ADR-0005) : ces tests vérifient que **relire une partie depuis la base et rejouer son
 * journal** produit le même état que l'état en mémoire juste après l'écriture, pas seulement
 * que l'écriture elle-même "a l'air" de fonctionner.
 */
class MatchRepositoryTest : RoomTestBase() {
    private val catalog = testCatalog()
    private val repository by lazy { MatchRepository(db.matchDao(), db.participantDao(), db.playerDao()) }

    @Test
    fun `creating a match persists a matchCreated event that replays to the same participants`() =
        runTest {
            val match =
                repository.createMatch(
                    gameID = catalog.allGames.first().id,
                    rulesVersion = 1,
                    variants = VariantSelection(),
                    seeds =
                        listOf(
                            MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1"),
                            MatchRepository.ParticipantSeed(null, "Bob", "emoji", "🐻", "2"),
                        ),
                )

            val state = repository.loadState(match, catalog)
            state.participants.map { it.displayName } shouldBe listOf("Alice", "Bob")
            state.status shouldBe MatchStatus.InProgress
        }

    @Test
    fun `resuming after a restart replays the same cumulative totals from the persisted log`() =
        runTest {
            val match =
                repository.createMatch(
                    gameID = catalog.allGames.first().id,
                    rulesVersion = 1,
                    variants = VariantSelection(),
                    seeds =
                        listOf(
                            MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1"),
                            MatchRepository.ParticipantSeed(null, "Bob", "emoji", "🐻", "2"),
                        ),
                )
            val firstState = repository.loadState(match, catalog)
            val (aliceID, bobID) = firstState.participants.map { it.id }

            repository.commitRound(
                RoundDraft(index = 0, inputs = listOf(ScoreInput(aliceID, 10), ScoreInput(bobID, 5))),
                match,
                catalog,
            )

            // Relit depuis la base — pas l'objet `match` d'origine — pour vérifier que c'est bien le
            // journal persisté, pas un état en mémoire, qui porte la vérité.
            val reloaded = db.matchDao().get(match.id)!!
            val resumedState = repository.loadState(reloaded, catalog)

            resumedState.totals()[aliceID] shouldBe 10
            resumedState.totals()[bobID] shouldBe 5
        }

    @Test
    fun `undoing the last round removes it from the replayed state`() =
        runTest {
            val match =
                repository.createMatch(
                    gameID = catalog.allGames.first().id,
                    rulesVersion = 1,
                    variants = VariantSelection(),
                    seeds = listOf(MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1")),
                )
            val alice =
                repository
                    .loadState(match, catalog)
                    .participants
                    .single()
                    .id

            val afterRound =
                repository.commitRound(
                    RoundDraft(index = 0, inputs = listOf(ScoreInput(alice, 10))),
                    match,
                    catalog,
                )
            afterRound.totals()[alice] shouldBe 10

            val reloadedAfterRound = db.matchDao().get(match.id)!!
            val afterUndo = repository.undoLastRound(reloadedAfterRound, catalog)
            afterUndo.totals()[alice] shouldBe 0
            afterUndo.rounds.isEmpty() shouldBe true
        }

    @Test
    fun `abandoning a match still records final standings on the participants`() =
        runTest {
            val match =
                repository.createMatch(
                    gameID = catalog.allGames.first().id,
                    rulesVersion = 1,
                    variants = VariantSelection(),
                    seeds =
                        listOf(
                            MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1"),
                            MatchRepository.ParticipantSeed(null, "Bob", "emoji", "🐻", "2"),
                        ),
                )
            val alice =
                repository
                    .loadState(match, catalog)
                    .participants
                    .first()
                    .id
            val committed =
                repository.commitRound(
                    RoundDraft(index = 0, inputs = listOf(ScoreInput(alice, 10))),
                    match,
                    catalog,
                )
            val reloaded = db.matchDao().get(match.id)!!

            val finalState = repository.abandonMatch(reloaded, catalog)

            finalState.status shouldBe MatchStatus.Abandoned
            val persisted = db.matchDao().get(match.id)!!
            persisted.status shouldBe MatchStatus.Abandoned
            // Abandonner compte comme une fin au même titre qu'une victoire (Swift : `state.status
            // == .ended || .abandoned` posent toutes deux `endedAt`) — la partie apparaît dans
            // l'historique avec une date de fin, rien n'est perdu.
            persisted.endedAt.shouldNotBeNull()

            val participants = db.participantDao().forMatch(match.id)
            participants.any { it.finalRank != null } shouldBe true
            committed.status shouldBe MatchStatus.InProgress // sanity: round alone didn't end the match
        }

    @Test
    fun `finishedMatches excludes archived matches and matches still in progress`() =
        runTest {
            val inProgress =
                repository.createMatch(
                    gameID = catalog.allGames.first().id,
                    rulesVersion = 1,
                    variants = VariantSelection(),
                    seeds = listOf(MatchRepository.ParticipantSeed(null, "Alice", "emoji", "🦊", "1")),
                )
            val toArchive =
                repository.createMatch(
                    gameID = catalog.allGames.first().id,
                    rulesVersion = 1,
                    variants = VariantSelection(),
                    seeds = listOf(MatchRepository.ParticipantSeed(null, "Bob", "emoji", "🐻", "2")),
                )
            repository.abandonMatch(toArchive, catalog)
            val reloadedToArchive = db.matchDao().get(toArchive.id)!!
            repository.archive(reloadedToArchive)

            val finished = repository.finishedMatches()

            finished.none { it.id == inProgress.id } shouldBe true
            finished.none { it.id == toArchive.id } shouldBe true // archivé, donc exclu malgré le statut terminé
        }
}
