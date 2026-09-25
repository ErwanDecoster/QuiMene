import Domain
import Foundation
import Testing

@testable import Sync

@Suite("Boîte aux lettres — historique partagé (doc 16, phase E)")
struct SharedMatchMailboxTests {
  private static let profileID = UUID(uuidString: "33333333-3333-4333-8333-333333333333")!

  private static var package: SharedMatchPackage {
    let theo = Participant(
      id: UUID(uuidString: "11111111-1111-4111-8111-111111111111")!, displayName: "Théo",
      seatIndex: 0)
    let matchID = UUID(uuidString: "CCCCCCCC-0000-4000-8000-000000000001")!
    return SharedMatchPackage(
      matchID: matchID,
      participants: [
        .init(
          participantID: theo.id, sharedProfileID: profileID, nickname: "Théo",
          avatarKind: "emoji", avatarValue: "🦊", paletteID: "4")
      ],
      events: [
        StampedEvent(
          id: matchID, lamport: 1, deviceID: "ios-device",
          occurredAt: Date(timeIntervalSinceReferenceDate: 800_000_000),
          event: .matchCreated(
            gameID: "skyjo", rulesVersion: 1, variants: VariantSelection(), participants: [theo]))
      ])
  }

  @Test("Scellée pour un profil, lisible par lui seul")
  func sealedForOneProfile() throws {
    let sealed = try MailboxCrypto.seal(Self.package, for: Self.profileID)
    #expect(MailboxCrypto.open(sealed, for: Self.profileID) == Self.package)
    #expect(MailboxCrypto.open(sealed, for: UUID()) == nil)
  }

  @Test("L'adresse de la boîte ne révèle pas l'identifiant")
  func lookupKeyHidesTheProfile() {
    let key = MailboxCrypto.lookupKey(for: Self.profileID)
    #expect(key.count == 64)
    #expect(key.allSatisfy { "0123456789abcdef".contains($0) })
    #expect(!key.contains("33333333"))
    #expect(key == MailboxCrypto.lookupKey(for: Self.profileID))
  }

  private static let fixtureURL = SessionFixture.specURL("mailbox-package.json")

  /// `spec/session/mailbox-package.json` : produit par ce code (`QUIMENE_WRITE_SPEC=1`), relu ici
  /// et par le test Android, qui doit retrouver la même adresse et le même paquet.
  @Test("Fixture de boîte aux lettres : même adresse, paquet déchiffré à l'identique")
  func mailboxFixture() throws {
    if ProcessInfo.processInfo.environment["QUIMENE_WRITE_SPEC"] == "1" {
      let document: [String: Any] = [
        "profileID": Self.profileID.uuidString,
        "mailboxKey": MailboxCrypto.lookupKey(for: Self.profileID),
        "plaintext": String(decoding: try JSONEncoder().encode(Self.package), as: UTF8.self),
        "ciphertext": try MailboxCrypto.seal(Self.package, for: Self.profileID),
      ]
      let data = try JSONSerialization.data(
        withJSONObject: document, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
      try data.write(to: Self.fixtureURL)
    }
    let document = try SessionFixture.load("mailbox-package.json")
    #expect(document["mailboxKey"] as? String == MailboxCrypto.lookupKey(for: Self.profileID))
    let opened = MailboxCrypto.open(document["ciphertext"] as? String ?? "", for: Self.profileID)
    #expect(opened == Self.package)
  }
}
