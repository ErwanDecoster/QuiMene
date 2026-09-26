package com.quimene.app.features.players

import com.quimene.domain.model.MatchStatus
import com.quimene.store.MatchEntity
import com.quimene.store.ParticipantEntity
import com.quimene.store.PlayerEntity
import io.kotest.matchers.shouldBe
import org.junit.Test

/** Tri de la liste des joueurs — régression : il était alphabétique alors qu'il annonce « les
 * habitués d'abord », comme `PlayersListView.swift`. */
class PlayersOrderingTest {
    private val alice = PlayerEntity(nickname = "Alice", sortIndex = 0)
    private val bob = PlayerEntity(nickname = "Bob", sortIndex = 1)
    private val chloe = PlayerEntity(nickname = "Chloé", sortIndex = 2)

    private val ended = MatchEntity(gameID = "skyjo", status = MatchStatus.Ended)
    private val endedToo = MatchEntity(gameID = "skyjo", status = MatchStatus.Ended)
    private val inProgress = MatchEntity(gameID = "skyjo", status = MatchStatus.InProgress)

    private val participants =
        listOf(
            ParticipantEntity(playerId = chloe.id, matchId = ended.id),
            ParticipantEntity(playerId = chloe.id, matchId = endedToo.id),
            ParticipantEntity(playerId = bob.id, matchId = ended.id),
            // Une partie en cours ne compte pas : seule Chloé aurait sinon trois parties.
            ParticipantEntity(playerId = alice.id, matchId = inProgress.id),
            ParticipantEntity(playerId = alice.id, matchId = inProgress.id),
            ParticipantEntity(playerId = alice.id, matchId = inProgress.id),
        )

    @Test
    fun `most active players come first`() {
        val ordered =
            orderedPlayers(
                listOf(alice, bob, chloe),
                listOf(ended, endedToo, inProgress),
                participants,
            )
        ordered.map { it.nickname } shouldBe listOf("Chloé", "Bob", "Alice")
    }

    @Test
    fun `ties keep the order players were added`() {
        val ordered =
            orderedPlayers(listOf(chloe, bob, alice), emptyList(), emptyList())
        ordered.map { it.nickname } shouldBe listOf("Alice", "Bob", "Chloé")
    }
}
