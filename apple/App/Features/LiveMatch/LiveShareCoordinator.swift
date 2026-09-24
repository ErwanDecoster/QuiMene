import Catalog
import DesignSystem
import Domain
import Foundation
import Observation
import Store
import SwiftData
import Sync

/// Doc 16, phase C — côté créateur d'une session en ligne. La session vit sur le serveur
/// (`SessionLink`/`OnlineSession`) : le journal de chaque partie partagée y fait foi, la partie
/// locale (`MatchRecord`) en est le miroir. Le créateur n'arbitre plus rien : chaque appareil,
/// lui compris, ajoute ses manches au journal serveur, qui garantit l'ordre.
///
/// Survit à l'écran de partie (se recrée à chaque nouvelle partie) et au redémarrage de l'app
/// (`PersistedOnlineSession`, reprise par `resumeIfNeeded`) : seul « Arrêter le partage » termine
/// la session (doc 16 : créateur uniquement).
@MainActor
@Observable
final class LiveShareCoordinator {
  static let shared = LiveShareCoordinator()

  private let catalog = GameCatalog.embedded
  private let backend = SupabaseSessionBackend()

  private(set) var link: SessionLink?
  private var match: MatchRecord?
  private var repository: MatchRepository?

  /// La partie actuellement diffusée — `nil` tant qu'aucune session n'est active.
  private(set) var attachedMatchID: UUID?
  private(set) var allowsContributors = true
  var isSharing: Bool { link != nil }
  var pairingCode: String? { link?.pairingCode }
  var sessionID: UUID? { link?.sessionID }
  /// Faux hors ligne : la saisie d'une partie partagée est alors bloquée (doc 16).
  var isReachable: Bool { link?.isReachable ?? true }
  /// Les autres appareils présents dans la session.
  var connectedPeers: [SessionPresence] {
    (link?.presence ?? []).filter { $0.deviceID != DeviceIdentity.current }
  }

  var attachedMatchIsConcluded: Bool {
    guard let match else { return true }
    return match.status == .ended || match.status == .abandoned
  }

  var attachedGameName: String? {
    guard let match else { return nil }
    return (try? catalog.definition(for: match.gameID, version: match.rulesVersion))?.name.localized
      ?? match.gameID
  }

  /// Republié à chaque événement ajouté par un autre appareil, pour que le `LiveMatchModel`
  /// affiché se recharge (`refreshFromRemote`) et l'annonce.
  private(set) var remoteEventToken = UUID()
  private(set) var remoteEventMatchID: UUID?
  private(set) var remoteEventDeviceID: String?
  private(set) var remoteEventIsRoundCommit = false

  /// Doc 16, phase C — un autre appareil vient de lancer la partie suivante : sa copie locale
  /// existe déjà (`newMatchID`). L'écran de la partie précédente (`previousMatchID`) y bascule.
  private(set) var remoteStartedMatch: (newMatchID: UUID, previousMatchID: UUID?)?
  private(set) var remoteStartedToken = UUID()

  private init() {}

  // MARK: - Démarrer, rattacher, reprendre

  /// Ouvre une session (nouveau code, retiré si déjà pris) et y publie cette partie ; si une
  /// session est déjà ouverte, y rattache simplement la partie.
  func startSharing(
    match: MatchRecord, context: ModelContext, deviceName: String, allowsContributors: Bool
  ) async throws {
    guard link == nil else {
      await attach(match: match, context: context)
      return
    }

    let sessionID = UUID()
    var code = OnlineSession.newPairingCode()
    for attempt in 1...3 {
      do {
        try await backend.open(
          sessionID: sessionID, pairingCode: code, ownerDeviceID: DeviceIdentity.current,
          allowsContributors: allowsContributors)
        break
      } catch OnlineSessionError.pairingCodeTaken where attempt < 3 {
        code = OnlineSession.newPairingCode()
      }
    }

    self.allowsContributors = allowsContributors
    PersistedOnlineSession(
      sessionID: sessionID, pairingCode: code, role: .owner, deviceName: deviceName
    ).save()
    await connect(sessionID: sessionID, code: code, deviceName: deviceName)
    await attach(match: match, context: context)
  }

  /// Rattache une partie à la session ouverte : si le serveur ne la connaît pas encore, publie
  /// son journal local ; puis la copie locale devient le miroir du journal serveur.
  func attach(match: MatchRecord, context: ModelContext) async {
    guard let link, attachedMatchID != match.id else { return }
    let repository = MatchRepository(context: context)
    self.repository = repository
    self.match = match
    attachedMatchID = match.id

    if await link.session.events(forMatch: match.id).isEmpty,
      let localLog = try? repository.currentLog(for: match)
    {
      for stamped in localLog {
        // Même identifiant qu'en local : celui du `matchCreated` est l'identifiant de la partie
        // (`MatchEngine`), partagé par tous les appareils.
        let result = await link.submit(
          stamped.event, matchID: match.id, eventID: stamped.id, occurredAt: stamped.occurredAt)
        guard case .accepted = result else { break }
      }
    }
    await mirrorAttachedMatch()
  }

  /// Au lancement : reprend la session que ce créateur avait ouverte (l'app a pu être tuée en
  /// arrière-plan), et sa partie courante.
  func resumeIfNeeded(context: ModelContext) async {
    guard link == nil, let persisted = PersistedOnlineSession.load(.owner) else { return }
    await connect(
      sessionID: persisted.sessionID, code: persisted.pairingCode,
      deviceName: persisted.deviceName)
    guard let link else { return }
    let repository = MatchRepository(context: context)
    self.repository = repository
    if let currentID = await link.session.currentMatchID(),
      let match = try? repository.match(withID: currentID)
    {
      self.match = match
      attachedMatchID = match.id
      await mirrorAttachedMatch()
    }
  }

  private func connect(sessionID: UUID, code: String, deviceName: String) async {
    let session = OnlineSession(
      sessionID: sessionID, pairingCode: code, deviceID: DeviceIdentity.current, backend: backend)
    let link = SessionLink(
      session: session, pairingCode: code,
      me: SessionPresence(deviceID: DeviceIdentity.current, deviceName: deviceName, isOwner: true))
    link.onNewRecords = { [weak self] records in
      Task { @MainActor [weak self] in await self?.handle(records) }
    }
    self.link = link
    await link.start()
  }

  // MARK: - Saisie

  /// Ajoute un événement à une partie de la session ; en cas de succès, la copie locale est déjà
  /// à jour quand cette fonction rend la main.
  func submit(_ event: MatchEvent, matchID: UUID) async -> SessionLink.SubmitResult {
    guard let link else { return .closed }
    let result = await link.submit(event, matchID: matchID)
    await mirrorAttachedMatch()
    return result
  }

  /// « Partie suivante » du créateur : nouvelle partie locale avec les mêmes joueurs (mêmes
  /// fiches, mêmes avatars), publiée dans la session. Mêmes variantes si c'est le même jeu.
  func startNextMatch(
    definition: GameDefinition, after previous: MatchRecord, context: ModelContext
  ) async -> MatchRecord? {
    guard link != nil else { return nil }
    let repository = MatchRepository(context: context)
    let seeds = previous.participants.sorted { $0.seatIndex < $1.seatIndex }.map {
      MatchRepository.ParticipantSeed(
        player: $0.player, nickname: $0.nicknameSnapshot, avatarKind: $0.avatarKindSnapshot,
        avatarValue: $0.avatarValueSnapshot, paletteID: $0.paletteIDSnapshot)
    }
    let variants =
      definition.id == previous.gameID
      ? (try? JSONDecoder().decode(VariantSelection.self, from: previous.variantsData))
        ?? VariantSelection()
      : VariantSelection()
    guard
      let match = try? repository.createMatch(
        gameID: definition.id, rulesVersion: definition.rulesVersion, variants: variants,
        seeds: seeds, deviceID: DeviceIdentity.current)
    else { return nil }
    await attach(match: match, context: context)
    return match
  }

  func setAllowsContributors(_ allowed: Bool) async {
    guard let link else { return }
    allowsContributors = allowed
    try? await backend.open(
      sessionID: link.sessionID, pairingCode: link.pairingCode,
      ownerDeviceID: DeviceIdentity.current, allowsContributors: allowed)
  }

  /// Seul point d'arrêt d'une session (doc 16 : créateur uniquement). Les participants le
  /// constatent à leur prochaine saisie ; le journal reste lisible 24 h pour qu'ils rattrapent.
  func stopSharing() async {
    if let link {
      try? await backend.close(sessionID: link.sessionID, ownerDeviceID: DeviceIdentity.current)
      await link.stop()
    }
    PersistedOnlineSession.clear(.owner)
    link = nil
    repository = nil
    match = nil
    attachedMatchID = nil
    allowsContributors = true
  }

  // MARK: - Miroir

  private func handle(_ records: [SessionEventRecord]) async {
    await adoptMatchesStartedElsewhere(records)
    await mirrorAttachedMatch()
    let remote = records.filter {
      $0.matchID == attachedMatchID && $0.event.deviceID != DeviceIdentity.current
    }
    guard let last = remote.last else { return }
    remoteEventMatchID = last.matchID
    remoteEventDeviceID = last.event.deviceID
    remoteEventIsRoundCommit = remote.contains {
      if case .roundCommitted = $0.event.event { true } else { false }
    }
    remoteEventToken = UUID()
  }

  /// Une partie lancée par un autre appareil (« Partie suivante » d'un participant) : en créer la
  /// copie locale, avec les fiches et avatars de la partie précédente pour les mêmes joueurs, et
  /// la rattacher — le créateur la retrouve ainsi dans son historique comme les siennes.
  private func adoptMatchesStartedElsewhere(_ records: [SessionEventRecord]) async {
    guard let link, let repository else { return }
    for record in records where record.event.deviceID != DeviceIdentity.current {
      guard case .matchCreated = record.event.event,
        (try? repository.match(withID: record.matchID)) == nil
      else { continue }
      let previous = match
      // Même place, même nom : c'est le même joueur que dans la partie précédente.
      let previousBySeat = Dictionary(
        (previous?.participants ?? []).map { ("\($0.seatIndex)|\($0.nicknameSnapshot)", $0) },
        uniquingKeysWith: { first, _ in first })
      let events = await link.session.events(forMatch: record.matchID)
      let created = try? repository.createMirroredMatch(
        id: record.matchID, events: events, catalog: catalog
      ) { participant in
        if let source = previousBySeat["\(participant.seatIndex)|\(participant.displayName)"] {
          return MatchRepository.ParticipantSeed(
            player: source.player, nickname: source.nicknameSnapshot,
            avatarKind: source.avatarKindSnapshot, avatarValue: source.avatarValueSnapshot,
            paletteID: source.paletteIDSnapshot)
        }
        return Self.generatedSeed(for: participant.displayName)
      }
      guard let created else { continue }
      match = created
      attachedMatchID = created.id
      remoteStartedMatch = (created.id, previous?.id)
      remoteStartedToken = UUID()
    }
  }

  static func generatedSeed(for nickname: String) -> MatchRepository.ParticipantSeed {
    let avatar = Avatar.generated(for: nickname)
    let emoji: String = if case .emoji(let value) = avatar.kind { value } else { "" }
    return MatchRepository.ParticipantSeed(
      player: nil, nickname: nickname, avatarKind: "emoji", avatarValue: emoji,
      paletteID: String(avatar.palette.index))
  }

  /// Recopie le journal serveur de la partie rattachée dans sa copie locale. Jamais une copie
  /// incomplète : tant que le serveur n'a pas au moins autant d'événements que la partie locale
  /// (publication interrompue hors ligne), la copie locale est gardée telle quelle.
  private func mirrorAttachedMatch() async {
    guard let link, let match, let repository else { return }
    let serverLog = await link.session.events(forMatch: match.id)
    guard !serverLog.isEmpty,
      let localLog = try? repository.currentLog(for: match),
      serverLog.count >= localLog.count,
      serverLog.map(\.id) != localLog.map(\.id)
    else { return }
    _ = try? repository.replaceLog(serverLog, in: match, catalog: catalog)
  }
}
