import DesignSystem
import Domain
import Foundation
import Store
import Sync

/// Doc 16, phase C — la partie suivie par un participant. Aucun `MatchRecord` local : l'état est
/// rejoué depuis le journal de la session (`SessionLink`/`OnlineSession`), qui fait foi pour tous.
/// La partie courante est celle du `matchCreated` le plus récent : quand quelqu'un en lance une
/// nouvelle, l'écran la suit de lui-même.
@MainActor
@Observable
final class SharedMatchModel {
  private(set) var state: MatchState?
  private(set) var definition: GameDefinition?
  private var rules: (any GameRules)?
  /// Pourquoi la dernière saisie n'a pas été enregistrée (devancée, hors ligne, session arrêtée).
  private(set) var latestRejectionReason: String?
  /// Doc utilisateur — même bandeau que côté créateur quand une règle modifie un score saisi
  /// (doublement Skyjo…), recalculé localement par le rejeu.
  private(set) var roundExplanationMessage: String?
  private var roundExplanationClearTask: Task<Void, Never>?
  private(set) var isSubmitting = false

  let role: Role
  let link: SessionLink

  private let catalog: GameCatalog
  private let engine = MatchEngine()
  private var currentMatchID: UUID?

  /// Joignable, et session toujours ouverte. Hors ligne, le tableau reste celui du dernier
  /// rattrapage et la saisie est bloquée (doc 16).
  var isHostConnected: Bool { link.isReachable && !link.isClosed }
  var isOffline: Bool { !link.isReachable }
  var isSessionClosed: Bool { link.isClosed }

  var participants: [Participant] {
    state?.participants.sorted { $0.seatIndex < $1.seatIndex } ?? []
  }

  var totals: [Participant.ID: Int] { state?.totals() ?? [:] }

  var currentStandings: [Standing] {
    guard let state, let rules, let definition else { return [] }
    return rules.standings(state, definition: definition)
  }

  var isConcluded: Bool { state?.status == .ended || state?.status == .abandoned }

  /// Seul un contributeur peut saisir, et seulement tant que la session est ouverte et qu'il a
  /// dit qui il est dans la partie (doc 16, phase D) : un spectateur regarde.
  var canPropose: Bool { role == .contributor && !link.isClosed && mySeat != nil }

  // MARK: Qui es-tu ? (doc 16, phase D)

  /// Mon profil, tel que publié dans la session. `nil` sans profil : seul « Je regarde
  /// seulement » est alors possible.
  let me: ProfileCard?
  /// « Je regarde seulement » : choix local, retenu avec la session.
  private(set) var isSpectator: Bool
  private let onSpectatorChange: (Bool) -> Void
  /// Pourquoi il faut de nouveau dire qui on est (place prise, association annulée).
  private(set) var identityMessage: String?
  private(set) var isClaiming = false

  enum SeatStatus: Equatable {
    case free, mine, taken
  }

  /// Ma place dans la partie courante : reliée à mon profil par le créateur (ami déjà lié,
  /// reconnu sans question), ou revendiquée.
  var mySeat: SeatRef? {
    guard let me, let seat = link.identities.seat(of: me.id),
      participants.contains(where: { Self.seat(of: $0) == seat })
    else { return nil }
    return seat
  }

  /// « Qui es-tu ? » à afficher : la partie est chargée, et je n'ai ni place ni choisi de
  /// seulement regarder.
  var needsIdentity: Bool { state != nil && !isSpectator && mySeat == nil }

  func seatStatus(of participant: Participant) -> SeatStatus {
    let seat = Self.seat(of: participant)
    if seat == mySeat { return .mine }
    return link.identities.occupant(of: seat) == nil ? .free : .taken
  }

  /// Ma place revendiquée (pas reliée par le créateur) : elle seule peut être rendue.
  var canChangeSeat: Bool {
    guard let me else { return false }
    return link.identities.activeClaim(of: me.id) != nil
  }

  /// Le créateur, à ajouter à mes amis une fois ma place retenue : la liaison est durable dans
  /// les deux sens (doc 16).
  var ownerToBefriend: ProfileCard? {
    guard mySeat != nil, let owner = link.identities.owner, owner.id != me?.id else { return nil }
    return owner
  }

  /// Doc 16 — « Moi » sur ma place ; un lien sur les places occupées par un profil que je
  /// compte parmi mes amis (`friendProfileIDs`, lus par l'écran dans mes fiches).
  func profileBadges(friendProfileIDs: Set<UUID>) -> [Participant.ID: ScoreBoardView.ProfileBadge] {
    var badges: [Participant.ID: ScoreBoardView.ProfileBadge] = [:]
    let mine = mySeat
    for participant in participants {
      let seat = Self.seat(of: participant)
      if seat == mine {
        badges[participant.id] = .me
      } else if let occupant = link.identities.occupant(of: seat),
        friendProfileIDs.contains(occupant)
      {
        badges[participant.id] = .friend
      }
    }
    return badges
  }

  /// Ma place dans la partie courante (« Moi »).
  var myParticipantID: Participant.ID? {
    guard let mine = mySeat else { return nil }
    return participants.first { Self.seat(of: $0) == mine }?.id
  }

  static func seat(of participant: Participant) -> SeatRef {
    SeatRef(seatIndex: participant.seatIndex, displayName: participant.displayName)
  }

  /// « C'est moi » : revendique cette place. Premier arrivé, premier servi — si un autre appareil
  /// l'a prise juste avant, on le dit et la liste reste affichée.
  func claim(_ participant: Participant) async {
    guard let me, let matchID = currentMatchID, !isClaiming else { return }
    isClaiming = true
    defer { isClaiming = false }
    identityMessage = nil
    let seat = Self.seat(of: participant)
    let sent = await link.submitIdentity(
      .claim(seat, profile: me, deviceID: link.session.deviceID), matchID: matchID)
    if !sent {
      identityMessage =
        link.isClosed
        ? String(localized: "Le créateur a arrêté la session.")
        : String(localized: "Hors connexion : la saisie reprendra au retour du réseau.")
    } else if mySeat != seat {
      identityMessage = String(localized: "Cette place vient d'être prise par quelqu'un d'autre.")
    }
  }

  func watchOnly() {
    identityMessage = nil
    isSpectator = true
    onSpectatorChange(true)
  }

  /// Revenir à « Qui es-tu ? » : depuis « Je regarde seulement », ou pour changer de place (la
  /// revendication précédente est retirée).
  func chooseAgain() async {
    identityMessage = nil
    if isSpectator {
      isSpectator = false
      onSpectatorChange(false)
      return
    }
    guard let me, let claim = link.identities.activeClaim(of: me.id), let matchID = currentMatchID
    else { return }
    await link.submitIdentity(
      .revoke(claim.claimID, deviceID: link.session.deviceID), matchID: matchID)
  }

  init(
    link: SessionLink, role: Role, catalog: GameCatalog, me: ProfileCard?, isSpectator: Bool,
    onSpectatorChange: @escaping (Bool) -> Void
  ) {
    self.link = link
    self.role = role
    self.catalog = catalog
    self.me = me
    self.isSpectator = isSpectator
    self.onSpectatorChange = onSpectatorChange
    link.onNewRecords = { [weak self] records in
      Task { @MainActor [weak self] in await self?.reload(fresh: records) }
    }
    link.onNewIdentities = { [weak self] records in
      Task { @MainActor [weak self] in await self?.noticeRevocation(in: records) }
    }
  }

  /// Le créateur a annulé mon association : « Qui es-tu ? » réapparaît, avec l'explication.
  private func noticeRevocation(in records: [SessionIdentityRecord]) async {
    guard let me else { return }
    let all = await link.session.identities
    let revokedMine = records.contains { record in
      record.event.kind == .revoke && record.event.deviceID != link.session.deviceID
        && all.contains {
          $0.event.id == record.event.revokedClaimID && $0.event.profile?.id == me.id
        }
    }
    if revokedMine, mySeat == nil {
      identityMessage = String(localized: "Le créateur a annulé ton association à cette place.")
    }
  }

  /// Valide localement avant d'envoyer, avec les mêmes règles que partout ailleurs.
  func validate(_ inputs: [ScoreInput]) -> ValidationResult? {
    guard let state, let rules, let definition else { return nil }
    let draft = RoundDraft(index: state.nextRoundIndex, inputs: inputs)
    return rules.validate(draft, in: state, definition: definition)
  }

  /// Envoie une manche. `true` si elle est enregistrée ; sinon la saisie reste en place et
  /// `latestRejectionReason` dit pourquoi.
  @discardableResult
  func propose(_ inputs: [ScoreInput], note: String? = nil) async -> Bool {
    guard canPropose, let state, let matchID = currentMatchID, !isSubmitting else { return false }
    let draft = RoundDraft(index: state.nextRoundIndex, inputs: inputs, note: note)
    latestRejectionReason = nil
    isSubmitting = true
    defer { isSubmitting = false }
    // L'identifiant de partie du serveur, pas celui reconstruit par le rejeu : c'est lui qui
    // range l'événement dans la bonne partie pour tous les appareils.
    switch await link.submit(.roundCommitted(draft), matchID: matchID) {
    case .accepted:
      await reload(fresh: [], isLocalCommit: true)
      return true
    case .overtaken(let name):
      latestRejectionReason = Self.overtakenMessage(name)
      return false
    case .offline:
      latestRejectionReason = String(localized: "Hors connexion : la saisie reprendra au retour du réseau.")
      return false
    case .closed:
      latestRejectionReason = String(localized: "Le créateur a arrêté la session.")
      return false
    }
  }

  /// Doc 16, phase C — « Partie suivante » lancée par ce participant : mêmes joueurs, aux mêmes
  /// places (le créateur y retrouve ses fiches et avatars par place et par nom — de nouveaux
  /// identifiants, un identifiant de participant ne servant qu'à une partie, clé primaire côté
  /// Android), mêmes variantes si c'est le même jeu. Tout l'écran suit ensuite la nouvelle partie.
  @discardableResult
  func startNextMatch(definition next: GameDefinition) async -> Bool {
    guard canPropose, let state, !isSubmitting else { return false }
    let participants = state.participants.sorted { $0.seatIndex < $1.seatIndex }.map {
      Participant(displayName: $0.displayName, seatIndex: $0.seatIndex)
    }
    let variants = next.id == state.gameID ? state.variants : VariantSelection()
    let matchID = UUID()
    isSubmitting = true
    defer { isSubmitting = false }
    let result = await link.submit(
      .matchCreated(
        gameID: next.id, rulesVersion: next.rulesVersion, variants: variants,
        participants: participants),
      matchID: matchID, eventID: matchID)
    switch result {
    case .accepted:
      await reload(fresh: [])
      return true
    case .overtaken:
      latestRejectionReason = String(localized: "Une partie vient d'être lancée sur un autre appareil.")
      return false
    case .offline:
      latestRejectionReason = String(localized: "Hors connexion : la saisie reprendra au retour du réseau.")
      return false
    case .closed:
      latestRejectionReason = String(localized: "Le créateur a arrêté la session.")
      return false
    }
  }

  /// Fiches en mémoire, jamais enregistrées, pour réutiliser l'écran de résultats du créateur
  /// (`ResultsView` affiche noms et avatars depuis des `ParticipantRecord`). Avatar dérivé du
  /// pseudo, comme pour toute nouvelle fiche.
  var transientRecords: [ParticipantRecord] {
    if let cached = cachedRecords, cached.matchID == currentMatchID { return cached.records }
    let records = participants.map { participant in
      let seed = LiveShareCoordinator.generatedSeed(for: participant.displayName)
      return ParticipantRecord(
        id: participant.id, player: nil, nicknameSnapshot: participant.displayName,
        avatarKindSnapshot: seed.avatarKind, avatarValueSnapshot: seed.avatarValue,
        paletteIDSnapshot: seed.paletteID, seatIndex: participant.seatIndex,
        teamID: participant.teamID)
    }
    cachedRecords = (currentMatchID, records)
    return records
  }

  @ObservationIgnored private var cachedRecords: (matchID: UUID?, records: [ParticipantRecord])?

  static func overtakenMessage(_ name: String?) -> String {
    if let name {
      return String(localized: "\(name) vient de valider une manche : vérifie avant de valider la tienne.")
    }
    return String(localized: "Une autre manche vient d'être validée : vérifie avant de valider la tienne.")
  }

  /// Départ volontaire : termine la Live Activity et ferme le lien.
  func stop() async {
    if let matchID = state?.matchID {
      MatchLiveActivityController.stopTracking(matchID: matchID)
    }
    await link.stop()
  }

  /// Rejoue la partie courante depuis le journal de la session. `isLocalCommit` : la dernière
  /// manche vient de cet appareil, c'est donc à lui d'envoyer la mise à jour des écrans
  /// verrouillés (doc 16, phase F — plus d'hôte pour le faire).
  private func reload(fresh: [SessionEventRecord], isLocalCommit: Bool = false) async {
    guard let matchID = await link.session.currentMatchID() else { return }
    let log = await link.session.events(forMatch: matchID)
    guard let replayed = try? engine.replay(log, catalog: catalog) else { return }
    let isNewMatch = matchID != currentMatchID
    let previousRoundCount = isNewMatch ? 0 : (state?.rounds.count ?? 0)
    currentMatchID = matchID
    state = replayed
    definition = try? catalog.definition(for: replayed.gameID, version: replayed.rulesVersion)
    rules = try? catalog.rules(for: replayed.gameID, version: replayed.rulesVersion)
    if isNewMatch { latestRejectionReason = nil }

    if replayed.rounds.count > previousRoundCount,
      let explanation = replayed.rounds.last?.entries.compactMap(\.explanation).first
    {
      roundExplanationMessage = explanation
      roundExplanationClearTask?.cancel()
      roundExplanationClearTask = Task { [weak self] in
        try? await Task.sleep(for: .seconds(4))
        guard !Task.isCancelled else { return }
        self?.roundExplanationMessage = nil
      }
    }

    if let definition, let rules {
      MatchLiveActivityController.refresh(
        definition: definition, rules: rules, state: replayed,
        isAuthoritative: isLocalCommit, sessionID: link.sessionID)
    }
  }
}
