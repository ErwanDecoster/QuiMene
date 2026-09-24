import Domain
import Foundation

/// Doc 05 « Wizard » — payload de `ScoreDetail` portant l'annonce (`bid`), le résultat réel
/// vivant dans `ScoreInput.rawValue`. Même précédent que `YamsCategoryDetail`/`TarotHandDetail` :
/// un type `Catalog`, construit par la couche UI à la soumission, décodé par le moteur.
public struct WizardBidDetail: Sendable, Codable, Equatable {
  public let bid: Int

  public init(bid: Int) {
    self.bid = bid
  }
}

/// Doc 05 « Wizard » — contrairement à Yams, une manche est simultanée : autant de
/// `ScoreInput` que de participants. `EndCondition.valueExpression` (« 60 / playerCount »)
/// existe dans le schéma mais n'est lu par aucun code de `Domain` (même écart pour tous les
/// jeux) — `endCheck()` calcule donc directement la limite, même précédent que
/// `MolkkyRulesV1`/`YamsRulesV1`, qui ignorent déjà `definition.end.conditions`.
public struct WizardRulesV1: GameRules {
  public static let engineID = "wizard.v1"

  public init() {}

  /// Doc 05 : « la somme des plis réalisés doit égaler le numéro de la manche — contrôle
  /// bloquant ». `MatchEngine.reduce` n'appelle jamais `validate` (seule la couche modèle de
  /// l'app le fait avant `commitRound`) : ce rejet ne peut donc jamais être exercé par un golden
  /// file, uniquement par un test direct sur `validate()` (`WizardRulesV1Tests`).
  public func validate(_ draft: RoundDraft, in state: MatchState, definition: GameDefinition)
    -> ValidationResult
  {
    let roundNumber = state.rounds.count + 1
    for input in draft.inputs {
      guard (0...roundNumber).contains(input.rawValue) else {
        return .invalid([
          ValidationError(
            field: .participant(input.participantID),
            message: "Résultat invalide (0 à \(roundNumber)).")
        ])
      }
      guard let bid = bid(of: input), (0...roundNumber).contains(bid) else {
        return .invalid([
          ValidationError(
            field: .participant(input.participantID),
            message: "Annonce invalide (0 à \(roundNumber)).")
        ])
      }
    }
    let totalTricks = draft.inputs.reduce(0) { $0 + $1.rawValue }
    guard totalTricks == roundNumber else {
      return .invalid([
        ValidationError(
          field: .general,
          message: "Le total des plis réalisés (\(totalTricks)) doit égaler \(roundNumber).")
      ])
    }
    return .valid
  }

  public func score(_ draft: RoundDraft, in state: MatchState, definition: GameDefinition)
    -> [ScoreEntry]
  {
    draft.inputs.map { input in
      let bid = bid(of: input) ?? 0
      let computedValue = bid == input.rawValue ? 20 + 10 * bid : -10 * abs(bid - input.rawValue)
      return ScoreEntry(
        participantID: input.participantID, rawValue: input.rawValue,
        computedValue: computedValue, detail: input.detail, modifiers: input.modifiers)
    }
  }

  public func endCheck(_ state: MatchState, definition: GameDefinition) -> EndCheck {
    let target = 60 / state.participants.count
    return state.rounds.count >= target ? .ended(reason: .roundLimit) : .continue
  }

  private func bid(of input: ScoreInput) -> Int? {
    guard let payload = input.detail?.payload else { return nil }
    return try? JSONDecoder().decode(WizardBidDetail.self, from: payload).bid
  }
}
