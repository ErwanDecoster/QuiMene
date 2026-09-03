package com.cacompte.app.features.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.designsystem.components.Avatar
import com.cacompte.designsystem.components.AvatarKind
import com.cacompte.store.PlayerEntity
import com.cacompte.store.PlayerRepository
import kotlinx.coroutines.launch

/**
 * Miroir de `PlayerEditorModel.swift` — un des trois objets d'état "par flux métier" du projet
 * (ADR-0007). **Le partage de profil (doc 14) n'est pas porté** : `ensureSharedProfileID`,
 * `linkProfile`, `unlinkProfile`, `shareURL` dépendent de `:sync` (étape F), hors périmètre de
 * cette étape — seule l'édition locale d'une fiche joueur est couverte.
 */
class PlayerEditorViewModel(
    private val mode: Mode,
    private val repository: PlayerRepository,
) : ViewModel() {
    sealed interface Mode {
        data object Create : Mode

        data class Edit(
            val player: PlayerEntity,
        ) : Mode
    }

    var nickname by mutableStateOf("")
        private set
    var avatarKind by mutableStateOf("emoji")
        private set
    var emojiValue by mutableStateOf(Avatar.curatedEmoji.first())
        private set
    var photoData by mutableStateOf<ByteArray?>(null)
        private set
    var paletteID by mutableStateOf("1")
        private set

    /** `true` dès que l'emoji ou la couleur a été choisi à la main — l'avatar ne se régénère
     * alors plus tout seul quand le pseudo change, jusqu'à [resetToGeneratedAvatar]. */
    var hasManualAvatarOverride by mutableStateOf(false)
        private set

    private var isRegeneratingProgrammatically = false

    val isEditing: Boolean get() = mode is Mode.Edit
    val isArchivedPlayer: Boolean get() = (mode as? Mode.Edit)?.player?.isArchived ?: false
    val canSave: Boolean get() = nickname.length in 1..24

    init {
        when (mode) {
            is Mode.Create -> applyGenerated(Avatar.generated(""))
            is Mode.Edit -> {
                val player = mode.player
                nickname = player.nickname
                avatarKind = if (player.avatarKind == "photo") "photo" else "emoji"
                emojiValue = if (player.avatarKind == "emoji") player.avatarValue else Avatar.curatedEmoji.first()
                photoData = if (player.avatarKind == "photo") player.avatarPhoto else null
                paletteID = player.paletteID

                // Un joueur existant dont l'emoji/la couleur ne correspond plus à ce que le
                // hachage du pseudo produirait aujourd'hui a forcément été personnalisé à la main.
                val generated = Avatar.generated(player.nickname)
                val generatedEmoji = (generated.kind as? AvatarKind.Emoji)?.character.orEmpty()
                hasManualAvatarOverride = player.avatarKind == "photo" ||
                    player.avatarValue != generatedEmoji ||
                    player.paletteID != generated.palette.index.toString()
            }
        }
    }

    fun updateNickname(value: String) {
        val capitalized = capitalizeEachWord(value)
        if (nickname == capitalized) return
        nickname = capitalized
        if (!hasManualAvatarOverride) regenerateFromNickname()
    }

    fun updateAvatarKind(value: String) {
        if (avatarKind == value) return
        avatarKind = value
        if (value == "emoji" && !hasManualAvatarOverride) regenerateFromNickname()
    }

    fun selectEmoji(emoji: String) {
        if (emojiValue == emoji) return
        emojiValue = emoji
        if (!isRegeneratingProgrammatically) hasManualAvatarOverride = true
    }

    fun selectPalette(id: String) {
        if (paletteID == id) return
        paletteID = id
        if (!isRegeneratingProgrammatically) hasManualAvatarOverride = true
    }

    fun updatePhotoData(data: ByteArray?) {
        photoData = data
    }

    /** Charte §1.5 : « même pseudo → même emoji et même couleur, quel que soit l'appareil. »
     * N'agit que tant qu'aucun choix manuel n'a eu lieu. */
    private fun regenerateFromNickname() {
        applyGenerated(Avatar.generated(nickname))
    }

    private fun applyGenerated(avatar: Avatar) {
        val emoji = (avatar.kind as? AvatarKind.Emoji)?.character ?: return
        isRegeneratingProgrammatically = true
        emojiValue = emoji
        paletteID = avatar.palette.index.toString()
        isRegeneratingProgrammatically = false
    }

    /** Revient à l'emoji et la couleur dérivés du pseudo actuel, en écrasant tout choix manuel. */
    fun resetToGeneratedAvatar() {
        hasManualAvatarOverride = false
        avatarKind = "emoji"
        regenerateFromNickname()
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val value = if (avatarKind == "photo") "" else emojiValue
            val photo = if (avatarKind == "photo") photoData else null
            when (mode) {
                is Mode.Create -> repository.create(nickname, avatarKind, value, photo, paletteID)
                is Mode.Edit ->
                    repository.save(
                        mode.player.copy(
                            nickname = nickname,
                            avatarKind = avatarKind,
                            avatarValue = value,
                            avatarPhoto = photo,
                            paletteID = paletteID,
                        ),
                    )
            }
            onSaved()
        }
    }

    fun archive(onDone: () -> Unit) {
        val player = (mode as? Mode.Edit)?.player ?: return
        viewModelScope.launch {
            repository.archive(player)
            onDone()
        }
    }

    fun unarchive(onDone: () -> Unit) {
        val player = (mode as? Mode.Edit)?.player ?: return
        viewModelScope.launch {
            repository.unarchive(player)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val player = (mode as? Mode.Edit)?.player ?: return
        viewModelScope.launch {
            repository.delete(player)
            onDone()
        }
    }

    /** Doc utilisateur — un pseudo commence toujours par une majuscule à chaque mot, imposé (pas
     * juste suggéré par le clavier) : ne force que la première lettre de chaque mot, laisse le
     * reste de la saisie intact (« McDonald » reste « McDonald », pas « Mcdonald »). */
    private fun capitalizeEachWord(value: String): String {
        val builder = StringBuilder(value.length)
        var capitalizeNext = true
        for (char in value) {
            builder.append(if (capitalizeNext) char.uppercaseChar() else char)
            capitalizeNext = char.isWhitespace()
        }
        return builder.toString()
    }
}
