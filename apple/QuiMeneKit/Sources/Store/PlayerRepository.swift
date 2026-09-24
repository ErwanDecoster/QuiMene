import Foundation
import SwiftData

/// Doc 14, phase 4 — une seule fiche par appareil peut être « la sienne » (celle qu'on partage) ;
/// tenter d'en partager une seconde est un vrai refus, pas un cas silencieusement ignoré.
public enum PlayerRepositoryError: Error, Sendable, Equatable {
  case alreadySharingAnotherProfile(nickname: String)
  /// Doc 14, phase 4 — remontée : une fiche déjà liée à l'identifiant d'un ami (elle *suit*
  /// quelqu'un) pouvait quand même être « partagée » — `sharedProfileID(for:)` retournait tout
  /// simplement l'identifiant déjà présent, celui de l'ami, sans jamais vérifier qu'il
  /// s'agissait bien du sien. Ça permettait de rediffuser l'identité d'un ami comme si c'était
  /// la sienne propre.
  case cannotShareALinkedProfile
}

/// Doc 02 : les écritures interactives (peu d'objets, latence nulle) restent sur le
/// `mainContext` — pas de `@ModelActor` dédié pour ce volume.
@MainActor
public struct PlayerRepository {
  private let context: ModelContext

  public init(context: ModelContext) {
    self.context = context
  }

  @discardableResult
  public func create(
    nickname: String,
    avatarKind: String,
    avatarValue: String,
    avatarPhoto: Data? = nil,
    paletteID: String? = nil
  ) throws -> PlayerRecord {
    let player = PlayerRecord(
      nickname: nickname,
      avatarKind: avatarKind,
      avatarValue: avatarValue,
      avatarPhoto: avatarPhoto,
      paletteID: paletteID ?? nextAvailablePaletteID(),
      sortIndex: nextSortIndex()
    )
    context.insert(player)
    try context.save()
    return player
  }

  public func save() throws {
    try context.save()
  }

  public func archive(_ player: PlayerRecord) throws {
    player.isArchived = true
    try context.save()
  }

  public func unarchive(_ player: PlayerRecord) throws {
    player.isArchived = false
    try context.save()
  }

  /// Suppression définitive — irréversible, contrairement à `archive`. La règle de suppression
  /// `.nullify` sur `ParticipantRecord.player` (doc 03) garde l'historique lisible : seul le
  /// lien vers la fiche disparaît, les *snapshots* (`nicknameSnapshot`…) restent inchangés.
  public func delete(_ player: PlayerRecord) throws {
    context.delete(player)
    try context.save()
  }

  /// Doc 14, phase 4 — génère l'identifiant partageable de cette fiche s'il n'existe pas
  /// encore, et la désigne comme *la* fiche de cet appareil (`sharedProfileIsMine`). Une seule
  /// fiche par appareil peut porter cette désignation : la partager en désignerait une seconde,
  /// ce que `SharedProfileSyncCoordinator` ne saurait pas départager (laquelle des deux
  /// représente vraiment l'utilisateur ?) — refusé explicitement plutôt que de laisser
  /// l'ambiguïté s'installer. Jamais régénéré une fois posé : un QR déjà distribué à un ami doit
  /// rester valable tant que la fiche n'est pas explicitement déliée.
  @discardableResult
  public func sharedProfileID(for player: PlayerRecord) throws -> UUID {
    if let existing = player.sharedProfileID {
      guard player.sharedProfileIsMine else {
        throw PlayerRepositoryError.cannotShareALinkedProfile
      }
      return existing
    }
    if let existingMine = try myOwnSharedPlayer(), existingMine.id != player.id {
      throw PlayerRepositoryError.alreadySharingAnotherProfile(nickname: existingMine.nickname)
    }
    let id = UUID()
    player.sharedProfileID = id
    player.sharedProfileIsMine = true
    try context.save()
    return id
  }

  /// Doc 16, phase A — deux profils « à moi » peuvent coexister après une synchronisation iCloud :
  /// sur un nouvel appareil, « Créer mon profil » peut être proposé avant que le profil existant
  /// n'arrive. Le plus ancien reste le profil (c'est à lui que les amis sont liés) ; le doublon
  /// est supprimé s'il n'a joué aucune partie, sinon il redevient une fiche ordinaire.
  public func resolveDuplicateOwnProfiles() throws {
    let descriptor = FetchDescriptor<PlayerRecord>(
      predicate: #Predicate { $0.sharedProfileIsMine },
      sortBy: [SortDescriptor(\.createdAt)])
    let mine = try context.fetch(descriptor)
    guard mine.count > 1 else { return }
    for duplicate in mine.dropFirst() {
      if duplicate.participations.isEmpty {
        context.delete(duplicate)
      } else {
        duplicate.sharedProfileID = nil
        duplicate.sharedProfileIsMine = false
      }
    }
    try context.save()
  }

  /// Doc 14, phase 4 — la fiche que cet appareil partage comme la sienne, s'il y en a une.
  public func myOwnSharedPlayer() throws -> PlayerRecord? {
    var descriptor = FetchDescriptor<PlayerRecord>(predicate: #Predicate { $0.sharedProfileIsMine })
    descriptor.fetchLimit = 1
    return try context.fetch(descriptor).first
  }

  /// Lie cette fiche à l'identifiant scanné depuis l'appareil d'un ami (doc 14) — écrase un
  /// éventuel identifiant précédent, cette fiche ne peut être liée qu'à une seule personne à
  /// la fois. `name` est celui du QR au moment du scan (doc 14, phase 3 « Limites de
  /// confiance ») : la seule trace locale de qui est de l'autre côté, jamais mise à jour
  /// ensuite. `sharedProfileIsMine` reste `false` : lier, contrairement à partager, ne désigne
  /// jamais cette fiche comme celle de l'utilisateur de cet appareil — c'est le suivi d'un ami.
  public func linkSharedProfile(_ id: UUID, name: String, for player: PlayerRecord) throws {
    player.sharedProfileID = id
    player.sharedProfileIsMine = false
    player.sharedProfileLinkedName = name
    player.sharedProfileLinkedAt = Date()
    try context.save()
  }

  public func unlinkSharedProfile(for player: PlayerRecord) throws {
    player.sharedProfileID = nil
    player.sharedProfileIsMine = false
    player.sharedProfileLinkedName = nil
    player.sharedProfileLinkedAt = nil
    try context.save()
  }

  /// Doc 14, phase 2 — toutes les fiches liées sur cet appareil, qu'elles représentent un ami
  /// (j'ai partagé/scanné pour lui) ou moi-même sur l'installation de quelqu'un d'autre.
  /// `SharedProfileSyncCoordinator` interroge la boîte aux lettres distante pour chacune.
  public func allSharedProfileIDs() throws -> [UUID] {
    let descriptor = FetchDescriptor<PlayerRecord>(
      predicate: #Predicate { $0.sharedProfileID != nil })
    return try context.fetch(descriptor).compactMap(\.sharedProfileID)
  }

  /// La première fiche locale liée à cet identifiant, si elle existe — sert à retrouver quel
  /// joueur *local* correspond à une entrée du classement reçu (doc 14, phase 2).
  public func player(withSharedProfileID id: UUID) throws -> PlayerRecord? {
    var descriptor = FetchDescriptor<PlayerRecord>(
      predicate: #Predicate { $0.sharedProfileID == id })
    descriptor.fetchLimit = 1
    return try context.fetch(descriptor).first
  }

  /// Ordre manuel de la liste des joueurs (`sortIndex`) — SwiftData ne préserve l'ordre
  /// d'aucune collection (doc 03, contrainte CloudKit n°5).
  public func reorder(_ players: [PlayerRecord]) throws {
    for (index, player) in players.enumerated() {
      player.sortIndex = index
    }
    try context.save()
  }

  /// Charte §1.5 : première couleur libre parmi les joueurs actifs à la création. Si les
  /// dix sont prises, reboucle plutôt que d'échouer — l'attribution reste modifiable ensuite.
  public func nextAvailablePaletteID() -> String {
    let active =
      (try? context.fetch(FetchDescriptor<PlayerRecord>(predicate: #Predicate { !$0.isArchived })))
      ?? []
    let used = Set(active.compactMap { Int($0.paletteID) })
    for index in 1...10 where !used.contains(index) {
      return String(index)
    }
    return String((active.count % 10) + 1)
  }

  private func nextSortIndex() -> Int {
    let all = (try? context.fetch(FetchDescriptor<PlayerRecord>())) ?? []
    return (all.map(\.sortIndex).max() ?? -1) + 1
  }
}
