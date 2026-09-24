import Catalog
import Domain
import Foundation
import Observation
import Store
import Sync

/// Doc 16, phase C — côté participant d'une session en ligne. Rejoindre, c'est résoudre le code
/// (`quimene_session_resolve`), rattraper le journal serveur, puis écouter le canal : plus de
/// poignée de main avec un hôte, qui n'a plus besoin d'être allumé. La reconnexion se réduit à un
/// rattrapage (`SessionLink.refresh`), fait tout seul au retour au premier plan et du réseau.
///
/// Vit aussi longtemps que l'app (même patron que `DeepLinkRouter.shared`) et retient la session
/// (`PersistedOnlineSession`) : à la réouverture après un arrêt complet du processus, la partie
/// suivie reprend sans redemander le code.
@MainActor
@Observable
final class MatchConnectionCoordinator {
  static let shared = MatchConnectionCoordinator()

  private(set) var sharedModel: SharedMatchModel?

  private let catalog = GameCatalog.embedded
  private let backend = SupabaseSessionBackend()

  private init() {
    if let persisted = PersistedOnlineSession.load(.participant) {
      Task { [weak self] in
        _ = try? await self?.connect(
          code: persisted.pairingCode, deviceName: persisted.deviceName)
      }
    }
  }

  /// Point d'entrée unique pour rejoindre une partie (`JoinTabView`). Renvoie le rôle accordé :
  /// contributeur, ou observateur si le créateur n'autorise pas les contributeurs.
  @discardableResult
  func join(code: String, deviceName: String, requestedRole: Role, appVersion: String) async throws
    -> Role
  {
    try await connect(code: code, deviceName: deviceName)
  }

  /// « Réessayer maintenant » : un rattrapage immédiat, sans attendre le réseau ou le premier plan.
  @discardableResult
  func reconnectNow() async -> Bool {
    guard let link = sharedModel?.link else { return false }
    await link.refresh()
    return link.isReachable
  }

  private func connect(code: String, deviceName: String) async throws -> Role {
    guard let info = try await backend.resolve(pairingCode: code) else {
      throw OnlineSessionError.sessionNotFound
    }
    await sharedModel?.stop()

    let session = OnlineSession(
      sessionID: info.sessionID, pairingCode: code, deviceID: DeviceIdentity.current,
      backend: backend)
    let link = SessionLink(
      session: session, pairingCode: code,
      me: SessionPresence(
        deviceID: DeviceIdentity.current, deviceName: deviceName, isOwner: false))
    let role: Role = info.allowsContributors ? .contributor : .observer
    let model = SharedMatchModel(link: link, role: role, catalog: catalog)
    sharedModel = model
    PersistedOnlineSession(
      sessionID: info.sessionID, pairingCode: code, role: .participant, deviceName: deviceName
    ).save()
    await link.start()
    return role
  }

  /// Départ volontaire : l'utilisateur quitte réellement la partie (« Quitter la partie »).
  func stop() async {
    await sharedModel?.stop()
    sharedModel = nil
    PersistedOnlineSession.clear(.participant)
  }
}
