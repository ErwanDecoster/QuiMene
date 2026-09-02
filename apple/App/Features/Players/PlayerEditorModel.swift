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
  /// Doc 14, phase 3 — pseudo connu au moment de la liaison (jamais mis à jour ensuite) : la
  /// seule trace locale de qui est de l'autre côté du lien, tant qu'aucun registre serveur
  /// n'existe (« Limites de confiance »). `nil` si cette fiche a seulement été *partagée*
  /// (généré un identifiant), jamais liée par scan.
  private(set) var linkedProfileName: String?
  private(set) var linkedProfileDate: Date?
  /// Doc 14, phase 4 — `true` si *cette* fiche est celle que cet appareil partage comme la
  /// sienne (par opposition à une fiche qui suit un ami, liée en scannant son code).
  private(set) var isMyOwnSharedProfile = false
  /// Doc 14, phase 4 — non-`nil` juste après un « Partager ce profil » refusé parce qu'une
  /// autre fiche est déjà celle de cet appareil.
  private(set) var shareConflictMessage: String?

  /// Lien QR à faire scanner par l'ami avec qui partager l'historique — recalculé depuis
  /// `sharedProfileID`/le pseudo/l'avatar courants, jamais stocké séparément. `nil` pour une
  /// fiche qui suit un ami (liée, pas partagée) : seule *la* fiche de cet appareil se partage,
  /// on ne rediffuse pas l'identité de quelqu'un d'autre en aval.
  var shareURL: URL? {
    guard isMyOwnSharedProfile, let sharedProfileID else { return nil }
    return ProfileShareLink.url(
      id: sharedProfileID,
      name: nickname,
      avatarKind: avatarKind,
      avatarValue: avatarKind == "photo" ? "" : emojiValue,
      paletteID: paletteID
    )
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

  /// Doc 14, phase 4 — génère l'identifiant partageable de cette fiche s'il n'existe pas
  /// encore, pour que « Partager ce profil » ait un QR à afficher immédiatement après le tap.
  /// Refuse si une *autre* fiche de cet appareil est déjà « la sienne » — une seule à la fois.
  func ensureSharedProfileID() {
    guard let player = editedPlayer else { return }
    shareConflictMessage = nil
    do {
      sharedProfileID = try repository.sharedProfileID(for: player)
      isMyOwnSharedProfile = true
    } catch PlayerRepositoryError.alreadySharingAnotherProfile(let nickname) {
      shareConflictMessage =
        "Tu partages déjà ta fiche « \(nickname) » comme la tienne. Une seule fiche par appareil peut l'être — délie-la d'abord si tu veux la remplacer par celle-ci."
    } catch PlayerRepositoryError.cannotShareALinkedProfile {
      // Doc 14, phase 4 — ne devrait plus arriver : la vue ne propose plus ce bouton sur
      // une fiche déjà liée à un ami. Filet de sécurité si jamais appelé autrement.
      shareConflictMessage =
        "Cette fiche suit déjà un ami : elle ne peut pas aussi être partagée comme la tienne."
    } catch {
      sharedProfileID = nil
    }
  }

  /// Doc 14, phase 3 « Limites de confiance » — si cet identifiant est déjà utilisé par une
  /// *autre* fiche locale, la vue doit avertir avant de continuer plutôt que lier en silence
  /// (rien ne distingue sinon un scan malencontreux d'une liaison volontaire).
  func conflictingPlayerName(forSharedProfileID id: UUID) -> String? {
    guard let player = editedPlayer,
      let existing = try? repository.player(withSharedProfileID: id),
      existing.id != player.id
    else { return nil }
    return existing.nickname
  }

  /// Doc 14 — lie cette fiche à l'identifiant scanné sur le téléphone d'un ami. `adoptNameAndAvatar`
  /// reprend le pseudo et l'avatar tels que connus au moment du scan (jamais une photo, qui ne
  /// transite pas par le QR — voir `ProfileShareLink`) plutôt que de garder ceux, potentiellement
  /// approximatifs, choisis à la création de cette fiche.
  func linkProfile(
    id: UUID, name: String, avatarKind: String, avatarValue: String, paletteID: String,
    adoptNameAndAvatar: Bool
  ) {
    guard let player = editedPlayer else { return }
    try? repository.linkSharedProfile(id, name: name, for: player)
    sharedProfileID = id
    linkedProfileName = name
    linkedProfileDate = Date()

    guard adoptNameAndAvatar else { return }
    // Posé avant les changements ci-dessous : l'avatar adopté ne doit pas se faire écraser par
    // la régénération automatique du pseudo.
    hasManualAvatarOverride = true
    nickname = name
    if avatarKind != "photo" {
      self.avatarKind = "emoji"
      emojiValue = avatarValue
      self.paletteID = paletteID
    }
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
