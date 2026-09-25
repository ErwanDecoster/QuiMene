import Domain
import Foundation
import SwiftData

/// Doc 02 : les écritures interactives restent sur le `mainContext`. `eventLogData` est
/// toujours la source de vérité — chaque écriture réencode le journal complet et rejoue, plutôt
/// que de faire confiance à un total mis en cache (doc 04).
@MainActor
public struct MatchRepository {
  public struct ParticipantSeed {
    public let player: PlayerRecord?
    public let nickname: String
    public let avatarKind: String
    public let avatarValue: String
    public let paletteID: String
    /// Doc 05 « Belote » — `nil` pour tous les jeux individuels.
    public let teamID: String?

    public init(
      player: PlayerRecord?, nickname: String, avatarKind: String, avatarValue: String,
      paletteID: String, teamID: String? = nil
    ) {
      self.player = player
      self.nickname = nickname
      self.avatarKind = avatarKind
      self.avatarValue = avatarValue
      self.paletteID = paletteID
      self.teamID = teamID
    }
  }

  private let context: ModelContext

  public init(context: ModelContext) {
    self.context = context
  }

  @discardableResult
  public func createMatch(
    gameID: String,
    rulesVersion: Int,
    variants: VariantSelection,
    seeds: [ParticipantSeed],
    deviceID: String = "local"
  ) throws -> MatchRecord {
    var participantModels: [ParticipantRecord] = []
    var domainParticipants: [Participant] = []

    for (index, seed) in seeds.enumerated() {
      let id = UUID()
      participantModels.append(
        ParticipantRecord(
          id: id,
          player: seed.player,
          nicknameSnapshot: seed.nickname,
          avatarKindSnapshot: seed.avatarKind,
          avatarValueSnapshot: seed.avatarValue,
          paletteIDSnapshot: seed.paletteID,
          seatIndex: index,
          teamID: seed.teamID
        ))
      domainParticipants.append(
        Participant(id: id, displayName: seed.nickname, seatIndex: index, teamID: seed.teamID))
    }

    let createdEvent = StampedEvent(
      lamport: 0,
      deviceID: deviceID,
      occurredAt: Date(),
      event: .matchCreated(
        gameID: gameID, rulesVersion: rulesVersion, variants: variants,
        participants: domainParticipants)
    )

    let match = MatchRecord(
      gameID: gameID,
      rulesVersion: rulesVersion,
      variantsData: try JSONEncoder().encode(variants),
      deviceOrigin: deviceID,
      eventLogData: try JSONEncoder().encode([createdEvent]),
      participants: participantModels
    )
    context.insert(match)
    try context.save()
    return match
  }

  public func loadState(_ match: MatchRecord, catalog: GameCatalog) throws -> MatchState {
    let events = try JSONDecoder().decode([StampedEvent].self, from: match.eventLogData)
    return try MatchEngine().replay(events, catalog: catalog)
  }

  @discardableResult
  public func commitRound(
    _ draft: RoundDraft,
    to match: MatchRecord,
    catalog: GameCatalog,
    deviceID: String = "local"
  ) throws -> MatchState {
    try appendEvent(.roundCommitted(draft), to: match, catalog: catalog, deviceID: deviceID)
  }

  @discardableResult
  public func undoLastRound(in match: MatchRecord, catalog: GameCatalog, deviceID: String = "local")
    throws -> MatchState
  {
    // La dernière manche *présente*, pas la plus grande jamais validée dans le journal : une
    // manche déjà annulée y figure toujours, et une deuxième annulation de suite la visait à
    // nouveau au lieu de retirer la précédente (sans effet visible).
    let lastIndex = try loadState(match, catalog: catalog).rounds.map(\.index).max()

    guard let lastIndex else {
      return try loadState(match, catalog: catalog)
    }
    return try appendEvent(
      .roundRemoved(index: lastIndex), to: match, catalog: catalog, deviceID: deviceID)
  }

  /// Doc 05 « Jeu libre » et tout jeu `manualStop` (Scrabble, Qwirkle…) : `endCheck` ne
  /// détecte jamais cette fin tout seul, elle vient toujours d'une action explicite du joueur.
  @discardableResult
  public func endMatchManually(
    _ match: MatchRecord, catalog: GameCatalog, deviceID: String = "local"
  ) throws -> MatchState {
    try appendEvent(.matchEndedManually, to: match, catalog: catalog, deviceID: deviceID)
  }

  /// Une partie abandonnée reçoit quand même un classement final (calculé sur l'état atteint)
  /// et apparaît dans l'historique — contrairement à une simple suppression, rien n'est perdu.
  @discardableResult
  public func abandonMatch(_ match: MatchRecord, catalog: GameCatalog, deviceID: String = "local")
    throws -> MatchState
  {
    try appendEvent(.matchAbandoned(at: Date()), to: match, catalog: catalog, deviceID: deviceID)
  }

  /// Doc 01 « Historique » — masque la partie de la liste sans en perdre la trace : les
  /// statistiques de profil (`ProfileRepository`) continuent de la compter, seul l'onglet
  /// Historique la filtre. Même mécanisme que `PlayerRepository.archive`.
  public func archive(_ match: MatchRecord) throws {
    match.isArchived = true
    try context.save()
  }

  public func unarchive(_ match: MatchRecord) throws {
    match.isArchived = false
    try context.save()
  }

  /// Suppression définitive — irréversible, contrairement à `archive`. Cascade sur
  /// `ParticipantRecord` (doc 03) : ces lignes de statistiques disparaissent avec la partie,
  /// contrairement à `PlayerRepository.delete` qui, lui, laisse l'historique intact.
  public func delete(_ match: MatchRecord) throws {
    context.delete(match)
    try context.save()
  }

  /// Doc utilisateur — remontée : rien n'empêche de démarrer plusieurs parties sans terminer la
  /// précédente ; toutes doivent rester reprenables, pas seulement la première trouvée. Triées
  /// par date de début, la plus récente d'abord (même convention que `finishedMatches`).
  public func inProgressMatches() throws -> [MatchRecord] {
    let descriptor = FetchDescriptor<MatchRecord>(
      predicate: #Predicate { $0.statusRaw == "inProgress" || $0.statusRaw == "finalRound" },
      sortBy: [SortDescriptor(\.startedAt, order: .reverse)]
    )
    return try context.fetch(descriptor)
  }

  /// Doc utilisateur « Handoff » (P9) — résout la partie reprise sur un autre appareil à partir
  /// du seul id transporté par `NSUserActivity` ; `nil` si elle n'est pas (encore) synchronisée
  /// localement via CloudKit.
  public func match(withID id: UUID) throws -> MatchRecord? {
    var descriptor = FetchDescriptor<MatchRecord>(predicate: #Predicate { $0.id == id })
    descriptor.fetchLimit = 1
    return try context.fetch(descriptor).first
  }

  /// Aucune partie jamais créée, terminée ou non — sert à distinguer « premier lancement ».
  public func hasAnyMatch() throws -> Bool {
    var descriptor = FetchDescriptor<MatchRecord>()
    descriptor.fetchLimit = 1
    return try !context.fetch(descriptor).isEmpty
  }

  /// Doc 01 « Historique » — parties terminées ou abandonnées, les plus récentes d'abord. Les
  /// filtres par jeu/joueur restent en mémoire côté appelant (doc 06 : le volume attendu ne
  /// justifie pas des prédicats composés).
  public func finishedMatches() throws -> [MatchRecord] {
    let descriptor = FetchDescriptor<MatchRecord>(
      predicate: #Predicate {
        !$0.isArchived && ($0.statusRaw == "ended" || $0.statusRaw == "abandoned")
      },
      sortBy: [SortDescriptor(\.startedAt, order: .reverse)]
    )
    return try context.fetch(descriptor)
  }

  /// Parties masquées de l'Historique via `archive(_:)` — écran séparé, même logique que
  /// `ArchivedPlayersView` pour les joueurs.
  public func archivedMatches() throws -> [MatchRecord] {
    let descriptor = FetchDescriptor<MatchRecord>(
      predicate: #Predicate { $0.isArchived },
      sortBy: [SortDescriptor(\.startedAt, order: .reverse)]
    )
    return try context.fetch(descriptor)
  }

  /// Joueurs de la dernière partie jouée à ce jeu (terminée ou abandonnée), dans l'ordre des
  /// sièges — sert à pré-sélectionner les habitués à la mise en place d'une nouvelle partie.
  /// Vide si ce jeu n'a jamais été joué (l'appelant retombe alors sur un autre critère, ex.
  /// les joueurs créés le plus récemment).
  public func mostRecentParticipants(forGameID gameID: String) throws -> [PlayerRecord] {
    var descriptor = FetchDescriptor<MatchRecord>(
      predicate: #Predicate {
        $0.gameID == gameID && ($0.statusRaw == "ended" || $0.statusRaw == "abandoned")
      },
      sortBy: [SortDescriptor(\.startedAt, order: .reverse)]
    )
    descriptor.fetchLimit = 1
    guard let lastMatch = try context.fetch(descriptor).first else { return [] }
    return lastMatch.participants
      .sorted { $0.seatIndex < $1.seatIndex }
      .compactMap(\.player)
  }

  /// Nombre de parties jouées (terminées ou abandonnées), tous jeux confondus, par joueur — sert
  /// à faire remonter les habitués en tête de la présélection d'une nouvelle partie (comportement
  /// calqué sur culnugame, doc utilisateur).
  public func participationCounts() throws -> [UUID: Int] {
    let descriptor = FetchDescriptor<MatchRecord>(
      predicate: #Predicate { $0.statusRaw == "ended" || $0.statusRaw == "abandoned" }
    )
    var counts: [UUID: Int] = [:]
    for match in try context.fetch(descriptor) {
      for participant in match.participants {
        guard let player = participant.player else { continue }
        counts[player.id, default: 0] += 1
      }
    }
    return counts
  }

  /// Doc 14, phase 2 — parties conclues avec au moins un participant lié, dont le résumé n'a
  /// pas encore été confirmé poussé (`SharedProfileSyncCoordinator` les retente à chaque retour
  /// au premier plan).
  public func matchesPendingSharedProfileSync() throws -> [MatchRecord] {
    let descriptor = FetchDescriptor<MatchRecord>(
      predicate: #Predicate { $0.pendingSharedProfileSync })
    return try context.fetch(descriptor)
  }

  public func markSharedProfileSyncComplete(_ match: MatchRecord) throws {
    match.pendingSharedProfileSync = false
    try context.save()
  }

  /// Doc 16, phase E — enregistre une partie reçue d'un ami (boîte aux lettres), complète : même
  /// journal, mêmes participants, rejouée comme une partie jouée ici. Chaque joueur lié à un
  /// profil connu de cet appareil (le mien, ou un ami) est relié à sa fiche ; les autres gardent
  /// seulement leurs *snapshots*. Sans effet si la partie est déjà connue (jouée ici, suivie dans
  /// la session, ou déjà reçue) ; `nil` si le paquet est illisible.
  @discardableResult
  public func importSharedMatch(_ package: SharedMatchPackage, catalog: GameCatalog) throws
    -> MatchRecord?
  {
    if let existing = try match(withID: package.matchID) { return existing }
    let players = PlayerRepository(context: context)
    let byID = Dictionary(
      package.participants.map { ($0.participantID, $0) }, uniquingKeysWith: { first, _ in first })
    return try createMirroredMatch(
      id: package.matchID, events: package.events, catalog: catalog, isReceived: true
    ) {
      participant in
      let entry = byID[participant.id]
      let player = entry?.sharedProfileID.flatMap { try? players.player(withSharedProfileID: $0) }
      return ParticipantSeed(
        player: player,
        nickname: entry?.nickname ?? participant.displayName,
        avatarKind: entry?.avatarKind ?? "emoji",
        avatarValue: entry?.avatarValue ?? "",
        paletteID: entry?.paletteID ?? "1")
    }
  }

  /// Le journal complet d'une partie — publié tel quel quand elle rejoint une session en ligne
  /// (doc 16, `LiveShareCoordinator.attach`).
  public func currentLog(for match: MatchRecord) throws -> [StampedEvent] {
    try JSONDecoder().decode([StampedEvent].self, from: match.eventLogData)
  }

  /// Doc 16, phase C — crée la copie locale d'une partie lancée par un autre appareil de la
  /// session (« Partie suivante » d'un participant). Les participants gardent l'identifiant que
  /// leur donne son `matchCreated` ; `seed` fournit, pour chacun, la fiche et l'avatar à retenir
  /// (ceux de la partie précédente quand c'est le même joueur, à la même place). `nil` si le
  /// journal ne commence pas par un `matchCreated`.
  @discardableResult
  public func createMirroredMatch(
    id: UUID, events: [StampedEvent], catalog: GameCatalog, isReceived: Bool = false,
    seed: (Participant) -> ParticipantSeed
  ) throws -> MatchRecord? {
    guard let first = events.first,
      case .matchCreated(let gameID, let rulesVersion, let variants, let participants) = first.event
    else { return nil }
    let records = participants.map { participant in
      let seed = seed(participant)
      return ParticipantRecord(
        id: participant.id,
        player: seed.player,
        nicknameSnapshot: seed.nickname,
        avatarKindSnapshot: seed.avatarKind,
        avatarValueSnapshot: seed.avatarValue,
        paletteIDSnapshot: seed.paletteID,
        seatIndex: participant.seatIndex,
        teamID: participant.teamID
      )
    }
    let match = MatchRecord(
      id: id,
      gameID: gameID,
      rulesVersion: rulesVersion,
      variantsData: try JSONEncoder().encode(variants),
      startedAt: first.occurredAt,
      deviceOrigin: isReceived ? MatchRecord.receivedOrigin : first.deviceID,
      eventLogData: try JSONEncoder().encode(events),
      participants: records
    )
    context.insert(match)
    try persist(events, to: match, catalog: catalog)
    return match
  }

  /// Doc 16, phase C — une partie partagée en ligne : le journal de la session (serveur) fait foi,
  /// la copie locale du créateur en est le miroir. Remplace le journal local en entier ; renvoie
  /// l'état rejoué.
  @discardableResult
  public func replaceLog(
    _ events: [StampedEvent], in match: MatchRecord, catalog: GameCatalog
  ) throws -> MatchState {
    try persist(events, to: match, catalog: catalog)
  }

  @discardableResult
  private func appendEvent(
    _ event: MatchEvent,
    to match: MatchRecord,
    catalog: GameCatalog,
    deviceID: String
  ) throws -> MatchState {
    var events = try currentLog(for: match)
    let nextLamport = (events.map(\.lamport).max() ?? 0) + 1
    events.append(
      StampedEvent(lamport: nextLamport, deviceID: deviceID, occurredAt: Date(), event: event))
    return try persist(events, to: match, catalog: catalog)
  }

  private func persist(_ events: [StampedEvent], to match: MatchRecord, catalog: GameCatalog) throws
    -> MatchState
  {
    match.eventLogData = try JSONEncoder().encode(events)

    let state = try MatchEngine().replay(events, catalog: catalog)
    match.statusRaw = state.status.rawValue
    match.endReasonRaw = state.endReason?.rawValue

    if state.status == .ended || state.status == .abandoned {
      // L'heure du dernier événement, pas celle de l'écriture : une copie faite plus tard (partie
      // suivie depuis un autre appareil, reçue d'un ami) garde la vraie heure de fin.
      match.endedAt = events.last?.occurredAt ?? Date()
      applyFinalStandings(state: state, match: match, catalog: catalog)
      // Doc 14, phase 2 — un seul appel suffit même si le lien a été fait après coup entre
      // deux manches : ce drapeau est réévalué à chaque conclusion, jamais figé à la
      // création de la partie.
      match.pendingSharedProfileSync = match.participants.contains {
        $0.player?.sharedProfileID != nil
      }
    } else {
      match.endedAt = nil
    }

    try context.save()
    return state
  }

  private func applyFinalStandings(state: MatchState, match: MatchRecord, catalog: GameCatalog) {
    guard let definition = try? catalog.definition(for: match.gameID, version: match.rulesVersion),
      let rules = try? catalog.rules(for: match.gameID, version: match.rulesVersion)
    else { return }

    let standingsByID = Dictionary(
      uniqueKeysWithValues: rules.standings(state, definition: definition).map {
        ($0.participantID, $0)
      })
    for participant in match.participants {
      guard let standing = standingsByID[participant.id] else { continue }
      participant.finalRank = standing.rank
      participant.finalScore = standing.score
    }
  }
}
