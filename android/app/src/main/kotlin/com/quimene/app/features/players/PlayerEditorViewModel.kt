package com.quimene.app.features.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.app.profilesharing.ProfileShareLink
import com.quimene.designsystem.components.Avatar
import com.quimene.designsystem.components.AvatarKind
import com.quimene.store.PlayerEntity
import com.quimene.store.PlayerRepository
import com.quimene.store.PlayerRepositoryError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Miroir de `PlayerEditorModel.swift` — un des trois objets d'état "par flux métier" du projet
 * (ADR-0007).
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

    /** Doc 14 « Profils partagés » — `null` tant que cette fiche n'a jamais été partagée ni liée
     * à l'installation d'un ami. */
    var sharedProfileID by mutableStateOf<UUID?>(null)
        private set

    /** Pseudo connu au moment de la liaison (jamais mis à jour ensuite) — `null` si cette fiche
     * a seulement été *partagée* (généré un identifiant), jamais liée par scan. */
    var linkedProfileName by mutableStateOf<String?>(null)
        private set
    var linkedProfileDate by mutableStateOf<Instant?>(null)
        private set

    /** `true` si *cette* fiche est celle que cet appareil partage comme la sienne (par
     * opposition à une fiche qui suit un ami, liée en scannant son code). */
    var isMyOwnSharedProfile by mutableStateOf(false)
        private set

    /** Non-`null` juste après un « Partager ce profil » refusé parce qu'une autre fiche est déjà
     * celle de cet appareil. */
    var shareConflictMessage by mutableStateOf<String?>(null)
        private set

    /** Lien QR à faire scanner par l'ami avec qui partager l'historique — non-`null` seulement
     * si cette fiche est *la* fiche partagée de cet appareil. */
    val shareURL: String?
        get() {
            val id = sharedProfileID?.takeIf { isMyOwnSharedProfile } ?: return null
            return ProfileShareLink.url(
                id = id,
                name = nickname,
                avatarKind = avatarKind,
                avatarValue = if (avatarKind == "photo") "" else emojiValue,
                paletteID = paletteID,
            )
        }

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

                sharedProfileID = player.sharedProfileID
                isMyOwnSharedProfile = player.sharedProfileIsMine
                if (!player.sharedProfileIsMine) {
                    linkedProfileName = player.sharedProfileLinkedName
                    linkedProfileDate = player.sharedProfileLinkedAt
                }
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

    /** Doc 14, phase 4 — génère l'identifiant partageable de cette fiche s'il n'existe pas
     * encore, pour que « Partager ce profil » ait un QR à afficher immédiatement après le tap.
     * Refuse si une *autre* fiche de cet appareil est déjà « la sienne » — une seule à la fois. */
    fun ensureSharedProfileID() {
        val player = (mode as? Mode.Edit)?.player ?: return
        shareConflictMessage = null
        viewModelScope.launch {
            try {
                sharedProfileID = repository.sharedProfileID(player)
                isMyOwnSharedProfile = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: PlayerRepositoryError.AlreadySharingAnotherProfile) {
                shareConflictMessage =
                    "Tu partages déjà ta fiche « ${error.nickname} » comme la tienne. Une seule fiche par " +
                    "appareil peut l'être — délie-la d'abord si tu veux la remplacer par celle-ci."
            } catch (error: PlayerRepositoryError.CannotShareALinkedProfile) {
                shareConflictMessage =
                    "Cette fiche suit déjà un ami : elle ne peut pas aussi être partagée comme la tienne."
            } catch (error: Exception) {
                sharedProfileID = null
            }
        }
    }

    /** Doc 14, phase 3 « Limites de confiance » — si cet identifiant est déjà utilisé par une
     * *autre* fiche locale, l'écran de confirmation doit avertir avant de continuer plutôt que
     * lier en silence. */
    suspend fun conflictingPlayerName(id: UUID): String? {
        val player = (mode as? Mode.Edit)?.player ?: return null
        val existing = repository.player(id) ?: return null
        return if (existing.id != player.id) existing.nickname else null
    }

    /** Lie cette fiche à l'identifiant scanné sur le téléphone d'un ami. [adoptNameAndAvatar]
     * reprend le pseudo et l'avatar tels que connus au moment du scan (jamais une photo, qui ne
     * transite pas par le QR) plutôt que de garder ceux, potentiellement approximatifs, choisis
     * à la création de cette fiche. */
    fun linkProfile(
        id: UUID,
        name: String,
        scannedAvatarKind: String,
        scannedAvatarValue: String,
        scannedPaletteID: String,
        adoptNameAndAvatar: Boolean,
    ) {
        val player = (mode as? Mode.Edit)?.player ?: return
        viewModelScope.launch {
            repository.linkSharedProfile(id, name, player)
            sharedProfileID = id
            isMyOwnSharedProfile = false
            linkedProfileName = name
            linkedProfileDate = Instant.now()

            if (!adoptNameAndAvatar) return@launch
            // Posé avant les changements ci-dessous : l'avatar adopté ne doit pas se faire
            // écraser par la régénération automatique du pseudo.
            hasManualAvatarOverride = true
            nickname = name
            if (scannedAvatarKind != "photo") {
                avatarKind = "emoji"
                emojiValue = scannedAvatarValue
                paletteID = scannedPaletteID
            }
        }
    }

    fun unlinkProfile() {
        val player = (mode as? Mode.Edit)?.player ?: return
        viewModelScope.launch {
            repository.unlinkSharedProfile(player)
            sharedProfileID = null
            isMyOwnSharedProfile = false
            linkedProfileName = null
            linkedProfileDate = null
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
