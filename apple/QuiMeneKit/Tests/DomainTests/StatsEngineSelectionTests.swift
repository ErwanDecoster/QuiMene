import Foundation
import Testing

@testable import Domain

private struct SumRules: GameRules {
  static let engineID = "test.sum.v1"
}

/// Doc 06 « Faits marquants » — la sélection (`StatsEngine.select`), au-delà des candidats que
/// vérifient les golden files.
@Suite("Sélection des faits marquants (doc 06)")
struct StatsEngineSelectionTests {
  @Test("Quand le plus haut score gagne, le même tour n'est raconté qu'une fois")
  func sameRoundToldOnce() {
    let definition = GameDefinition(
      id: "sum",
      specVersion: 1,
      rulesVersion: 1,
      name: .init(fr: "Test"),
      symbol: "circle",
      players: .init(min: 2, max: 8),
      scoring: .init(direction: .highestWins, entry: .init(kind: .integer)),
      engine: SumRules.engineID,
      end: .init(conditions: [.init(type: .roundLimit, value: 100)]),
      tieBreak: [.shared]
    )
    let alice = Participant(displayName: "Alice", seatIndex: 0)
    let bob = Participant(displayName: "Bob", seatIndex: 1)
    let engine = MatchEngine(now: { Date(timeIntervalSince1970: 0) })
    var state = MatchState(
      matchID: UUID(), gameID: "sum", rulesVersion: 1, variants: VariantSelection(),
      participants: [alice, bob])
    for (index, scores) in [(40, 5), (5, 5)].enumerated() {
      let inputs = [
        ScoreInput(participantID: alice.id, rawValue: scores.0),
        ScoreInput(participantID: bob.id, rawValue: scores.1),
      ]
      state = engine.reduce(
        state, .roundCommitted(RoundDraft(index: index, inputs: inputs)), rules: SumRules(),
        definition: definition)
    }

    // Les deux candidats existent (même joueur, même manche, même valeur)…
    let candidates = StatsEngine().candidates(state: state, definition: definition)
    #expect(candidates.contains { $0.id == .highestRoundScore })
    #expect(candidates.contains { $0.id == .bestRoundScore })
    // … mais un seul est retenu.
    let roundFacts = StatsEngine().insights(for: state, definition: definition)
      .filter { $0.id == .highestRoundScore || $0.id == .bestRoundScore }
    #expect(roundFacts.count == 1)
  }
}
