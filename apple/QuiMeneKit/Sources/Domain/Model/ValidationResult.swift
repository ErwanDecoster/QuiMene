/// Résultat de `GameRules.validate` (doc 04). Aucun texte d'interface ici : erreurs et
/// avertissements portent une raison typée, que chaque app rédige dans la langue de
/// l'utilisateur — `Domain` ne dépend d'aucune ressource de traduction (ADR-0002).
public enum ValidationResult: Sendable, Equatable {
  case valid
  case warning([ValidationWarning])
  case invalid([ValidationError])
}

/// Saisie acceptée mais inhabituelle : à signaler, sans empêcher de valider (doc 04).
public enum ValidationWarning: Sendable, Equatable {
  /// Score hors de la plage habituelle du jeu (`warnBelow`/`warnAbove`, extrêmes du Skyjo).
  case unusualScore
}

public struct ValidationError: Sendable, Equatable {
  public enum Field: Sendable, Equatable {
    case participant(Participant.ID)
    case modifier(ModifierID)
    case general
  }

  /// Pourquoi la saisie est refusée.
  public enum Reason: Sendable, Equatable {
    /// Score sous le minimum de la définition du jeu.
    case scoreBelowMinimum(Int)
    /// Score au-dessus du maximum de la définition du jeu.
    case scoreAboveMaximum(Int)
    /// Skyjo : exactement un joueur ferme la manche.
    case singleCloserRequired
    /// Belote : deux équipes par donne.
    case twoTeamsRequired
    /// Belote : une seule équipe preneuse par donne.
    case singleTakingTeamRequired
    /// Belote, Tarot : points du preneur hors de `0...max`.
    case takerPointsOutOfRange(max: Int)
    /// Tarot : un preneur, ou la donne marquée passée.
    case takerRequired
    /// Tarot à 5 : un partenaire (roi appelé), distinct du preneur.
    case partnerRequired
    /// Tarot : contrat, bouts ou poignée hors des valeurs possibles.
    case invalidTarotHand
    /// Wizard : plis réalisés hors de `0...max`.
    case tricksOutOfRange(max: Int)
    /// Wizard : annonce hors de `0...max`.
    case bidOutOfRange(max: Int)
    /// Wizard : le total des plis réalisés doit égaler le numéro de la manche.
    case tricksTotalMismatch(total: Int, expected: Int)
    /// Yams : une seule catégorie remplie par tour.
    case singleCategoryPerTurn
    /// Yams : catégorie absente de la grille.
    case unknownCategory
    /// Yams : catégorie déjà remplie.
    case categoryAlreadyFilled
    /// Yams : nombre de dés hors de `0...5`.
    case invalidDiceCount
    /// Yams : une figure vaut 0 (ratée) ou 1 (réussie).
    case invalidFigureValue
    /// Yams : somme de dés impossible.
    case invalidDiceSum
  }

  public let field: Field
  public let reason: Reason

  public init(field: Field, reason: Reason) {
    self.field = field
    self.reason = reason
  }
}
