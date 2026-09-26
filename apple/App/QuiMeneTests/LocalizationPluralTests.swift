import Foundation
import Testing

/// Pluriels du catalogue (`Localizable.xcstrings`) : un nombre et son nom s'accordent dans chaque
/// langue, y compris quand une phrase en accorde plusieurs (substitutions `%#@…@`).
@Suite("Pluriels du catalogue")
struct LocalizationPluralTests {
  /// Le texte tel que l'app l'afficherait dans `language`, catalogue compilé de l'app compris.
  private func text(_ value: String.LocalizationValue, in language: String) throws -> String {
    let path = try #require(Bundle.main.path(forResource: language, ofType: "lproj"))
    let bundle = try #require(Bundle(path: path))
    return String(localized: value, bundle: bundle, locale: Locale(identifier: language))
  }

  @Test func frenchSingularCoversZeroAndOne() throws {
    #expect(try text("\(0) points", in: "fr") == "0 point")
    #expect(try text("\(1) points", in: "fr") == "1 point")
    #expect(try text("\(2) points", in: "fr") == "2 points")
  }

  @Test func englishSingularIsOnlyOne() throws {
    #expect(try text("\(1) points", in: "en") == "1 point")
    #expect(try text("\(0) points", in: "en") == "0 points")
  }

  @Test func pluralAfterAnotherArgument() throws {
    let name = "Alice"
    #expect(
      try text("\(name) — \(1) manche(s) d'affilée", in: "fr") == "Alice — 1 manche d'affilée")
    #expect(try text("\(name) — \(3) manche(s) d'affilée", in: "en") == "Alice — 3 rounds in a row")
  }

  @Test func eachNumberAgreesInACompositeSentence() throws {
    #expect(try text("\(1) partie(s) · \(3) victoire(s)", in: "fr") == "1 partie · 3 victoires")
    #expect(try text("\(2) partie(s) · \(1) victoire(s)", in: "de") == "2 Spiele · 1 Sieg")
  }
}
