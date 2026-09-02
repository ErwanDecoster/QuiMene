package com.cacompte.app.features.matchsetup

import com.cacompte.app.RoomTestBase
import com.cacompte.app.testing.testGameDefinition
import com.cacompte.app.testing.testIntVariant
import com.cacompte.app.testing.testTeamGameDefinition
import com.cacompte.domain.model.VariantValue
import com.cacompte.domain.rules.LocalizedText
import com.cacompte.domain.rules.Players
import com.cacompte.domain.rules.Variant
import com.cacompte.domain.rules.VariantKind
import com.cacompte.store.MatchRepository
import com.cacompte.store.PlayerEntity
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Miroir de la logique observée dans `MatchSetupModel.swift` — sélection des joueurs, bornes
 * min/max, assignation d'équipe et variantes, sans re-tester ce que [MatchRepository] couvre déjà
 * lui-même (`:store` a ses propres tests). Aucun historique de partie n'existe dans ces tests
 * (base en mémoire fraîche à chaque cas) : le `init` du ViewModel présélectionne donc toujours
 * les joueurs disponibles par repli (`sortedByDescending { createdAt }.take(max)`), pas de
 * "dernière partie" — plusieurs tests en tiennent compte plutôt que de partir d'une sélection
 * vide.
 */
class MatchSetupViewModelTest : RoomTestBase() {
    private val repository by lazy { MatchRepository(db.matchDao(), db.participantDao(), db.playerDao()) }

    private fun player(
        nickname: String,
        createdAt: Instant = Instant.now(),
    ) = PlayerEntity(nickname = nickname, createdAt = createdAt)

    @Test
    fun `variant defaults are populated at init from the game definition`() {
        val definition = testGameDefinition(variants = listOf(testIntVariant(default = 42)))
        val vm = MatchSetupViewModel(definition, emptyList(), repository)

        vm.intVariant("target") shouldBe 42
        vm.setIntVariant("target", 99)
        vm.intVariant("target") shouldBe 99
    }

    @Test
    fun `canStart requires the player count to be within the game's bounds`() {
        val definition = testGameDefinition(players = Players(min = 2, max = 3))
        val alice = player("Alice")

        val tooFew = MatchSetupViewModel(definition, listOf(alice), repository)
        tooFew.canStart shouldBe false // repli : un seul joueur disponible, minimum 2

        val bob = player("Bob")
        val enough = MatchSetupViewModel(definition, listOf(alice, bob), repository)
        enough.canStart shouldBe true // repli : les 2 joueurs disponibles présélectionnés
    }

    @Test
    fun `toggle refuses to select more players than the game's maximum`() {
        val definition = testGameDefinition(players = Players(min = 2, max = 2))
        val alice = player("Alice")
        val bob = player("Bob")
        val carol = player("Carol")
        val vm = MatchSetupViewModel(definition, listOf(alice, bob, carol), repository)
        // Repart d'une sélection vide, quel que soit ce que le repli a présélectionné au init.
        vm.selectedPlayers.toList().forEach(vm::toggle)
        vm.selectedPlayers shouldBe emptyList()

        vm.toggle(alice)
        vm.toggle(bob)
        vm.toggle(carol) // déjà 2 sélectionnés (le maximum) : ignoré

        vm.selectedPlayers.map { it.nickname } shouldBe listOf("Alice", "Bob")
    }

    @Test
    fun `toggling a pre-selected player deselects them, toggling again reselects them`() {
        val definition = testGameDefinition()
        val alice = player("Alice")
        val vm = MatchSetupViewModel(definition, listOf(alice), repository)
        vm.isSelected(alice) shouldBe true // seul joueur disponible : présélectionné par repli

        vm.toggle(alice)
        vm.isSelected(alice) shouldBe false

        vm.toggle(alice)
        vm.isSelected(alice) shouldBe true
    }

    @Test
    fun `team games require every selected player assigned and every team full`() {
        val definition = testTeamGameDefinition() // équipes de 2, 4 joueurs
        val threePlayers = listOf(player("Alice"), player("Bob"), player("Carol"))
        val incomplete = MatchSetupViewModel(definition, threePlayers, repository)

        // Repli : les 3 joueurs disponibles présélectionnés, répartis par alternance (2 puis 1) —
        // une équipe reste incomplète.
        incomplete.teamsAreValid shouldBe false
        incomplete.canStart shouldBe false

        val fourPlayers = threePlayers + player("Dave")
        val complete = MatchSetupViewModel(definition, fourPlayers, repository)

        complete.teamsAreValid shouldBe true
        complete.canStart shouldBe true
    }

    @Test
    fun `assign moves a player to the chosen team explicitly`() {
        val definition = testTeamGameDefinition()
        val alice = player("Alice")
        val vm = MatchSetupViewModel(definition, listOf(alice), repository)

        vm.assign(alice, "B")

        vm.teamAssignment[alice.id] shouldBe "B"
    }

    @Test
    fun `start persists a match with the selected players as seeds`() =
        runTest {
            val definition = testGameDefinition()
            val alice = player("Alice")
            val bob = player("Bob")
            // Un participant lié à un joueur doit référencer une ligne réellement persistée
            // (clé étrangère `players.id`) — contrairement aux autres tests de ce fichier, qui
            // n'exercent jamais l'écriture en base et peuvent se contenter d'objets en mémoire.
            db.playerDao().insert(alice)
            db.playerDao().insert(bob)
            val vm = MatchSetupViewModel(definition, listOf(alice, bob), repository)

            var startedMatchID: UUID? = null
            vm.start { match -> startedMatchID = match.id }

            val persisted = requireNotNull(startedMatchID?.let { db.matchDao().get(it) })
            val participants = db.participantDao().forMatch(persisted.id).sortedBy { it.seatIndex }
            participants.map { it.nicknameSnapshot }.toSet() shouldBe setOf("Alice", "Bob")
        }

    @Test
    fun `boolVariant round-trips through setBoolVariant`() {
        val boolVariant =
            Variant(
                id = "strict",
                label = LocalizedText(fr = "Strict"),
                kind = VariantKind.Bool,
                defaultValue = VariantValue.BoolValue(false),
            )
        val definition = testGameDefinition(variants = listOf(boolVariant))
        val vm = MatchSetupViewModel(definition, emptyList(), repository)

        vm.boolVariant("strict") shouldBe false
        vm.setBoolVariant("strict", true)
        vm.boolVariant("strict") shouldBe true
    }
}
