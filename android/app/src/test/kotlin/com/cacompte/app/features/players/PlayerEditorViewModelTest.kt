package com.cacompte.app.features.players

import com.cacompte.designsystem.components.Avatar
import com.cacompte.designsystem.components.AvatarKind
import com.cacompte.store.PlayerEntity
import com.cacompte.store.PlayerRepository
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Miroir de la logique observée dans `PlayerEditorModel.swift` (`didSet` de `nickname`/
 * `avatarKind`/`emojiValue`/`paletteID`) — la régénération automatique de l'avatar tant
 * qu'aucun choix manuel n'a eu lieu, et son verrouillage dès qu'un choix manuel survient. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerEditorViewModelTest {
    // Room n'est pas nécessaire ici (PlayerRepository n'est jamais appelé par ces tests), un
    // repository non construit suffit tant qu'aucune méthode dessus n'est invoquée.
    private fun viewModel(mode: PlayerEditorViewModel.Mode = PlayerEditorViewModel.Mode.Create) =
        PlayerEditorViewModel(mode, PlayerRepository(FakePlayerDao()))

    @Test
    fun `typing a nickname regenerates the avatar deterministically`() {
        val vm = viewModel()
        vm.updateNickname("Alice")

        val expected = Avatar.generated("Alice")
        val expectedEmoji = (expected.kind as AvatarKind.Emoji).character
        vm.emojiValue shouldBe expectedEmoji
        vm.paletteID shouldBe expected.palette.index.toString()
        vm.hasManualAvatarOverride shouldBe false
    }

    @Test
    fun `updateNickname capitalizes the first letter of each word without touching the rest`() {
        val vm = viewModel()

        vm.updateNickname("jean paul")
        vm.nickname shouldBe "Jean Paul"

        vm.updateNickname("mcDonald")
        vm.nickname shouldBe "McDonald"

        vm.updateNickname("  léa")
        vm.nickname shouldBe "  Léa"
    }

    @Test
    fun `manually picking an emoji stops further automatic regeneration`() {
        val vm = viewModel()
        vm.updateNickname("Alice")
        vm.selectEmoji("🎲")

        vm.hasManualAvatarOverride shouldBe true
        vm.updateNickname("Bob") // ne doit plus toucher à l'emoji choisi à la main.
        vm.emojiValue shouldBe "🎲"
    }

    @Test
    fun `resetting to the generated avatar unlocks automatic regeneration again`() {
        val vm = viewModel()
        vm.updateNickname("Alice")
        vm.selectEmoji("🎲")

        vm.resetToGeneratedAvatar()

        vm.hasManualAvatarOverride shouldBe false
        val expected = Avatar.generated("Alice")
        vm.emojiValue shouldBe (expected.kind as AvatarKind.Emoji).character

        vm.updateNickname("Bob")
        vm.emojiValue shouldBe (Avatar.generated("Bob").kind as AvatarKind.Emoji).character
    }

    @Test
    fun `editing an existing player with a custom avatar is detected as a manual override`() {
        val player =
            PlayerEntity(
                nickname = "Alice",
                avatarKind = "emoji",
                avatarValue = "🎲", // ne correspond pas au hachage de "Alice".
                paletteID = "7",
            )
        val vm = viewModel(PlayerEditorViewModel.Mode.Edit(player))

        vm.hasManualAvatarOverride shouldBe true
        vm.isEditing shouldBe true
    }

    @Test
    fun `canSave requires a non-empty nickname within 24 characters`() {
        val vm = viewModel()
        vm.canSave shouldBe false

        vm.updateNickname("Alice")
        vm.canSave shouldBe true

        vm.updateNickname("A".repeat(25))
        vm.canSave shouldBe false
    }
}
