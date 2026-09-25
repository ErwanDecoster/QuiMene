import Catalog
import Domain
import Foundation
import Observation
import Store
import SwiftData
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
  /// Doc 16, phase E — pour enregistrer dans mon historique les parties terminées de la session.
  private var modelContext: ModelContext?

  /// Au lancement (`QuiMeneApp`) : donne accès aux fiches, puis rattrape les parties terminées
  /// pendant que l'app était fermée.
  func configure(context: ModelContext) {
    modelContext = context
    Task { await sharedModel?.keepConcludedMatches() }
  }

  private init() {
    if let persisted = PersistedOnlineSession.load(.participant) {
      Task { [weak self] in
        _ = try? await self?.connect(
          code: persisted.pairingCode, deviceName: persisted.deviceName,
          profile: persisted.profile, isSpectator: persisted.isSpectator ?? false)
      }
    }
  }

  /// Point d'entrée unique pour rejoindre une partie (`JoinTabView`). Renvoie le rôle accordé :
  /// contributeur, ou observateur si le créateur n'autorise pas les contributeurs.
  @discardableResult
  /// `profile` : mon profil, publié quand je dis qui je suis (« Qui es-tu ? », doc 16 phase D).
  func join(
    code: String, deviceName: String, profile: ProfileCard?, requestedRole: Role,
    appVersion: String
  ) async throws -> Role {
    try await connect(code: code, deviceName: deviceName, profile: profile, isSpectator: false)
  }

  /// « Réessayer maintenant » : un rattrapage immédiat, sans attendre le réseau ou le premier plan.
  @discardableResult
  func reconnectNow() async -> Bool {
    guard let link = sharedModel?.link else { return false }
    await link.refresh()
    return link.isReachable
  }

  private func connect(
    code: String, deviceName: String, profile: ProfileCard?, isSpectator: Bool
  ) async throws -> Role {
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
        deviceID: DeviceIdentity.current, deviceName: deviceName, isOwner: false),
      ownerDeviceID: info.ownerDeviceID)
    let role: Role = info.allowsContributors ? .contributor : .observer
    var persisted = PersistedOnlineSession(
      sessionID: info.sessionID, pairingCode: code, role: .participant, deviceName: deviceName,
      profile: profile, isSpectator: isSpectator)
    persisted.save()
    let model = SharedMatchModel(
      link: link, role: role, catalog: catalog, me: profile, isSpectator: isSpectator
    ) { spectator in
      persisted.isSpectator = spectator
      persisted.save()
    }
    model.keepMatch = { [weak self, weak model] matchID, events in
      guard let self, let model else { return false }
      return self.keep(matchID: matchID, events: events, in: model)
    }
    sharedModel = model
    await link.start()
    return role
  }

  /// Doc 16, phase E — une partie terminée de la session où j'ai une place : enregistrée dans mon
  /// historique, complète, comme chez le créateur. Ma place est reliée à ma fiche, celles de mes
  /// amis à leurs fiches ; les autres gardent leur pseudo et un avatar dérivé. Déjà enregistrée
  /// (reçue par ailleurs) : mise à jour si le journal de la session est plus long.
  private func keep(matchID: UUID, events: [StampedEvent], in model: SharedMatchModel) -> Bool {
    guard let modelContext, let me = model.me, let first = events.first,
      case .matchCreated(_, _, _, let participants) = first.event,
      let mySeat = model.link.identities.seat(of: me.id),
      participants.contains(where: { SharedMatchModel.seat(of: $0) == mySeat })
    else { return false }
    let repository = MatchRepository(context: modelContext)
    if let existing = try? repository.match(withID: matchID) {
      if let local = try? repository.currentLog(for: existing), events.count > local.count {
        _ = try? repository.replaceLog(events, in: existing, catalog: catalog)
      }
      return true
    }
    let players = PlayerRepository(context: modelContext)
    let myFiche = try? players.myOwnSharedPlayer()
    let created = try? repository.createMirroredMatch(
      id: matchID, events: events, catalog: catalog
    ) { participant in
      let seat = SharedMatchModel.seat(of: participant)
      let fiche: PlayerRecord? =
        if seat == mySeat {
          myFiche
        } else if let occupant = model.link.identities.occupant(of: seat) {
          try? players.player(withSharedProfileID: occupant)
        } else {
          nil
        }
      guard let fiche else { return LiveShareCoordinator.generatedSeed(for: participant.displayName) }
      return MatchRepository.ParticipantSeed(
        player: fiche, nickname: participant.displayName, avatarKind: fiche.avatarKind,
        avatarValue: fiche.avatarValue, paletteID: fiche.paletteID, teamID: participant.teamID)
    }
    if created != nil {
      // Déposée tout de suite chez mes amis liés qui y ont joué (doc 16, phase E).
      Task { await SharedProfileSyncCoordinator.shared.sync(context: modelContext) }
    }
    return created != nil
  }

  /// Départ volontaire : l'utilisateur quitte réellement la partie (« Quitter la partie »).
  func stop() async {
    await sharedModel?.stop()
    sharedModel = nil
    PersistedOnlineSession.clear(.participant)
  }
}
