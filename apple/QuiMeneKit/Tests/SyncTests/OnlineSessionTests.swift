import Domain
import Foundation
import Testing

@testable import Sync

/// Mêmes règles que les fonctions SQL de `create_quimene_sessions` (vérifiées de leur côté par
/// `supabase/tests/quimene_sessions_test.sql`) : numérotation continue, ajout refusé s'il n'est
/// pas au numéro attendu, idempotent sur l'identifiant d'événement, lecture par lots de 500.
actor InMemorySessionBackend: OnlineSessionBackend {
  private var events: [UUID: [RawSessionEvent]] = [:]
  private var closed: Set<UUID> = []

  func open(sessionID: UUID, pairingCode: String, ownerDeviceID: String, allowsContributors: Bool) {
    events[sessionID] = events[sessionID] ?? []
  }

  func resolve(pairingCode: String) -> OnlineSessionInfo? { nil }

  func append(
    sessionID: UUID, expectedSeq: Int64, eventID: UUID, matchID: UUID, deviceID: String,
    ciphertext: String
  ) throws -> Int64 {
    var log = events[sessionID] ?? []
    if let existing = log.first(where: { $0.eventID == eventID }) { return existing.seq }
    guard !closed.contains(sessionID) else { throw OnlineSessionError.sessionClosed }
    let next = Int64(log.count) + 1
    guard expectedSeq == next else { throw OnlineSessionError.staleSequence }
    log.append(
      RawSessionEvent(
        seq: next, eventID: eventID, matchID: matchID, deviceID: deviceID, ciphertext: ciphertext))
    events[sessionID] = log
    return next
  }

  func events(sessionID: UUID, after seq: Int64) -> [RawSessionEvent] {
    Array((events[sessionID] ?? []).filter { $0.seq > seq }.prefix(OnlineSession.pageSize))
  }

  func close(sessionID: UUID, ownerDeviceID: String) {
    closed.insert(sessionID)
  }

  /// Simule un événement écrit par un appareil sans la bonne clé.
  func injectGarbage(sessionID: UUID) {
    var log = events[sessionID] ?? []
    log.append(
      RawSessionEvent(
        seq: Int64(log.count) + 1, eventID: UUID(), matchID: UUID(), deviceID: "intrus",
        ciphertext: Data("pas chiffré".utf8).base64EncodedString()))
    events[sessionID] = log
  }
}

@Suite("OnlineSession — journal de session en ligne (doc 16, phase C)")
struct OnlineSessionTests {
  private let participants = [
    Participant(displayName: "Marion", seatIndex: 0),
    Participant(displayName: "Théo", seatIndex: 1),
  ]

  private func created(_ gameID: String = "dummy") -> MatchEvent {
    .matchCreated(
      gameID: gameID, rulesVersion: 1, variants: VariantSelection(), participants: participants)
  }

  private func round(_ index: Int, _ value: Int) -> MatchEvent {
    .roundCommitted(
      RoundDraft(
        index: index, inputs: participants.map { ScoreInput(participantID: $0.id, rawValue: value) }))
  }

  @Test("Deux appareils partagent le même journal, ordonné par le serveur")
  func twoDevicesShareTheLog() async throws {
    let backend = InMemorySessionBackend()
    let sessionID = UUID()
    let matchID = UUID()
    let erwan = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "erwan", backend: backend)
    let theo = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "theo", backend: backend)

    try await erwan.append(created(), matchID: matchID)
    try await theo.sync()
    try await theo.append(round(0, 7), matchID: matchID)
    let fresh = try await erwan.sync()

    #expect(fresh.map(\.event.deviceID) == ["theo"])
    let events = await erwan.events(forMatch: matchID)
    #expect(events.map(\.lamport) == [1, 2], "l'horodatage logique est le numéro du serveur")
    #expect(await theo.events(forMatch: matchID) == events)
  }

  @Test("Un ajout devancé est refusé, et l'appareil a déjà rattrapé quand il le reçoit")
  func staleAppendCatchesUp() async throws {
    let backend = InMemorySessionBackend()
    let sessionID = UUID()
    let matchID = UUID()
    let erwan = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "erwan", backend: backend)
    let theo = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "theo", backend: backend)

    try await erwan.append(created(), matchID: matchID)
    try await theo.sync()
    try await erwan.append(round(0, 10), matchID: matchID)

    await #expect(throws: OnlineSessionError.staleSequence) {
      try await theo.append(round(0, 1), matchID: matchID)
    }
    #expect(await theo.lastSeq == 2)
    let state = try MatchEngine().replay(await theo.events(forMatch: matchID), catalog: .testing)
    #expect(state.rounds.count == 1, "Théo voit la manche d'Erwan avant de revalider la sienne")

    try await theo.append(round(state.nextRoundIndex, 1), matchID: matchID)
    #expect(await theo.lastSeq == 3)
  }

  @Test("Un événement indéchiffrable est sauté, mais compte dans la numérotation")
  func undecryptableEventIsSkipped() async throws {
    let backend = InMemorySessionBackend()
    let sessionID = UUID()
    let matchID = UUID()
    let erwan = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "erwan", backend: backend)
    try await erwan.append(created(), matchID: matchID)
    await backend.injectGarbage(sessionID: sessionID)

    let late = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "late", backend: backend)
    try await late.sync()
    #expect(await late.records.count == 1)
    #expect(await late.lastSeq == 2)
    try await late.append(round(0, 3), matchID: matchID)
    #expect(await late.lastSeq == 3)

    let wrongCode = OnlineSession(
      sessionID: sessionID, pairingCode: "000000", deviceID: "x", backend: backend)
    try await wrongCode.sync()
    #expect(await wrongCode.records.isEmpty, "sans le bon code, rien n'est lisible")
  }

  @Test("Le rattrapage lit au-delà d'un lot de 500 événements")
  func syncPagesThroughBatches() async throws {
    let backend = InMemorySessionBackend()
    let sessionID = UUID()
    let matchID = UUID()
    let writer = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "w", backend: backend)
    try await writer.append(created(), matchID: matchID)
    for index in 0..<1_100 {
      try await writer.append(.noteAdded(roundIndex: index, text: "n"), matchID: matchID)
    }
    let reader = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "r", backend: backend)
    let fresh = try await reader.sync()
    #expect(fresh.count == 1_101)
    #expect(await reader.lastSeq == 1_101)
  }

  @Test("Une partie publiée garde son identifiant : celui de son matchCreated local")
  func publishedMatchKeepsItsID() async throws {
    // Remontée : le créateur ré-horodatait son journal local avec de nouveaux identifiants ;
    // `MatchEngine` tirant l'identifiant de partie du `matchCreated`, les participants
    // écrivaient ensuite leurs manches sous un autre identifiant — invisibles pour tous.
    let backend = InMemorySessionBackend()
    let sessionID = UUID()
    let local = StampedEvent(lamport: 1, deviceID: "erwan", occurredAt: Date(), event: created())
    let matchID = local.id
    let erwan = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "erwan", backend: backend)
    try await erwan.append(
      local.event, matchID: matchID, eventID: local.id, occurredAt: local.occurredAt)

    let theo = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "theo", backend: backend)
    try await theo.sync()
    let replayed = try MatchEngine().replay(await theo.events(forMatch: matchID), catalog: .testing)
    #expect(replayed.matchID == matchID)
    #expect(await theo.currentMatchID() == matchID)
  }

  @Test("La partie courante est celle du matchCreated le plus récent")
  func currentMatchIsLatestCreated() async throws {
    let backend = InMemorySessionBackend()
    let session = OnlineSession(
      sessionID: UUID(), pairingCode: "042817", deviceID: "d", backend: backend)
    let first = UUID()
    let second = UUID()
    try await session.append(created(), matchID: first)
    try await session.append(round(0, 2), matchID: first)
    #expect(await session.currentMatchID() == first)
    try await session.append(created(), matchID: second)
    #expect(await session.currentMatchID() == second)
    #expect(await session.events(forMatch: first).count == 2)
  }
}

extension GameCatalog {
  /// Catalogue minimal pour rejouer un journal dans ces tests (même patron que
  /// `LiveSessionTests`).
  fileprivate static var testing: GameCatalog {
    let definition = GameDefinition(
      id: "dummy",
      specVersion: 1,
      rulesVersion: 1,
      name: .init(fr: "Test"),
      symbol: "circle",
      players: .init(min: 2, max: 8),
      scoring: .init(direction: .lowestWins, entry: .init(kind: .integer)),
      engine: TestingRules.engineID,
      end: .init(conditions: [.init(type: .roundLimit, value: 1000)]),
      tieBreak: [.shared]
    )
    return try! GameCatalog(
      definitions: [definition], engineTable: [TestingRules.engineID: { TestingRules() }])
  }
}

private struct TestingRules: GameRules {
  static let engineID = "test.online.v1"
}
