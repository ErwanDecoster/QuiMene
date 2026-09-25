import Domain
import Foundation
import Testing

@testable import Sync

/// Doc 16, phase G — compatibilité croisée dans l'autre sens : `spec/session/android-*.json` sont
/// produits par le code Kotlin réel (`AndroidFixturesTest`, `QUIMENE_WRITE_SPEC=1`). iOS doit les
/// déchiffrer et y retrouver exactement les valeurs reconstruites ici, indépendamment : événements
/// de partie (toutes les sortes), identités, boîte aux lettres, mise à jour d'écran verrouillé.
@Suite("Fixtures produites par Android (doc 16, phase G)")
struct AndroidFixtureTests {
  private static let code = "042817"
  private static let sessionID = UUID(uuidString: "5A1E2B3C-4D5E-4F60-8172-8394A5B6C7D8")!
  private static let matchID = UUID(uuidString: "DDDDDDDD-0000-4000-8000-000000000001")!
  private static let profileID = UUID(uuidString: "33333333-3333-4333-8333-333333333333")!
  private static let device = "android-device"
  private static let marion = Participant(
    id: UUID(uuidString: "11111111-1111-4111-8111-111111111111")!, displayName: "Marion",
    seatIndex: 0, teamID: "A")
  private static let theo = Participant(
    id: UUID(uuidString: "22222222-2222-4222-8222-222222222222")!, displayName: "Théo",
    seatIndex: 1)

  private static func at(_ seconds: Double) -> Date {
    Date(timeIntervalSinceReferenceDate: 800_000_000 + seconds)
  }

  private static func id(_ n: Int) -> UUID {
    UUID(uuidString: String(format: "DDDDDDDD-0000-4000-8000-%012d", n))!
  }

  private static var events: [StampedEvent] {
    let round = RoundDraft(
      index: 0,
      inputs: [
        ScoreInput(participantID: marion.id, rawValue: 12, modifiers: [.closedRound]),
        ScoreInput(
          participantID: theo.id, rawValue: -3, detail: ScoreDetail(payload: Data([1, 2, 3]))),
      ],
      note: "Première manche")
    let kinds: [MatchEvent] = [
      .matchCreated(
        gameID: "skyjo", rulesVersion: 1,
        variants: ["threshold": .int(100), "doublePenalty": .bool(true), "mode": .string("classic")],
        participants: [marion, theo]),
      .roundCommitted(round),
      .roundAmended(index: 0, draft: RoundDraft(index: 0, inputs: round.inputs)),
      .noteAdded(roundIndex: 0, text: "Belle manche"),
      .roundRemoved(index: 0),
      .matchEndedManually,
      .matchAbandoned(at: at(400)),
    ]
    return kinds.enumerated().map { index, event in
      StampedEvent(
        id: index == 0 ? matchID : id(index + 1), lamport: UInt64(index + 1), deviceID: device,
        occurredAt: at(Double(index * 60)), event: event)
    }
  }

  private static func fixture(_ name: String) throws -> [String: Any] {
    try SessionFixture.load(name)
  }

  private static func ciphertexts(_ document: [String: Any]) throws -> [String] {
    try #require(document["events"] as? [[String: Any]]).map { $0["ciphertext"] as? String ?? "" }
  }

  @Test("Événements de partie scellés par Android : toutes les sortes, à l'identique")
  func sealedMatchEvents() throws {
    let key = SessionCrypto.deriveKey(pairingCode: Self.code, sessionID: Self.sessionID)
    let opened = try Self.ciphertexts(try Self.fixture("android-sealed-events.json")).map {
      try #require(
        OnlineSession.open(
          RawSessionEvent(
            seq: 1, eventID: UUID(), matchID: Self.matchID, deviceID: Self.device, ciphertext: $0),
          key: key)
      ).event
    }
    #expect(opened == Self.events)
  }

  @Test("Identités scellées par Android : à l'identique")
  func sealedIdentities() throws {
    let key = SessionCrypto.deriveKey(pairingCode: Self.code, sessionID: Self.sessionID)
    let owner = UUID(uuidString: "44444444-4444-4444-8444-444444444444")!
    let claim = SessionIdentityEvent(
      id: Self.id(101), deviceID: Self.device, occurredAt: Self.at(0), kind: .claim,
      seat: SeatRef(seatIndex: 1, displayName: "Théo"),
      profile: ProfileCard(
        id: Self.profileID, name: "Théo", avatarKind: "emoji", avatarValue: "🦊", paletteID: "4"))
    let expected = [
      claim,
      SessionIdentityEvent(
        id: Self.id(102), deviceID: Self.device, occurredAt: Self.at(30), kind: .roster,
        profile: ProfileCard(
          id: owner, name: "Erwan", avatarKind: "emoji", avatarValue: "🐻", paletteID: "1"),
        linkedSeats: [
          LinkedSeat(seat: SeatRef(seatIndex: 0, displayName: "Marion"), profileID: owner)
        ]),
      SessionIdentityEvent(
        id: Self.id(103), deviceID: Self.device, occurredAt: Self.at(60), kind: .revoke,
        revokedClaimID: claim.id),
    ]
    let opened = try Self.ciphertexts(try Self.fixture("android-identity-events.json")).map {
      try #require(
        OnlineSession.openIdentity(
          RawSessionEvent(
            seq: 1, eventID: UUID(), matchID: Self.matchID, deviceID: Self.device, ciphertext: $0),
          key: key)
      ).event
    }
    #expect(opened == expected)
  }

  @Test("Boîte aux lettres remplie par Android : même adresse, même partie")
  func mailboxPackage() throws {
    let document = try Self.fixture("android-mailbox-package.json")
    #expect(document["mailboxKey"] as? String == MailboxCrypto.lookupKey(for: Self.profileID))
    let expected = SharedMatchPackage(
      matchID: Self.matchID,
      participants: [
        .init(
          participantID: Self.marion.id, sharedProfileID: nil, nickname: "Marion",
          avatarKind: "emoji", avatarValue: "🐼", paletteID: "2"),
        .init(
          participantID: Self.theo.id, sharedProfileID: Self.profileID, nickname: "Théo",
          avatarKind: "emoji", avatarValue: "🦊", paletteID: "4"),
      ],
      events: Array(Self.events.prefix(2)))
    #expect(
      MailboxCrypto.open(document["ciphertext"] as? String ?? "", for: Self.profileID) == expected)
  }

  #if os(iOS)
    @Test("Mise à jour d'écran verrouillé envoyée par Android : lisible par la Live Activity")
    func lockScreenPush() throws {
      let document = try Self.fixture("android-live-activity-push.json")
      #expect(document["activityKey"] as? String == "session:\(Self.sessionID.uuidString)")
      #expect(document["event"] as? String == "update")
      let content = try JSONDecoder().decode(
        MatchActivityAttributes.ContentState.self,
        from: JSONSerialization.data(withJSONObject: try #require(document["contentState"])))
      #expect(
        content
          == MatchActivityAttributes.ContentState(
            matchID: Self.matchID, gameName: "Skyjo", gameSymbol: "square.grid.3x3.fill",
            roundNumber: 1,
            standings: [
              .init(id: Self.theo.id, name: "Théo", score: -3),
              .init(id: Self.marion.id, name: "Marion", score: 12),
            ]))
    }
  #endif
}
