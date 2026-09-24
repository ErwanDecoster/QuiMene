import DesignSystem
import Foundation
import Store
import SwiftData

enum PlayerEditorMode {
  case create
  /// Doc 16, phase A — crée la fiche de l'utilisateur de cet appareil : « mon profil ».
  case createProfile
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
  /// Doc 14, phase 3 — pseudo connu au moment de la liaison (jamais mis à jour ensuite) : la
  /// seule trace locale de qui est de l'autre côté du lien, tant qu'aucun registre serveur
  /// n'existe (« Limites de confiance »). `nil` si cette fiche a seulement été *partagée*
  /// (généré un identifiant), jamais liée par scan.
  private(set) var linkedProfileName: String?
  private(set) var linkedProfileDate: Date?
  /// Doc 14, phase 4 — `true` si *cette* fiche est celle que cet appareil partage comme la
  /// sienne (mon profil, doc 16) par opposition à une fiche qui suit un ami.
  private(set) var isMyOwnSharedProfile = false

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

  var isCreatingProfile: Bool {
    if case .createProfile = mode { return true }
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
    case .create, .createProfile:
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
      linkedProfileName = nil
      linkedProfileDate = nil
      isMyOwnSharedProfile = false
    case .edit(let player):
      nickname = player.nickname
      avatarKind = player.avatarKind == "photo" ? "photo" : "emoji"
      emojiValue =
        player.avatarKind == "emoji" ? player.avatarValue : (Avatar.curatedEmoji.first ?? "🙂")
      photoData = player.avatarKind == "photo" ? player.avatarPhoto : nil
      paletteID = player.paletteID
      sharedProfileID = player.sharedProfileID
      linkedProfileName = player.sharedProfileLinkedName
      linkedProfileDate = player.sharedProfileLinkedAt
      isMyOwnSharedProfile = player.sharedProfileIsMine

      // Un joueur existant dont l'emoji/la couleur ne correspond plus à ce que le hachage
      // du pseudo produirait aujourd'hui a forcément été personnalisé à la main.
      let generated = Avatar.generated(for: player.nickname)
      let generatedEmoji: String = if case .emoji(let value) = generated.kind { value } else { "" }
      hasManualAvatarOverride =
        player.avatarKind == "photo"
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
    case .createProfile:
      let player = try repository.create(
        nickname: nickname,
        avatarKind: avatarKind,
        avatarValue: value,
        avatarPhoto: photo,
        paletteID: paletteID
      )
      try repository.sharedProfileID(for: player)
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

  func unlinkProfile() {
    guard let player = editedPlayer else { return }
    try? repository.unlinkSharedProfile(for: player)
    sharedProfileID = nil
    linkedProfileName = nil
    linkedProfileDate = nil
    isMyOwnSharedProfile = false
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
