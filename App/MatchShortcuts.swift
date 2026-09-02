import AppIntents
import Catalog
import Domain

/// Doc utilisateur — App Intents (roadmap P9) : expose deux actions à Siri, Raccourcis et
/// Spotlight. `openAppWhenRun` fait tourner `perform()` dans le process de l'app plutôt que dans
/// une extension isolée — on peut donc muter `DeepLinkRouter.shared` directement et laisser
/// `GamesTabView` réagir, sur le même principe que `.onOpenURL` et Handoff (`MatchContinuation`).

struct GameEntity: AppEntity {
  let id: String
  let name: String
  let symbol: String

  static let typeDisplayRepresentation: TypeDisplayRepresentation = "Jeu"
  static let defaultQuery = GameEntityQuery()

  var displayRepresentation: DisplayRepresentation {
    DisplayRepresentation(title: "\(name)", image: .init(systemName: symbol))
  }
}

struct GameEntityQuery: EntityStringQuery {
  func entities(for identifiers: [String]) async throws -> [GameEntity] {
    allEntities().filter { identifiers.contains($0.id) }
  }

  func entities(matching string: String) async throws -> [GameEntity] {
    allEntities().filter { $0.name.localizedCaseInsensitiveContains(string) }
  }

  func suggestedEntities() async throws -> [GameEntity] {
    allEntities()
  }

  private func allEntities() -> [GameEntity] {
    GameCatalog.embedded.allGames
      .sorted { $0.name.localized < $1.name.localized }
      .map { GameEntity(id: $0.id, name: $0.name.localized, symbol: $0.symbol) }
  }
}

struct StartMatchIntent: AppIntent {
  static let title: LocalizedStringResource = "Commencer une partie"
  static let openAppWhenRun = true

  @Parameter(title: "Jeu")
  var game: GameEntity

  static var parameterSummary: some ParameterSummary {
    Summary("Commencer une partie de \(\.$game)")
  }

  @MainActor
  func perform() async throws -> some IntentResult {
    DeepLinkRouter.shared.pendingGameID = game.id
    return .result()
  }
}

struct ResumeMatchIntent: AppIntent {
  static let title: LocalizedStringResource = "Reprendre la partie en cours"
  static let openAppWhenRun = true

  @MainActor
  func perform() async throws -> some IntentResult {
    DeepLinkRouter.shared.wantsResume = true
    return .result()
  }
}

struct CaCompteShortcuts: AppShortcutsProvider {
  /// Doc utilisateur (audit qualité, 15) — remontée : « lance une partie de Skyjo sur CaCompte »
  /// (verbe et préposition absents des phrases déclarées, une seule variante par intent au
  /// départ) tombait sur l'intent musique du système au lieu de `StartMatchIntent` — Siri ne
  /// fait pas de correspondance sémantique libre, seulement un rapprochement des phrases
  /// vraiment déclarées ici. Plusieurs verbes/prépositions courants couverts par intent plutôt
  /// qu'une seule formulation.
  static var appShortcuts: [AppShortcut] {
    AppShortcut(
      intent: ResumeMatchIntent(),
      phrases: [
        "Reprends ma partie dans \(.applicationName)",
        "Reprends la partie en cours dans \(.applicationName)",
        "Continue ma partie dans \(.applicationName)",
      ],
      shortTitle: "Reprendre la partie",
      systemImageName: "arrow.clockwise.circle"
    )
    AppShortcut(
      intent: StartMatchIntent(),
      phrases: [
        "Commence une partie de \(\.$game) dans \(.applicationName)",
        "Lance une partie de \(\.$game) dans \(.applicationName)",
        "Lance une partie de \(\.$game) sur \(.applicationName)",
        "Démarre une partie de \(\.$game) dans \(.applicationName)",
      ],
      shortTitle: "Commencer une partie",
      systemImageName: "die.face.5"
    )
  }
}
