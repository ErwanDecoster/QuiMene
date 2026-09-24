import Domain
import Store
import SwiftData
import Testing

@testable import QuiMene

/// Doc 15 « Reste au plan — Phase C » : `MatchSetupModel` porte les règles de sélection des
/// joueurs (effectif min/max, équipes complètes) avant de créer une partie — comportement que le
/// portage Android (doc 11) devra reproduire à l'identique, jamais deviné depuis l'écran seul.
@MainActor
@Suite("MatchSetupModel", .serialized)
struct MatchSetupModelTests {
  /// Doc utilisateur — voir `LiveMatchModelTests.makeModel` : renvoie le `ModelContainer` lui-
  /// même, pas seulement son contexte. SwiftData invalide un `ModelContext` dès que le
  /// conteneur qui le possède est désalloué ; l'appelant doit donc le garder en vie
  /// (`withExtendedLifetime`) pour toute la durée du test. `cloudKitDatabase: .none` explicite,
  /// nécessaire en hébergé (`TEST_HOST`) pour ne pas tenter CloudKit malgré l'entitlement réel
  /// de `QuiMene.app`.
  private func makeContainer() throws -> ModelContainer {
    let schema = Schema(QuiMeneSchemaV1.models)
    let config = ModelConfiguration(
      schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
    return try ModelContainer(for: schema, configurations: [config])
  }

  private func makePlayers(_ names: [String], in context: ModelContext) -> [PlayerRecord] {
    names.enumerated().map { index, name in
      let player = PlayerRecord(
        nickname: name, avatarKind: "symbol", avatarValue: "hare.fill",
        paletteID: String(index + 1))
      context.insert(player)
      return player
    }
  }

  private func makeDefinition(min: Int, max: Int, teamSize: Int? = nil) -> GameDefinition {
    GameDefinition(
      id: "dummy",
      specVersion: 1,
      rulesVersion: 1,
      name: .init(fr: "Test"),
      symbol: "circle",
      players: .init(
        min: min, max: max, teams: teamSize.map { GameDefinition.Players.Teams(size: $0) }),
      scoring: .init(direction: .lowestWins, entry: .init(kind: .integer)),
      engine: "generic.sum.v1",
      end: .init(conditions: [.init(type: .roundLimit, value: 100)])
    )
  }

  @Test("canStart est faux sous le minimum de joueurs, vrai dans les bornes")
  func canStartRespectsPlayerBounds() throws {
    let container = try makeContainer()
    withExtendedLifetime(container) {
      let context = container.mainContext
      let players = makePlayers(["Alice", "Bob", "Chloé"], in: context)
      let model = MatchSetupModel(
        definition: makeDefinition(min: 2, max: 4), availablePlayers: players, context: context)

      model.selectedPlayers = [players[0]]
      #expect(!model.canStart, "un seul joueur, sous le minimum de 2")

      model.selectedPlayers = [players[0], players[1]]
      #expect(model.canStart)
    }
  }

  @Test("toggle ne sélectionne jamais plus que l'effectif maximum du jeu")
  func toggleRespectsMaxPlayers() throws {
    let container = try makeContainer()
    withExtendedLifetime(container) {
      let context = container.mainContext
      let players = makePlayers(["Alice", "Bob", "Chloé"], in: context)
      let model = MatchSetupModel(
        definition: makeDefinition(min: 1, max: 2), availablePlayers: players, context: context)

      model.selectedPlayers = []
      model.toggle(players[0])
      model.toggle(players[1])
      #expect(model.selectedPlayers.count == 2)

      model.toggle(players[2])
      #expect(model.selectedPlayers.count == 2, "le troisième joueur dépasse le maximum de 2")
      #expect(!model.isSelected(players[2]))
    }
  }

  @Test("canStart exige des équipes complètes quand le jeu en déclare")
  func canStartRequiresCompleteTeams() throws {
    let container = try makeContainer()
    withExtendedLifetime(container) {
      let context = container.mainContext
      let players = makePlayers(["Alice", "Bob", "Chloé", "David"], in: context)
      let model = MatchSetupModel(
        definition: makeDefinition(min: 2, max: 4, teamSize: 2), availablePlayers: players,
        context: context)

      model.selectedPlayers = [players[0], players[1], players[2]]
      model.assign(players[0], toTeam: "A")
      model.assign(players[1], toTeam: "A")
      model.assign(players[2], toTeam: "B")
      #expect(!model.canStart, "l'équipe B n'a qu'un joueur sur deux, alors que teams.size == 2")

      model.toggle(players[3])
      #expect(
        model.teamsAreValid,
        "quatre joueurs sélectionnés : la réassignation alternée automatique de toggle() produit deux équipes complètes"
      )
    }
  }

  @Test("start() crée une partie avec exactement les joueurs sélectionnés")
  func startCreatesMatchWithSelectedPlayers() throws {
    let container = try makeContainer()
    try withExtendedLifetime(container) {
      let context = container.mainContext
      let players = makePlayers(["Alice", "Bob"], in: context)
      let model = MatchSetupModel(
        definition: makeDefinition(min: 2, max: 4), availablePlayers: players, context: context)
      model.selectedPlayers = players

      let match = try model.start()

      #expect(match.gameID == "dummy")
      #expect(match.participants.count == 2)
      #expect(Set(match.participants.map(\.nicknameSnapshot)) == ["Alice", "Bob"])
    }
  }
}
