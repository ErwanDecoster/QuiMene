import Domain
import Foundation
import Testing

@testable import Catalog

/// Doc 10 « Invariants et tests de propriété » — « Somme des scores d'une donne = 0 » (Tarot),
/// testée ici comme promis par le tableau des invariants, jamais par une assertion à l'exécution
/// dans `score()` (calcul entier déterministe, une `precondition` n'y ajouterait rien). Complète
/// aussi les golden Tarot avec des cas trop fins pour y tenir (bornes de multiplicateur, chelem).
@Suite("TarotRulesV1")
struct TarotRulesV1Tests {
  private func makeDefinition() -> GameDefinition {
    GameDefinition(
      id: "tarot-test",
      specVersion: 1,
      rulesVersion: 1,
      name: .init(fr: "Test"),
      symbol: "circle",
      players: .init(min: 3, max: 5),
      scoring: .init(direction: .highestWins, entry: .init(kind: .structured, min: 0, max: 91)),
      engine: TarotRulesV1.engineID,
      end: .init(conditions: [.init(type: .roundLimit, value: 100)]),
      tieBreak: [.shared]
    )
  }

  private func makeParticipants(_ count: Int) -> [Participant] {
    (0..<count).map { Participant(displayName: "J\($0)", seatIndex: $0) }
  }

  private func makeState(_ participants: [Participant]) -> MatchState {
    MatchState(
      matchID: UUID(), gameID: "tarot-test", rulesVersion: 1,
      variants: VariantSelection(), participants: participants)
  }

  private func handDetail(contract: Int, bouts: Int, poignee: Int) -> ScoreDetail {
    ScoreDetail(
      payload: try! JSONEncoder().encode(
        TarotHandDetail(contract: contract, bouts: bouts, poignee: poignee)))
  }

  @Test("La somme des scores d'une donne est toujours nulle", arguments: 0..<50)
  func scoreSumsToZero(seed: Int) {
    var generator = SeededGenerator(seed: seed)
    let playerCount = [3, 4, 5].randomElement(using: &generator)!
    let participants = makeParticipants(playerCount)
    let definition = makeDefinition()
    let rules = TarotRulesV1()
    let state = makeState(participants)

    let taker = participants.randomElement(using: &generator)!
    let others = participants.filter { $0.id != taker.id }
    let partner: Participant? = playerCount == 5 ? others.randomElement(using: &generator) : nil

    var modifiers: Set<ModifierID> = ["isTaker"]
    if Bool.random(using: &generator) { modifiers.insert("petitAuBout") }
    if Bool.random(using: &generator) { modifiers.insert("chelemAnnounced") }
    if Bool.random(using: &generator) { modifiers.insert("chelemAchieved") }

    var inputs = [
      ScoreInput(
        participantID: taker.id,
        rawValue: Int.random(in: 0...91, using: &generator),
        detail: handDetail(
          contract: Int.random(in: 0...3, using: &generator),
          bouts: Int.random(in: 0...3, using: &generator),
          poignee: Int.random(in: 0...3, using: &generator)),
        modifiers: modifiers)
    ]
    if let partner {
      inputs.append(ScoreInput(participantID: partner.id, rawValue: 0, modifiers: ["isPartner"]))
    }

    let draft = RoundDraft(index: 0, inputs: inputs)
    let entries = rules.score(draft, in: state, definition: definition)

    #expect(entries.count == playerCount)
    #expect(entries.reduce(0) { $0 + $1.computedValue } == 0)
  }

  @Test("Donne passée : tous les scores restent à zéro")
  func passedHandScoresZero() {
    let participants = makeParticipants(4)
    let definition = makeDefinition()
    let rules = TarotRulesV1()
    let state = makeState(participants)

    let draft = RoundDraft(
      index: 0,
      inputs: [ScoreInput(participantID: participants[0].id, rawValue: 0, modifiers: ["passed"])])
    let entries = rules.score(draft, in: state, definition: definition)

    #expect(entries.count == 4)
    #expect(entries.allSatisfy { $0.computedValue == 0 })
  }

  @Test("Garde sans le chien (×4) contre Garde contre le chien (×6), à écart identique")
  func guardMultiplierBoundary() {
    let participants = makeParticipants(4)
    let definition = makeDefinition()
    let rules = TarotRulesV1()
    let state = makeState(participants)

    // bouts: 0 ⇒ requis 56 ; points 70 ⇒ écart +14 ⇒ base = 25 + 14 = 39.
    func score(contract: Int) -> [ScoreEntry] {
      let draft = RoundDraft(
        index: 0,
        inputs: [
          ScoreInput(
            participantID: participants[0].id, rawValue: 70,
            detail: handDetail(contract: contract, bouts: 0, poignee: 0),
            modifiers: ["isTaker"])
        ])
      return rules.score(draft, in: state, definition: definition)
    }

    // Garde sans le chien : magnitude = 39 × 4 = 156, preneur (4 joueurs) = 3 × 156 = 468.
    let sansLeChien = score(contract: 2)
    #expect(sansLeChien.first { $0.participantID == participants[0].id }?.computedValue == 468)
    #expect(sansLeChien.first { $0.participantID == participants[1].id }?.computedValue == -156)

    // Garde contre le chien : magnitude = 39 × 6 = 234, preneur = 3 × 234 = 702.
    let contreLeChien = score(contract: 3)
    #expect(contreLeChien.first { $0.participantID == participants[0].id }?.computedValue == 702)
    #expect(contreLeChien.first { $0.participantID == participants[1].id }?.computedValue == -234)
  }

  @Test("Chelem : annoncé et réalisé, réalisé seul, annoncé et raté, ni l'un ni l'autre")
  func chelemBonusCombinations() {
    let participants = makeParticipants(4)
    let definition = makeDefinition()
    let rules = TarotRulesV1()
    let state = makeState(participants)

    // bouts: 0 ⇒ requis 56 ; points 70 ⇒ écart +14 ; contrat Petite (×1) ⇒ base × mult = 39.
    func takerScore(announced: Bool, achieved: Bool) -> Int {
      var modifiers: Set<ModifierID> = ["isTaker"]
      if announced { modifiers.insert("chelemAnnounced") }
      if achieved { modifiers.insert("chelemAchieved") }
      let draft = RoundDraft(
        index: 0,
        inputs: [
          ScoreInput(
            participantID: participants[0].id, rawValue: 70,
            detail: handDetail(contract: 0, bouts: 0, poignee: 0),
            modifiers: modifiers)
        ])
      return rules.score(draft, in: state, definition: definition)
        .first { $0.participantID == participants[0].id }!.computedValue
    }

    // magnitude = 39 + bonus, preneur (4 joueurs) = 3 × magnitude.
    #expect(takerScore(announced: true, achieved: true) == 3 * (39 + 400))
    #expect(takerScore(announced: false, achieved: true) == 3 * (39 + 200))
    #expect(takerScore(announced: true, achieved: false) == 3 * (39 - 200))
    #expect(takerScore(announced: false, achieved: false) == 3 * 39)
  }

  @Test("Distribution à 3 joueurs : contrat chuté, preneur seul contre 2 défenseurs")
  func threePlayerDistributionOnFailedContract() {
    let participants = makeParticipants(3)
    let definition = makeDefinition()
    let rules = TarotRulesV1()
    let state = makeState(participants)

    // bouts: 2 ⇒ requis 41 ; points 30 ⇒ écart −11 (chuté) ⇒ base = 25 + 11 = 36, contrat
    // Petite (×1) ⇒ magnitude 36, signé négatif (écart < 0) ⇒ S = −36.
    let draft = RoundDraft(
      index: 0,
      inputs: [
        ScoreInput(
          participantID: participants[0].id, rawValue: 30,
          detail: handDetail(contract: 0, bouts: 2, poignee: 0),
          modifiers: ["isTaker"])
      ])
    let entries = rules.score(draft, in: state, definition: definition)

    #expect(entries.first { $0.participantID == participants[0].id }?.computedValue == -72)
    #expect(entries.first { $0.participantID == participants[1].id }?.computedValue == 36)
    #expect(entries.first { $0.participantID == participants[2].id }?.computedValue == 36)
  }

  @Test("Distribution à 5 joueurs : preneur ×2S, partenaire +S, chaque défenseur −S")
  func fivePlayerDistributionWithPartner() {
    let participants = makeParticipants(5)
    let definition = makeDefinition()
    let rules = TarotRulesV1()
    let state = makeState(participants)

    // bouts: 1 ⇒ requis 51 ; points 60 ⇒ écart +9 ⇒ base = 34, contrat Garde (×2) ⇒ S = 68.
    let draft = RoundDraft(
      index: 0,
      inputs: [
        ScoreInput(
          participantID: participants[0].id, rawValue: 60,
          detail: handDetail(contract: 1, bouts: 1, poignee: 0),
          modifiers: ["isTaker"]),
        ScoreInput(participantID: participants[1].id, rawValue: 0, modifiers: ["isPartner"]),
      ])
    let entries = rules.score(draft, in: state, definition: definition)

    #expect(entries.first { $0.participantID == participants[0].id }?.computedValue == 136)
    #expect(entries.first { $0.participantID == participants[1].id }?.computedValue == 68)
    for defender in participants[2...] {
      #expect(entries.first { $0.participantID == defender.id }?.computedValue == -68)
    }
  }
}
