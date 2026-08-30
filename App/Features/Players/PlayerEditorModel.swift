import DesignSystem
import Foundation
import Store
import SwiftData

enum PlayerEditorMode {
    case create
    case edit(PlayerRecord)
}

/// Doc 02 : un des rares `@Observable @MainActor` portés « par flux métier », pas par écran.
/// Doc 07/08 : l'avatar par défaut (emoji + couleur) est dérivé du pseudo par hachage stable —
/// mais reste modifiable à la main, avec une réinitialisation vers la version générée.
@MainActor
@Observable
final class PlayerEditorModel {
    var nickname: String {
        didSet {
            guard oldValue != nickname else { return }
            if !hasManualAvatarOverride {
                regenerateFromNickname()
            }
        }
    }

    var avatarKind: String {
        didSet {
            guard oldValue != avatarKind else { return }
            if avatarKind == "emoji", !hasManualAvatarOverride {
                regenerateFromNickname()
            }
        }
    }

    var emojiValue: String {
        didSet {
            guard oldValue != emojiValue, !isRegeneratingProgrammatically else { return }
            hasManualAvatarOverride = true
        }
    }

    var photoData: Data?

    /// Doc 14 « Profils partagés » — `nil` tant que cette fiche n'a jamais été partagée ni liée
    /// à l'installation d'un ami.
    private(set) var sharedProfileID: UUID?

    /// Lien QR à faire scanner par l'ami que cette fiche représente — recalculé depuis
    /// `sharedProfileID` et le pseudo courant, jamais stocké séparément.
    var shareURL: URL? {
        guard let sharedProfileID else { return nil }
        return ProfileShareLink.url(id: sharedProfileID, name: nickname)
    }

    var paletteID: String {
        didSet {
            guard oldValue != paletteID, !isRegeneratingProgrammatically else { return }
            hasManualAvatarOverride = true
        }
    }

    /// `true` dès que l'emoji ou la couleur a été choisi à la main — l'avatar ne se
    /// régénère alors plus tout seul quand le pseudo change, jusqu'à réinitialisation explicite.
    private(set) var hasManualAvatarOverride: Bool
    private var isRegeneratingProgrammatically = false

    private let mode: PlayerEditorMode
    private let repository: PlayerRepository

    var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    /// Un joueur archivé n'a plus de sens à ré-archiver depuis son éditeur — la seule action
    /// destructive qui reste pertinente ici est la suppression définitive.
    var isArchivedPlayer: Bool {
        if case .edit(let player) = mode { return player.isArchived }
        return false
    }

    var canSave: Bool {
        (1...24).contains(nickname.count)
    }

    init(mode: PlayerEditorMode, context: ModelContext) {
        self.mode = mode
        self.repository = PlayerRepository(context: context)

        switch mode {
        case .create:
            nickname = ""
            avatarKind = "emoji"
            let generated = Avatar.generated(for: "")
            if case .emoji(let value) = generated.kind {
                emojiValue = value
            } else {
                emojiValue = Avatar.curatedEmoji.first ?? "🙂"
            }
            photoData = nil
            paletteID = String(generated.palette.index)
            hasManualAvatarOverride = false
            sharedProfileID = nil
        case .edit(let player):
            nickname = player.nickname
            avatarKind = player.avatarKind == "photo" ? "photo" : "emoji"
            emojiValue = player.avatarKind == "emoji" ? player.avatarValue : (Avatar.curatedEmoji.first ?? "🙂")
            photoData = player.avatarKind == "photo" ? player.avatarPhoto : nil
            paletteID = player.paletteID
            sharedProfileID = player.sharedProfileID

            // Un joueur existant dont l'emoji/la couleur ne correspond plus à ce que le hachage
            // du pseudo produirait aujourd'hui a forcément été personnalisé à la main.
            let generated = Avatar.generated(for: player.nickname)
            let generatedEmoji: String = if case .emoji(let value) = generated.kind { value } else { "" }
            hasManualAvatarOverride = player.avatarKind == "photo"
                || player.avatarValue != generatedEmoji
                || player.paletteID != String(generated.palette.index)
        }
    }

    /// Charte §1.5 : « même pseudo → même emoji et même couleur, quel que soit l'appareil. »
    /// N'agit que tant qu'aucun choix manuel n'a eu lieu — voir `resetToGeneratedAvatar()` pour
    /// revenir en arrière après coup.
    private func regenerateFromNickname() {
        let generated = Avatar.generated(for: nickname)
        guard case .emoji(let value) = generated.kind else { return }
        isRegeneratingProgrammatically = true
        emojiValue = value
        paletteID = String(generated.palette.index)
        isRegeneratingProgrammatically = false
    }

    /// Revient à l'emoji et la couleur dérivés du pseudo actuel, en écrasant tout choix manuel.
    func resetToGeneratedAvatar() {
        hasManualAvatarOverride = false
        avatarKind = "emoji"
        regenerateFromNickname()
    }

    func selectEmoji(_ emoji: String) {
        emojiValue = emoji
    }

    func selectPalette(_ id: String) {
        paletteID = id
    }

    func setPhotoData(_ data: Data?) {
        photoData = data
    }

    func save() throws {
        let value = avatarKind == "photo" ? "" : emojiValue
        let photo = avatarKind == "photo" ? photoData : nil

        switch mode {
        case .create:
            try repository.create(
                nickname: nickname,
                avatarKind: avatarKind,
                avatarValue: value,
                avatarPhoto: photo,
                paletteID: paletteID
            )
        case .edit(let player):
            player.nickname = nickname
            player.avatarKind = avatarKind
            player.avatarValue = value
            player.avatarPhoto = photo
            player.paletteID = paletteID
            try repository.save()
        }
    }

    private var editedPlayer: PlayerRecord? {
        if case .edit(let player) = mode { return player }
        return nil
    }

    /// Doc 14 — génère l'identifiant partageable de cette fiche s'il n'existe pas encore, pour
    /// que « Partager ce profil » ait un QR à afficher immédiatement après le tap.
    func ensureSharedProfileID() {
        guard let player = editedPlayer else { return }
        sharedProfileID = try? repository.sharedProfileID(for: player)
    }

    /// Doc 14 — lie cette fiche à l'identifiant scanné sur le téléphone d'un ami.
    func linkProfile(id: UUID) {
        guard let player = editedPlayer else { return }
        try? repository.linkSharedProfile(id, for: player)
        sharedProfileID = id
    }

    func unlinkProfile() {
        guard let player = editedPlayer else { return }
        try? repository.unlinkSharedProfile(for: player)
        sharedProfileID = nil
    }

    func archive() throws {
        if case .edit(let player) = mode {
            try repository.archive(player)
        }
    }

    func delete() throws {
        if case .edit(let player) = mode {
            try repository.delete(player)
        }
    }
}
