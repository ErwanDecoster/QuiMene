import Foundation

/// Pourquoi `computedValue` diffère de la saisie brute. Le texte affiché est rédigé par l'app,
/// dans la langue de l'utilisateur (voir `ValidationResult`).
public enum ScoreExplanation: String, Sendable, Codable, Equatable {
  /// Skyjo : a fermé la manche sans le score le plus bas, score doublé.
  case doubledForClosingWithoutLowest
  /// Mölkky : dépassement de 50, retour à 25.
  case bustBackTo25
  /// Yams : bonus de la section haute (+35).
  case upperSectionBonus
}

/// Résultat après application des règles (doublement Skyjo, bonus Yams…).
public struct ScoreEntry: Sendable, Codable, Equatable {
  public let participantID: Participant.ID
  public let rawValue: Int
  public let computedValue: Int
  public let explanation: ScoreExplanation?
  public let detail: ScoreDetail?
  public let modifiers: Set<ModifierID>

  public init(
    participantID: Participant.ID,
    rawValue: Int,
    computedValue: Int,
    explanation: ScoreExplanation? = nil,
    detail: ScoreDetail? = nil,
    modifiers: Set<ModifierID> = []
  ) {
    self.participantID = participantID
    self.rawValue = rawValue
    self.computedValue = computedValue
    self.explanation = explanation
    self.detail = detail
    self.modifiers = modifiers
  }
}

public struct Round: Sendable, Codable, Equatable {
  public let index: Int
  public let entries: [ScoreEntry]
  public let committedAt: Date
  public let note: String?

  public init(index: Int, entries: [ScoreEntry], committedAt: Date, note: String? = nil) {
    self.index = index
    self.entries = entries
    self.committedAt = committedAt
    self.note = note
  }
}
