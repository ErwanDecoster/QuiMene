import CryptoKit
import Domain
import Foundation
import Testing

@testable import Sync

@Suite("SessionIdentities — « Qui es-tu ? » (doc 16, phase D)")
struct SessionIdentityTests {
  private let owner = "owner-device"
  private let marion = SeatRef(seatIndex: 0, displayName: "Marion")
  private let theo = SeatRef(seatIndex: 1, displayName: "Théo")

  private func card(_ name: String, id: UUID = UUID()) -> ProfileCard {
    ProfileCard(id: id, name: name, avatarKind: "emoji", avatarValue: "🦊", paletteID: "3")
  }

  private func records(_ events: [SessionIdentityEvent]) -> [SessionIdentityRecord] {
    events.enumerated().map { SessionIdentityRecord(seq: Int64($0.offset + 1), event: $0.element) }
  }

  @Test("Premier arrivé, premier servi")
  func firstClaimWins() {
    let first = card("Théo")
    let second = card("Intrus")
    let identities = SessionIdentities(
      records: records([
        .claim(theo, profile: first, deviceID: "a"),
        .claim(theo, profile: second, deviceID: "b"),
      ]),
      ownerDeviceID: owner)
    #expect(identities.occupant(of: theo) == first.id)
    #expect(identities.seat(of: second.id) == nil)
  }

  @Test("Une place reliée par le créateur à un autre profil ne peut pas être revendiquée")
  func linkedSeatIsProtected() {
    let friend = UUID()
    let intruder = card("Théo")
    let identities = SessionIdentities(
      records: records([
        .roster(owner: card("Erwan"), linkedSeats: [LinkedSeat(seat: theo, profileID: friend)], deviceID: owner),
        .claim(theo, profile: intruder, deviceID: "b"),
      ]),
      ownerDeviceID: owner)
    #expect(identities.occupant(of: theo) == friend)
    #expect(identities.seat(of: friend) == theo)
    #expect(identities.activeClaims.isEmpty)
  }

  @Test("Reconnu d'office sur une place, on peut en changer : l'ancienne devient libre")
  func linkedProfileCanMove() {
    let erwanCard = card("Erwan")
    let identities = SessionIdentities(
      records: records([
        .roster(
          owner: card("Hôte"), linkedSeats: [LinkedSeat(seat: marion, profileID: erwanCard.id)],
          deviceID: owner),
        .claim(theo, profile: erwanCard, deviceID: "erwan-phone"),
        .claim(marion, profile: card("Marion"), deviceID: "marion-phone"),
      ]),
      ownerDeviceID: owner)
    #expect(identities.seat(of: erwanCard.id) == theo)
    #expect(identities.occupant(of: theo) == erwanCard.id)
    #expect(identities.occupant(of: marion) != erwanCard.id)
    #expect(identities.occupant(of: marion) != nil)
  }

  @Test("Un registre publié par un autre appareil que le créateur est ignoré")
  func rosterFromParticipantIgnored() {
    let identities = SessionIdentities(
      records: records([
        .roster(owner: card("Faux"), linkedSeats: [LinkedSeat(seat: theo, profileID: UUID())], deviceID: "b")
      ]),
      ownerDeviceID: owner)
    #expect(identities.owner == nil)
    #expect(identities.occupant(of: theo) == nil)
  }

  @Test("Annulation par le créateur ou par l'auteur, pas par un tiers")
  func revocation() {
    let theoCard = card("Théo")
    let claim = SessionIdentityEvent.claim(theo, profile: theoCard, deviceID: "theo-phone")
    let byThirdParty = SessionIdentities(
      records: records([claim, .revoke(claim.id, deviceID: "autre")]), ownerDeviceID: owner)
    #expect(byThirdParty.occupant(of: theo) == theoCard.id)
    let byOwner = SessionIdentities(
      records: records([claim, .revoke(claim.id, deviceID: owner)]), ownerDeviceID: owner)
    #expect(byOwner.occupant(of: theo) == nil)
    let byAuthor = SessionIdentities(
      records: records([claim, .revoke(claim.id, deviceID: "theo-phone")]), ownerDeviceID: owner)
    #expect(byAuthor.occupant(of: theo) == nil)
  }

  @Test("Un profil n'occupe qu'une place : la nouvelle revendication remplace l'ancienne")
  func oneSeatPerProfile() {
    let theoCard = card("Théo")
    let identities = SessionIdentities(
      records: records([
        .claim(marion, profile: theoCard, deviceID: "a"),
        .claim(theo, profile: theoCard, deviceID: "a"),
      ]),
      ownerDeviceID: owner)
    #expect(identities.seat(of: theoCard.id) == theo)
    #expect(identities.occupant(of: marion) == nil)
  }

  @Test("Les identités partagent le journal sans gêner le rejeu des parties")
  func identitiesInTheLog() async throws {
    let backend = InMemorySessionBackend()
    let sessionID = UUID()
    let matchID = UUID()
    let participants = [Participant(displayName: "Marion", seatIndex: 0)]
    let erwan = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: owner, backend: backend)
    let theoPhone = OnlineSession(
      sessionID: sessionID, pairingCode: "042817", deviceID: "theo", backend: backend)

    try await erwan.append(
      .matchCreated(gameID: "dummy", rulesVersion: 1, variants: VariantSelection(), participants: participants),
      matchID: matchID, eventID: matchID)
    try await theoPhone.sync()
    try await theoPhone.appendIdentity(
      .claim(marion, profile: card("Marion"), deviceID: "theo"), matchID: matchID)

    try await erwan.sync()
    #expect(await erwan.records.count == 1)
    #expect(await erwan.identities.count == 1)
    #expect(await erwan.lastSeq == 2)
    #expect(await erwan.currentMatchID() == matchID)
  }

  // MARK: - Fixture multiplateforme

  private static let fixtureURL = SessionFixture.specURL("identity-events.json")

  private static let fixtureSessionID = UUID(uuidString: "5A1E2B3C-4D5E-4F60-8172-8394A5B6C7D8")!

  private static var fixtureEvents: [SessionIdentityEvent] {
    let profile = ProfileCard(
      id: UUID(uuidString: "33333333-3333-4333-8333-333333333333")!, name: "Théo",
      avatarKind: "emoji", avatarValue: "🦊", paletteID: "4")
    let owner = ProfileCard(
      id: UUID(uuidString: "44444444-4444-4444-8444-444444444444")!, name: "Erwan",
      avatarKind: "emoji", avatarValue: "🐻", paletteID: "1")
    let claim = SessionIdentityEvent(
      id: UUID(uuidString: "BBBBBBBB-0000-4000-8000-000000000001")!, deviceID: "ios-device",
      occurredAt: Date(timeIntervalSinceReferenceDate: 800_000_000), kind: .claim,
      seat: SeatRef(seatIndex: 1, displayName: "Théo"), profile: profile)
    return [
      claim,
      SessionIdentityEvent(
        id: UUID(uuidString: "BBBBBBBB-0000-4000-8000-000000000002")!, deviceID: "ios-device",
        occurredAt: Date(timeIntervalSinceReferenceDate: 800_000_030), kind: .roster,
        profile: owner,
        linkedSeats: [
          LinkedSeat(seat: SeatRef(seatIndex: 0, displayName: "Erwan"), profileID: owner.id)
        ]),
      SessionIdentityEvent(
        id: UUID(uuidString: "BBBBBBBB-0000-4000-8000-000000000003")!, deviceID: "ios-device",
        occurredAt: Date(timeIntervalSinceReferenceDate: 800_000_060), kind: .revoke,
        revokedClaimID: claim.id),
    ]
  }

  /// `spec/session/identity-events.json` : produit par ce code (`QUIMENE_WRITE_SPEC=1`), relu ici
  /// et par le test Android, qui doit retrouver exactement les mêmes événements.
  @Test("Fixture d'identités : déchiffrée à l'identique")
  func identityFixture() throws {
    let key = SessionCrypto.deriveKey(pairingCode: "042817", sessionID: Self.fixtureSessionID)
    if ProcessInfo.processInfo.environment["QUIMENE_WRITE_SPEC"] == "1" {
      let events = try Self.fixtureEvents.enumerated().map { offset, event in
        [
          "seq": offset + 3,
          "plaintext": String(
            decoding: try JSONEncoder().encode(SessionIdentityEnvelope(identity: event)),
            as: UTF8.self),
          "ciphertext": try OnlineSession.seal(event, key: key),
        ] as [String: Any]
      }
      let document: [String: Any] = [
        "pairingCode": "042817", "sessionID": Self.fixtureSessionID.uuidString, "events": events,
      ]
      let data = try JSONSerialization.data(
        withJSONObject: document, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
      try data.write(to: Self.fixtureURL)
    }

    let document = try SessionFixture.load("identity-events.json")
    let events = try #require(document["events"] as? [[String: Any]])
    #expect(events.count == Self.fixtureEvents.count)
    for (raw, expected) in zip(events, Self.fixtureEvents) {
      let record = try #require(
        OnlineSession.openIdentity(
          RawSessionEvent(
            seq: Int64(raw["seq"] as? Int ?? 0), eventID: expected.id, matchID: UUID(),
            deviceID: "ios-device", ciphertext: raw["ciphertext"] as? String ?? ""),
          key: key))
      #expect(record.event == expected)
    }
  }
}
