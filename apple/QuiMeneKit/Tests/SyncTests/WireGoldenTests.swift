import Domain
import Foundation
import Testing

@testable import Sync

/// Golden files du protocole applicatif (`spec/wire/*.json`, doc 09 « Tests »), un par cas de
/// `WireMessage.Kind`. Garde-fou de portage : un développeur Kotlin décode ces mêmes fixtures et
/// vérifie qu'il obtient une valeur équivalente, sans jamais lire le code Swift pour deviner le
/// format d'échange. Même patron de découverte que `GoldenFile` (`CatalogTests`), mais décode
/// directement dans le type de production `WireMessage` — pas de struct de fixture intermédiaire,
/// il n'y a pas de traduction à faire entre le format du fichier et le domaine.
@Suite("Wire golden files")
struct WireGoldenTests {
  static let fixtureURLs: [URL] =
    (Bundle.module.urls(forResourcesWithExtension: "json", subdirectory: "WireResources") ?? [])
    .sorted { $0.lastPathComponent < $1.lastPathComponent }

  @Test(
    "Une fixture décode dans un WireMessage, et le round-trip encode→décode redonne la même valeur",
    arguments: fixtureURLs)
  func decodesAndRoundTrips(url: URL) throws {
    let data = try Data(contentsOf: url)
    let decoded = try JSONDecoder().decode(WireMessage.self, from: data)

    let reencoded = try JSONEncoder().encode(decoded)
    let redecoded = try JSONDecoder().decode(WireMessage.self, from: reencoded)
    #expect(redecoded == decoded)
  }

  @Test("Les huit cas de Kind ont chacun leur fixture")
  func coversEveryKind() {
    let names = Set(Self.fixtureURLs.map { $0.deletingPathExtension().lastPathComponent })
    #expect(
      names == [
        "hello", "welcome", "events", "matchChanged", "proposal", "rejection", "heartbeat",
        "goodbye",
      ])
  }
}
