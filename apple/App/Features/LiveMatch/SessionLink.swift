import DesignSystem
import Domain
import Foundation
import Network
import Store
import SwiftData
import Sync
import UIKit

/// Doc 16, phase C — lien d'un appareil avec une session en ligne, commun au créateur
/// (`LiveShareCoordinator`) et aux participants (`MatchConnectionCoordinator`) : le journal
/// (`OnlineSession`), le canal temps réel (`SessionChannel`), le rattrapage à chaque notification
/// ou retour au premier plan, l'état du réseau, et l'ajout d'un événement.
///
/// Plus d'hôte qui arbitre : chaque appareil valide sa saisie contre l'état à jour, et le serveur
/// garantit l'ordre (numéro attendu). Le créateur n'a donc plus besoin d'être allumé.
@MainActor
@Observable
final class SessionLink {
  enum SubmitResult: Equatable {
    case accepted(SessionEventRecord)
    /// Un autre appareil a ajouté un événement entre-temps ; le journal local est déjà à jour.
    /// Pas de nouvel essai automatique : deux personnes qui saisissent la même manche physique
    /// créeraient un doublon silencieux. On montre l'état à jour, la saisie reste en place.
    case overtaken(byDeviceName: String?)
    case offline
    case closed
  }

  let session: OnlineSession
  let pairingCode: String
  private(set) var presence: [SessionPresence] = []
  /// Faux quand le réseau manque ou qu'un appel vient d'échouer : la saisie est alors bloquée
  /// (doc 16, « saisie hors ligne »), l'écran rattrape dès le retour du réseau.
  private(set) var isReachable = true
  private(set) var isClosed = false
  /// Nouveaux événements lisibles, dans l'ordre — publiés à chaque rattrapage.
  var onNewRecords: (([SessionEventRecord]) -> Void)?
  /// Doc 16, phase D — qui est qui, recalculé à chaque nouvel événement d'identité.
  private(set) var identities: SessionIdentities
  /// Nouveaux événements d'identité, dans l'ordre (après `identities` mis à jour).
  var onNewIdentities: (([SessionIdentityRecord]) -> Void)?
  /// L'appareil du créateur : seul son registre (`roster`) fait foi.
  let ownerDeviceID: String
  private var knownIdentityCount = 0

  private let channel: SessionChannel
  private var tasks: [Task<Void, Never>] = []
  private var foregroundObserver: (any NSObjectProtocol)?
  private let pathMonitor = NWPathMonitor()

  init(session: OnlineSession, pairingCode: String, me: SessionPresence, ownerDeviceID: String) {
    self.session = session
    self.pairingCode = pairingCode
    self.ownerDeviceID = ownerDeviceID
    identities = SessionIdentities(records: [], ownerDeviceID: ownerDeviceID)
    channel = SessionChannel(sessionID: session.sessionID, me: me)
  }

  var sessionID: UUID { session.sessionID }

  /// Rattrapage initial, puis écoute : notifications du canal, présence, réseau, premier plan.
  /// Le canal est un confort (rapidité, liste des présents) : s'il ne se connecte pas, le
  /// rattrapage au premier plan et après chaque saisie suffit à rester juste.
  func start() async {
    await refresh()
    let channel = channel
    try? await channel.connect()
    tasks = [
      Task { [weak self] in
        for await _ in channel.notifications {
          await self?.refresh()
        }
      },
      Task { [weak self] in
        for await present in channel.presence {
          self?.presence = present
        }
      },
    ]
    pathMonitor.pathUpdateHandler = { [weak self] path in
      Task { @MainActor [weak self] in
        guard let self else { return }
        let wasReachable = self.isReachable
        self.isReachable = path.status == .satisfied
        if self.isReachable, !wasReachable { await self.refresh() }
      }
    }
    pathMonitor.start(queue: .global(qos: .utility))
    foregroundObserver = NotificationCenter.default.addObserver(
      forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main
    ) { [weak self] _ in
      Task { @MainActor [weak self] in await self?.refresh() }
    }
  }

  func stop() async {
    for task in tasks { task.cancel() }
    tasks = []
    pathMonitor.cancel()
    if let foregroundObserver { NotificationCenter.default.removeObserver(foregroundObserver) }
    foregroundObserver = nil
    await channel.disconnect()
  }

  /// Rattrape le journal et publie ce qui est nouveau.
  func refresh() async {
    do {
      let fresh = try await session.sync()
      isReachable = true
      if !fresh.isEmpty { onNewRecords?(fresh) }
      await publishIdentityChanges()
    } catch {
      isReachable = false
    }
  }

  /// Ajoute un événement à la partie `matchID`. Le journal local est rattrapé dans tous les cas
  /// (les nouveaux événements sont publiés via `onNewRecords`, le sien compris).
  func submit(
    _ event: MatchEvent, matchID: UUID, eventID: UUID = UUID(), occurredAt: Date = Date()
  ) async -> SubmitResult {
    guard !isClosed else { return .closed }
    let before = await session.lastSeq
    do {
      let record = try await session.append(
        event, matchID: matchID, eventID: eventID, occurredAt: occurredAt)
      isReachable = true
      let fresh = await session.records.filter { $0.seq > before }
      onNewRecords?(fresh)
      await publishIdentityChanges()
      return .accepted(record)
    } catch OnlineSessionError.staleSequence {
      isReachable = true
      let fresh = await session.records.filter { $0.seq > before }
      if !fresh.isEmpty { onNewRecords?(fresh) }
      await publishIdentityChanges()
      let author = fresh.last { $0.event.deviceID != session.deviceID }?.event.deviceID
      return .overtaken(byDeviceName: author.flatMap(deviceName(for:)))
    } catch OnlineSessionError.sessionClosed {
      isClosed = true
      return .closed
    } catch {
      isReachable = false
      return .offline
    }
  }

  /// Doc 16, phase D — publie un événement d'identité dans la partie `matchID`. Contrairement à
  /// une manche, nouvel essai automatique si un autre appareil a devancé : une revendication ne
  /// dépend pas de l'état de la partie (les règles de `SessionIdentities` départagent ensuite).
  @discardableResult
  func submitIdentity(_ event: SessionIdentityEvent, matchID: UUID) async -> Bool {
    guard !isClosed else { return false }
    for _ in 0..<3 {
      do {
        try await session.appendIdentity(event, matchID: matchID)
        isReachable = true
        await publishIdentityChanges()
        return true
      } catch OnlineSessionError.staleSequence {
        await publishIdentityChanges()
        continue
      } catch OnlineSessionError.sessionClosed {
        isClosed = true
        return false
      } catch {
        isReachable = false
        return false
      }
    }
    return false
  }

  private func publishIdentityChanges() async {
    let all = await session.identities
    guard all.count != knownIdentityCount else { return }
    let fresh = Array(all.dropFirst(knownIdentityCount))
    knownIdentityCount = all.count
    identities = SessionIdentities(records: all, ownerDeviceID: ownerDeviceID)
    onNewIdentities?(fresh)
  }

  func deviceName(for deviceID: String) -> String? {
    presence.first { $0.deviceID == deviceID }?.deviceName
  }

  /// Marque la session comme fermée côté appareil (le créateur vient de l'arrêter).
  func markClosed() {
    isClosed = true
  }
}

/// Doc 16, phase C — ce qu'un appareil retient d'une session pour la reprendre après un
/// redémarrage de l'app, créateur compris (auparavant, une app tuée en arrière-plan côté hôte
/// perdait le partage).
struct PersistedOnlineSession: Codable, Equatable {
  enum Role: String, Codable {
    case owner, participant
  }

  let sessionID: UUID
  let pairingCode: String
  let role: Role
  /// Nom affiché aux autres appareils (présence), retenu pour la reprise au lancement.
  let deviceName: String
  /// Doc 16, phase D — côté participant : mon profil tel que publié, et « Je regarde seulement ».
  var profile: ProfileCard?
  var isSpectator: Bool?

  private static func key(_ role: Role) -> String { "onlineSession.\(role.rawValue)" }

  static func load(_ role: Role) -> PersistedOnlineSession? {
    guard let data = UserDefaults.standard.data(forKey: key(role)) else { return nil }
    return try? JSONDecoder().decode(PersistedOnlineSession.self, from: data)
  }

  func save() {
    guard let data = try? JSONEncoder().encode(self) else { return }
    UserDefaults.standard.set(data, forKey: Self.key(role))
  }

  static func clear(_ role: Role) {
    UserDefaults.standard.removeObject(forKey: key(role))
  }
}

extension OnlineSession {
  /// Même générateur que l'ancien partage (`SessionCrypto`) : six chiffres.
  static func newPairingCode() -> String {
    String(format: "%06d", Int.random(in: 0...999_999))
  }
}

/// Doc 16 — le nom montré aux autres appareils d'une session (appareils connectés, « X vient de
/// valider une manche ») : le pseudo du profil, obligatoire depuis la phase A ; le nom de
/// l'appareil seulement en repli.
@MainActor
enum SessionDisplayName {
  static func current(in context: ModelContext) -> String {
    let nickname = (try? PlayerRepository(context: context).myOwnSharedPlayer())?.nickname ?? ""
    let trimmed = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? UIDevice.current.name : trimmed
  }
}

extension ProfileCard {
  /// Doc 16, phase D — la carte de mon profil, publiée dans une session (« Qui es-tu ? », registre
  /// du créateur). `nil` sans profil. Un avatar photo ne voyage pas : repli sur l'emoji dérivé
  /// du pseudo, comme pour le QR de profil.
  @MainActor
  static func mine(in context: ModelContext) -> ProfileCard? {
    let repository = PlayerRepository(context: context)
    guard let me = try? repository.myOwnSharedPlayer(),
      let id = try? repository.sharedProfileID(for: me)
    else { return nil }
    return ProfileCard(player: me, id: id)
  }

  init(player: PlayerRecord, id: UUID) {
    let emoji: String
    if player.avatarKind == "emoji", !player.avatarValue.isEmpty {
      emoji = player.avatarValue
    } else if case .emoji(let value) = Avatar.generated(for: player.nickname).kind {
      emoji = value
    } else {
      emoji = ""
    }
    self.init(
      id: id, name: player.nickname, avatarKind: "emoji", avatarValue: emoji,
      paletteID: player.paletteID)
  }

  /// L'avatar à montrer pour cette carte.
  var avatar: Avatar {
    let emoji: String
    if avatarKind == "emoji", !avatarValue.isEmpty {
      emoji = avatarValue
    } else if case .emoji(let value) = Avatar.generated(for: name).kind {
      emoji = value
    } else {
      emoji = Avatar.curatedEmoji.first ?? "🙂"
    }
    return Avatar(kind: .emoji(emoji), palette: PlayerPalette(index: Int(paletteID) ?? 1))
  }
}

/// Doc 16, phase D — la liaison « Qui es-tu ? » vaut dans les deux sens : côté participant, le
/// créateur devient un ami lié (doc 14), comme après un scan de son QR de profil.
@MainActor
enum FriendLinking {
  /// Une fiche déjà liée à ce profil : rien à faire. Sinon, une fiche active non liée qui porte
  /// exactement ce pseudo (et une seule) est liée ; à défaut, une fiche est créée.
  static func ensureFriend(_ card: ProfileCard, in context: ModelContext) {
    let repository = PlayerRepository(context: context)
    guard (try? repository.player(withSharedProfileID: card.id)) == nil else { return }
    let name = card.name.trimmingCharacters(in: .whitespacesAndNewlines)
    let unlinked =
      (try? context.fetch(
        FetchDescriptor<PlayerRecord>(
          predicate: #Predicate { !$0.isArchived && $0.sharedProfileID == nil }))) ?? []
    let sameName = unlinked.filter {
      $0.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        .localizedCaseInsensitiveCompare(name) == .orderedSame
    }
    let fiche: PlayerRecord?
    if sameName.count == 1 {
      fiche = sameName.first
    } else {
      guard case .emoji(let emoji) = card.avatar.kind else { return }
      fiche = try? repository.create(
        nickname: name, avatarKind: "emoji", avatarValue: emoji, paletteID: card.paletteID)
    }
    guard let fiche else { return }
    try? repository.linkSharedProfile(card.id, name: card.name, for: fiche)
  }
}
