package com.quimene.store

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

/** Miroir partiel de la logique de `PlayerRepository.swift` exercée à travers de vraies
 * requêtes Room (Robolectric, base SQLite en mémoire). */
class PlayerRepositoryTest : RoomTestBase() {
    private val repository by lazy { PlayerRepository(db.playerDao()) }

    @Test
    fun `create assigns the next free palette id and sort index`() =
        runTest {
            repository.create(nickname = "Alice", avatarKind = "emoji", avatarValue = "🦊")
            val bob = repository.create(nickname = "Bob", avatarKind = "emoji", avatarValue = "🐻")

            bob.paletteID shouldBe "2"
            bob.sortIndex shouldBe 1
        }

    @Test
    fun `archive and unarchive round-trip`() =
        runTest {
            val player = repository.create(nickname = "Alice", avatarKind = "emoji", avatarValue = "🦊")

            repository.archive(player)
            db.playerDao().get(player.id)?.isArchived shouldBe true

            val archived = db.playerDao().get(player.id)!!
            repository.unarchive(archived)
            db.playerDao().get(player.id)?.isArchived shouldBe false
        }

    @Test
    fun `reorder rewrites sort index to match the given order`() =
        runTest {
            val alice = repository.create(nickname = "Alice", avatarKind = "emoji", avatarValue = "🦊")
            val bob = repository.create(nickname = "Bob", avatarKind = "emoji", avatarValue = "🐻")

            repository.reorder(listOf(bob, alice))

            db.playerDao().get(bob.id)?.sortIndex shouldBe 0
            db.playerDao().get(alice.id)?.sortIndex shouldBe 1
        }

    @Test
    fun `sharing a second profile from the same device is refused`() =
        runTest {
            val alice = repository.create(nickname = "Alice", avatarKind = "emoji", avatarValue = "🦊")
            val bob = repository.create(nickname = "Bob", avatarKind = "emoji", avatarValue = "🐻")

            repository.sharedProfileID(alice)

            shouldThrow<PlayerRepositoryError.AlreadySharingAnotherProfile> {
                repository.sharedProfileID(bob)
            }
        }

    @Test
    fun `sharing the same profile twice returns the same id, never regenerated`() =
        runTest {
            val alice = repository.create(nickname = "Alice", avatarKind = "emoji", avatarValue = "🦊")

            val first = repository.sharedProfileID(alice)
            val reloaded = db.playerDao().get(alice.id)!!
            val second = repository.sharedProfileID(reloaded)

            first shouldBe second
        }

    @Test
    fun `linking a profile never marks it as this device's own`() =
        runTest {
            val alice = repository.create(nickname = "Alice", avatarKind = "emoji", avatarValue = "🦊")
            val friendID = java.util.UUID.randomUUID()

            repository.linkSharedProfile(friendID, "Bob", alice)

            val reloaded = db.playerDao().get(alice.id)!!
            reloaded.sharedProfileID shouldBe friendID
            reloaded.sharedProfileIsMine shouldBe false
            reloaded.sharedProfileLinkedName shouldBe "Bob"
        }
}
