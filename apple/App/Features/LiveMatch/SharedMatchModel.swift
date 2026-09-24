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

  /// Seul un contributeur peut saisir, et seulement tant que la session est ouverte.
  var canPropose: Bool { role == .contributor && !link.isClosed }

  init(link: SessionLink, role: Role, catalog: GameCatalog) {
    self.link = link
    self.role = role
    self.catalog = catalog
    link.onNewRecords = { [weak self] records in
      Task { @MainActor [weak self] in await self?.reload(fresh: records) }
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
