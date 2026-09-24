import Catalog
import Domain
import Store
import SwiftData
import Testing

@testable import QuiMene

/// Doc 15 « Reste au plan — Phase C » : `LiveMatchModel` n'avait aucun test avant celui-ci, alors
/// que c'est le flux qui porte `commitRound`/`undoLastRound` — exactement le comportement qu'un
/// `ViewModel` Kotlin devra reproduire au portage Android (doc 11). Mêmes réglages SwiftData en
/// mémoire que `QuiMeneKit/Tests/StoreTests` (`MatchRepositoryTests`), même catalogue de test
/// minimal (`generic.sum.v1` implicite via les valeurs par défaut de `GameRules`).
@MainActor
@Suite("LiveMatchModel", .serialized)
struct LiveMatchModelTests {
  private struct DummyRules: GameRules {
    static let engineID = "test.dummy.v1"
  }

  /// `roundLimit` déclenche la fin de partie générique (`GameRules.endCheck`, doc 04) ;
  /// `scoreBounds` borne les scores acceptés (`GameRules.validate`) pour pouvoir déclencher un
  /// rejet de manche invalide sans écrire de moteur dédié.
  private func makeCatalog(roundLimit: Int, scoreBounds: (min: Int, max: Int) = (0, 20))
    -> GameCatalog
  {
    let definition = GameDefinition(
      id: "dummy",
      specVersion: 1,
      rulesVersion: 1,
      name: .init(fr: "Test"),
      symbol: "circle",
      players: .init(min: 2, max: 8),
      scoring: .init(
        direction: .lowestWins,
        entry: .init(kind: .integer, min: scoreBounds.min, max: scoreBounds.max)),
      engine: DummyRules.engineID,
      end: .init(conditions: [.init(type: .roundLimit, value: roundLimit)]),
      tieBreak: [.shared]
    )
    return try! GameCatalog(
      definitions: [definition], engineTable: [DummyRules.engineID: { DummyRules() }])
  }

  /// Crée une partie à deux joueurs et le modèle qui la porte — la mise en place commune à
  /// chaque test ci-dessous. Renvoie aussi le `ModelContainer` : SwiftData invalide son
  /// `ModelContext` dès que le conteneur qui le possède est désalloué (« This model instance was
  /// destroyed by calling ModelContext.reset »), donc l'appelant doit le garder en vie (`let`
  /// local non désossé) pour toute la durée du test — même convention que
  /// `QuiMeneKit/Tests/StoreTests`, où le conteneur reste une variable du corps du test, jamais
  /// d'une fonction utilitaire qui retournerait seulement son contexte.
  private func makeModel(roundLimit: Int, scoreBounds: (min: Int, max: Int) = (0, 20)) throws -> (
    container: ModelContainer, model: LiveMatchModel, ids: [Participant.ID]
  ) {
    let schema = Schema(QuiMeneSchemaV1.models)
    // Doc utilisateur — `cloudKitDatabase: .none` explicite : ce test tourne hébergé dans
    // `QuiMene.app` (`TEST_HOST`), qui porte l'entitlement iCloud réel. Sans ce réglage
    // explicite, `.automatic` (par défaut) tente quand même CloudKit dans ce process précis —
    // absent d'un exécutable de test non hébergé comme `QuiMeneKit/Tests/StoreTests`.
    let config = ModelConfiguration(
      schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
    let container = try ModelContainer(for: schema, configurations: [config])
    let catalog = makeCatalog(roundLimit: roundLimit, scoreBounds: scoreBounds)
    let repository = MatchRepository(context: container.mainContext)
    let match = try repository.createMatch(
      gameID: "dummy", rulesVersion: 1, variants: VariantSelection(),
      seeds: [
        MatchRepository.ParticipantSeed(
          player: nil, nickname: "Alice", avatarKind: "symbol", avatarValue: "hare.fill",
          paletteID: "1"),
        MatchRepository.ParticipantSeed(
          player: nil, nickname: "Bob", avatarKind: "symbol", avatarValue: "tortoise.fill",
          paletteID: "2"),
      ])
    let model = try LiveMatchModel(match: match, context: container.mainContext, catalog: catalog)
    return (container, model, model.participants.map(\.id))
  }

  @Test("commitRound valide une manche, l'ajoute au journal et réinitialise la saisie en cours")
  func commitRoundAddsRoundAndResetsDraft() throws {
    let (container, model, ids) = try makeModel(roundLimit: 10)
    withExtendedLifetime(container) {
      model.setScore(5, for: ids[0])
      model.setScore(9, for: ids[1])
      let committed = model.commitRound()

      #expect(committed)
      #expect(model.state.rounds.count == 1)
      #expect(model.pendingScores.isEmpty)
      #expect(model.validationErrorMessage == nil)
      #expect(model.totals[ids[0]] == 5)
      #expect(model.totals[ids[1]] == 9)
    }
  }

  @Test(
    "Une manche hors bornes est rejetée : le journal ne bouge pas, un message d'erreur est posé")
  func commitRoundRejectsOutOfBoundsScore() throws {
    let (container, model, ids) = try makeModel(roundLimit: 10, scoreBounds: (0, 20))
    withExtendedLifetime(container) {
      model.setScore(99, for: ids[0])
      model.setScore(5, for: ids[1])
      let committed = model.commitRound()

      #expect(!committed)
      #expect(model.state.rounds.isEmpty)
      #expect(model.validationErrorMessage != nil)
    }
  }

  @Test("undoLastRound retire la dernière manche validée")
  func undoLastRoundRemovesLastRound() throws {
    let (container, model, ids) = try makeModel(roundLimit: 10)
    withExtendedLifetime(container) {
      model.setScore(3, for: ids[0])
      model.setScore(4, for: ids[1])
      #expect(model.commitRound())
      #expect(model.state.rounds.count == 1)

      model.undoLastRound()

      #expect(model.state.rounds.isEmpty)
      #expect(model.totals[ids[0]] == 0)
    }
  }

  @Test("La partie passe à .ended dès que la limite de manches est atteinte")
  func matchEndsWhenRoundLimitReached() throws {
    let (container, model, ids) = try makeModel(roundLimit: 1)
    withExtendedLifetime(container) {
      #expect(!model.isConcluded)

      model.setScore(5, for: ids[0])
      model.setScore(9, for: ids[1])
      #expect(model.commitRound())

      #expect(model.state.status == .ended)
      #expect(model.isConcluded)
      #expect(model.canEndManually)
    }
  }
}
