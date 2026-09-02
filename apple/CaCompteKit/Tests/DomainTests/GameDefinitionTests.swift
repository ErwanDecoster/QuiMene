import Testing

@testable import Domain

@Suite("GameDefinition.LocalizedText — recherche multilingue")
struct GameDefinitionLocalizedTextTests {
  @Test("Trouve une traduction qui n'est pas la langue source")
  func matchesNonSourceTranslation() {
    let text = GameDefinition.LocalizedText(fr: "Yams", en: "Yahtzee")
    #expect(text.matches("Yahtzee"))
    #expect(text.matches("yahtzee"))
    #expect(text.matches("Yams"))
  }

  @Test("Ne trouve rien pour un terme absent de toutes les traductions")
  func doesNotMatchUnrelatedTerm() {
    let text = GameDefinition.LocalizedText(fr: "Yams", en: "Yahtzee")
    #expect(!text.matches("Belote"))
  }

  @Test("Ignore les traductions non renseignées")
  func ignoresMissingTranslations() {
    let text = GameDefinition.LocalizedText(fr: "Belote")
    #expect(text.matches("Belote"))
    #expect(!text.matches("Card"))
  }

  @Test("Les espaces, internes ou en bordure, ne comptent pas")
  func ignoresWhitespace() {
    let text = GameDefinition.LocalizedText(fr: "Petit Bac")
    #expect(text.matches("petitbac"))
    #expect(text.matches("petit  bac"))
    #expect(text.matches(" Petit Bac "))
    #expect(text.matches("p e t i t b a c"))
  }
}
