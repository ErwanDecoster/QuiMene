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

  private let channel: SessionChannel
  private var tasks: [Task<Void, Never>] = []
  private var foregroundObserver: (any NSObjectProtocol)?
  private let pathMonitor = NWPathMonitor()

  init(session: OnlineSession, pairingCode: String, me: SessionPresence) {
    self.session = session
    self.pairingCode = pairingCode
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
      return .accepted(record)
    } catch OnlineSessionError.staleSequence {
      isReachable = true
      let fresh = await session.records.filter { $0.seq > before }
      if !fresh.isEmpty { onNewRecords?(fresh) }
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
