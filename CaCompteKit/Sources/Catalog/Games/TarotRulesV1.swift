import Domain
import Foundation

/// Doc 05 « Tarot » — payload de `ScoreDetail`, opaque au moteur générique et aux autres jeux
/// (même précédent que `YamsCategoryDetail`). `contract`/`bouts`/`poignee` sont des entiers, pas
/// des `flag`/`exclusiveFlag` : un `Set<ModifierID>` (`ScoreInput.modifiers`) ne porte que des
/// étiquettes, jamais de valeur associée — un modifier `counter` existe dans le schéma mais
/// n'est consommé par aucun moteur, ici comme ailleurs.
public struct TarotHandDetail: Sendable, Codable, Equatable {
  /// 0 Petite, 1 Garde, 2 Garde sans le chien, 3 Garde contre le chien.
  public let contract: Int
  /// Nombre de bouts (0 à 3) capturés par le preneur — détermine le seuil requis.
  public let bouts: Int
  /// 0 aucune, 1 simple, 2 double, 3 triple.
  public let poignee: Int

  public init(contract: Int, bouts: Int, poignee: Int) {
    self.contract = contract
    self.bouts = bouts
    self.poignee = poignee
  }
}

/// Doc 05 « Tarot » — le calcul le plus dense du catalogue. Une donne est représentée par 1 ou 2
/// `ScoreInput` (jamais un par participant) : celui du preneur (toujours), et celui du
/// partenaire à 5 joueurs (« roi appelé », révélé à chaque donne — voir doc 11, pas de
/// `players.teams`, qui suppose une partition stable). Les défenseurs ne soumettent jamais rien,
/// leur part est calculée et distribuée par `score()`, même principe que
/// `BeloteRulesV1.teamEntries`.
///
/// La somme des scores d'une donne est nulle par construction (doc 10, invariant testé dans
/// `TarotRulesV1Tests` — jamais dans `score()` elle-même : le calcul est de l'arithmétique
/// entière déterministe, une assertion à l'exécution n'y ajouterait rien).
public struct TarotRulesV1: GameRules {
  public static let engineID = "tarot.v1"

  /// Points requis selon le nombre de bouts capturés par le preneur (doc 05).
  private static let requiredPoints = [56, 51, 41, 36]
  /// Multiplicateur selon le contrat (0 Petite … 3 Garde contre le chien).
  private static let multipliers = [1, 2, 4, 6]
  /// Bonus de poignée (0 aucune … 3 triple).
  private static let poigneeBonuses = [0, 20, 30, 40]

  public init() {}

  public func validate(_ draft: RoundDraft, in state: MatchState, definition: GameDefinition)
    -> ValidationResult
  {
    if draft.inputs.count == 1, draft.inputs[0].modifiers.contains("passed") {
      return .valid
    }

    let takers = draft.inputs.filter { $0.modifiers.contains("isTaker") }
    guard takers.count == 1, let takerInput = takers.first else {
      return .invalid([
        ValidationError(
          field: .general, message: "Une donne doit avoir un preneur, ou être marquée passée.")
      ])
    }

    if state.participants.count == 5 {
      let partners = draft.inputs.filter { $0.modifiers.contains("isPartner") }
      guard partners.count == 1, let partnerInput = partners.first,
        partnerInput.participantID != takerInput.participantID
      else {
        return .invalid([
          ValidationError(
            field: .general, message: "Un partenaire (roi appelé) est requis à 5 joueurs.")
        ])
      }
    }

    guard (0...91).contains(takerInput.rawValue) else {
      return .invalid([
        ValidationError(
          field: .participant(takerInput.participantID), message: "Points invalides (0 à 91).")
      ])
    }

    guard let detail = handDetail(of: takerInput),
      (0...3).contains(detail.contract), (0...3).contains(detail.bouts),
      (0...3).contains(detail.poignee)
    else {
      return .invalid([
        ValidationError(
          field: .participant(takerInput.participantID),
          message: "Contrat, bouts ou poignée invalides.")
      ])
    }

    return .valid
  }

  public func score(_ draft: RoundDraft, in state: MatchState, definition: GameDefinition)
    -> [ScoreEntry]
  {
    if draft.inputs.count == 1, draft.inputs[0].modifiers.contains("passed") {
      return state.participants.map {
        ScoreEntry(participantID: $0.id, rawValue: 0, computedValue: 0)
      }
    }

    guard let takerInput = draft.inputs.first(where: { $0.modifiers.contains("isTaker") }),
      let detail = handDetail(of: takerInput)
    else {
      return []
    }
    let partnerID = draft.inputs.first { $0.modifiers.contains("isPartner") }?.participantID

    let required = Self.requiredPoints[detail.bouts]
    let margin = takerInput.rawValue - required
    let petitAuBout = takerInput.modifiers.contains("petitAuBout")
    let announced = takerInput.modifiers.contains("chelemAnnounced")
    let achieved = takerInput.modifiers.contains("chelemAchieved")

    let base = 25 + abs(margin) + (petitAuBout ? 10 : 0)
    let chelemBonus = achieved ? (announced ? 400 : 200) : (announced ? -200 : 0)
    let magnitude =
      base * Self.multipliers[detail.contract] + Self.poigneeBonuses[detail.poignee]
      + chelemBonus
    let signedScore = margin >= 0 ? magnitude : -magnitude

    var entries: [ScoreEntry] = [
      ScoreEntry(
        participantID: takerInput.participantID, rawValue: takerInput.rawValue,
        computedValue: takerScore(signedScore, playerCount: state.participants.count),
        detail: takerInput.detail, modifiers: takerInput.modifiers)
    ]
    if let partnerID {
      entries.append(
        ScoreEntry(participantID: partnerID, rawValue: 0, computedValue: signedScore))
    }
    for participant in state.participants
    where participant.id != takerInput.participantID && participant.id != partnerID {
      entries.append(
        ScoreEntry(participantID: participant.id, rawValue: 0, computedValue: -signedScore))
    }
    return entries
  }

  /// Doc 05 : preneur seul (3/4 joueurs) → `2S`/`3S` ; preneur avec partenaire (5 joueurs) →
  /// `2S`, le partenaire recevant `S` séparément (voir `score()`).
  private func takerScore(_ signedScore: Int, playerCount: Int) -> Int {
    playerCount == 4 ? signedScore * 3 : signedScore * 2
  }

  public func endCheck(_ state: MatchState, definition: GameDefinition) -> EndCheck {
    let handsPerPlayer = state.variants.int("handsPerPlayer", default: 2)
    let target = state.participants.count * handsPerPlayer
    return state.rounds.count >= target ? .ended(reason: .roundLimit) : .continue
  }

  private func handDetail(of input: ScoreInput) -> TarotHandDetail? {
    guard let payload = input.detail?.payload else { return nil }
    return try? JSONDecoder().decode(TarotHandDetail.self, from: payload)
  }
}
