import Foundation
import Testing

/// Doc 16, phase G — fichiers de référence `spec/session/`, lus depuis leur copie embarquée
/// (`SessionResources`, vérifiée identique par `Scripts/check-spec-sync.sh`) : le lanceur de tests
/// du simulateur n'a pas le droit de lire `~/Documents`, où vit le dépôt.
enum SessionFixture {
  static func load(_ name: String) throws -> [String: Any] {
    let url = try #require(
      Bundle.module.url(forResource: name, withExtension: nil, subdirectory: "SessionResources"),
      "\(name) absent de SessionResources : copier spec/session/ (Scripts/check-spec-sync.sh)")
    return try #require(
      JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
  }

  /// Destination d'une régénération (`QUIMENE_WRITE_SPEC=1`) : `spec/session/`, à recopier
  /// ensuite dans `SessionResources`.
  static func specURL(_ name: String, file: String = #filePath) -> URL {
    URL(fileURLWithPath: file)
      .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
      .deletingLastPathComponent().deletingLastPathComponent()
      .appendingPathComponent("spec/session/\(name)")
  }
}
