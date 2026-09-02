import Domain
import Foundation
import Testing

@testable import Catalog

/// Doc 05 « Wizard » — « la somme des plis réalisés doit égaler le numéro de la manche, contrôle
/// bloquant ». `MatchEngine.reduce` n'appelle jamais `validate` (seule la couche modèle de l'app
/// le fait avant `commitRound`) : ce rejet ne peut donc jamais être exercé par un golden file,
/// uniquement par un test direct sur `validate()`, ici.
@Suite("WizardRulesV1")
struct WizardRulesV1Tests {
  private func makeDefinition() -> GameDefinition {
    GameDefinition(
      id: "wizard-test",
      specVersion: 1,
      rulesVersion: 1,
      name: .init(fr: "Test"),
      symbol: "circle",
      players: .init(min: 3, max: 6),
      scoring: .init(
        direction: .highestWins, entry: .init(kind: .predictionAndResult, min: 0, max: 20)),
      engine: WizardRulesV1.engineID,
      end: .init(conditions: []),
      tieBreak: [.shared]
    )
  }

  private func makeParticipants(_ count: Int) -> [Participant] {
    (0..<count).map { Participant(displayName: "J\($0)", seatIndex: $0) }
  }

  private func bidDetail(_ bid: Int) -> ScoreDetail {
    ScoreDetail(payload: try! JSONEncoder().encode(WizardBidDetail(bid: bid)))
  }

  @Test("Rejette une manche dont la somme des plis réalisés ne correspond pas au numéro de manche")
  func rejectsWhenTricksDoNotSumToRoundNumber() {
    let definition = makeDefinition()
    let rules = WizardRulesV1()
    let participants = makeParticipants(4)
    let state = MatchState(
      matchID: UUID(), gameID: "wizard-test", rulesVersion: 1,
      variants: VariantSelection(), participants: participants)

    // Manche 1 (state.rounds.count == 0 ⇒ numéro de manche == 1) : les 4 joueurs déclarent
    // chacun 1 pli réalisé — la somme (4) ne peut jamais égaler 1, rejet garanti.
    let draft = RoundDraft(
      index: 0,
      inputs: participants.map {
        ScoreInput(participantID: $0.id, rawValue: 1, detail: bidDetail(1))
      })

    guard case .invalid = rules.validate(draft, in: state, definition: definition) else {
      Issue.record("La somme des plis (4) ne doit pas être acceptée pour la manche 1.")
      return
    }
  }

  @Test("Accepte une manche dont la somme des plis réalisés égale le numéro de la manche")
  func acceptsWhenTricksSumMatchesRoundNumber() {
    let definition = makeDefinition()
    let rules = WizardRulesV1()
    let participants = makeParticipants(4)
    let state = MatchState(
      matchID: UUID(), gameID: "wizard-test", rulesVersion: 1,
      variants: VariantSelection(), participants: participants)

    // Manche 1 : un seul pli disponible, un seul joueur le remporte — somme = 1.
    let draft = RoundDraft(
      index: 0,
      inputs: [
        ScoreInput(participantID: participants[0].id, rawValue: 1, detail: bidDetail(1)),
        ScoreInput(participantID: participants[1].id, rawValue: 0, detail: bidDetail(0)),
        ScoreInput(participantID: participants[2].id, rawValue: 0, detail: bidDetail(1)),
        ScoreInput(participantID: participants[3].id, rawValue: 0, detail: bidDetail(0)),
      ])

    guard case .valid = rules.validate(draft, in: state, definition: definition) else {
      Issue.record("Une somme de plis valide (1 == numéro de manche) doit être acceptée.")
      return
    }
  }

  @Test("Score : annonce juste rapporte 20 + 10×annonce, annonce fausse coûte 10×écart")
  func scoreMatchesBidFormula() {
    let definition = makeDefinition()
    let rules = WizardRulesV1()
    let participants = makeParticipants(3)
    let state = MatchState(
      matchID: UUID(), gameID: "wizard-test", rulesVersion: 1,
      variants: VariantSelection(), participants: participants)

    let draft = RoundDraft(
      index: 0,
      inputs: [
        // Annonce 2, réalisé 2 : 20 + 10×2 = 40.
        ScoreInput(participantID: participants[0].id, rawValue: 2, detail: bidDetail(2)),
        // Annonce 3, réalisé 1 : −10×|3−1| = −20.
        ScoreInput(participantID: participants[1].id, rawValue: 1, detail: bidDetail(3)),
        // Annonce 0, réalisé 1 : −10×|0−1| = −10.
        ScoreInput(participantID: participants[2].id, rawValue: 1, detail: bidDetail(0)),
      ])
    let entries = rules.score(draft, in: state, definition: definition)

    #expect(entries.first { $0.participantID == participants[0].id }?.computedValue == 40)
    #expect(entries.first { $0.participantID == participants[1].id }?.computedValue == -20)
    #expect(entries.first { $0.participantID == participants[2].id }?.computedValue == -10)
  }

  @Test("endCheck se termine à 60 / nombre de joueurs manches, jamais avant")
  func endCheckStopsAtSixtyDividedByPlayerCount() {
    let definition = makeDefinition()
    let rules = WizardRulesV1()
    let participants = makeParticipants(6)
    var state = MatchState(
      matchID: UUID(), gameID: "wizard-test", rulesVersion: 1,
      variants: VariantSelection(), participants: participants)
    let engine = MatchEngine(now: { Date(timeIntervalSince1970: 0) })

    // 60 / 6 joueurs = 10 manches.
    for index in 0..<9 {
      let draft = RoundDraft(
        index: index,
        inputs: [
          ScoreInput(participantID: participants[0].id, rawValue: 0, detail: bidDetail(0))
        ]
          + participants.dropFirst().map {
            ScoreInput(participantID: $0.id, rawValue: 0, detail: bidDetail(0))
          })
      state = engine.reduce(state, .roundCommitted(draft), rules: rules, definition: definition)
      #expect(
        state.status == .inProgress, "manche \(index + 1) : la partie ne doit pas être finie")
    }

    let lastDraft = RoundDraft(
      index: 9,
      inputs: participants.map {
        ScoreInput(participantID: $0.id, rawValue: 0, detail: bidDetail(0))
      })
    state = engine.reduce(state, .roundCommitted(lastDraft), rules: rules, definition: definition)
    #expect(state.status == .ended, "10ᵉ manche : 60 / 6 joueurs atteint")
  }
}
