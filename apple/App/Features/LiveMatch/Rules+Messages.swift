import Domain
import Foundation

// Textes des règles, rédigés ici plutôt que dans `Domain`/`Catalog` (doc 04, ADR-0002) : les
// moteurs ne produisent qu'une raison typée, l'app la met en mots dans la langue de
// l'utilisateur. Miroir de `RulesMessages.kt` côté Android.

extension ValidationError {
  /// Message affiché quand la saisie est refusée.
  var message: String {
    switch reason {
    case .scoreBelowMinimum(let min):
      String(localized: "Score sous le minimum autorisé (\(min)).")
    case .scoreAboveMaximum(let max):
      String(localized: "Score au-dessus du maximum autorisé (\(max)).")
    case .singleCloserRequired:
      String(localized: "Un seul joueur ferme la manche.")
    case .twoTeamsRequired:
      String(localized: "Deux équipes attendues par donne.")
    case .singleTakingTeamRequired:
      String(localized: "Une seule équipe preneuse par donne.")
    case .takerPointsOutOfRange(let max):
      String(localized: "Points invalides (0 à \(max)).")
    case .takerRequired:
      String(localized: "Une donne doit avoir un preneur, ou être marquée passée.")
    case .partnerRequired:
      String(localized: "Un partenaire (roi appelé) est requis à 5 joueurs.")
    case .invalidTarotHand:
      String(localized: "Contrat, bouts ou poignée invalides.")
    case .tricksOutOfRange(let max):
      String(localized: "Résultat invalide (0 à \(max)).")
    case .bidOutOfRange(let max):
      String(localized: "Annonce invalide (0 à \(max)).")
    case .tricksTotalMismatch(let total, let expected):
      String(localized: "Le total des plis réalisés (\(total)) doit égaler \(expected).")
    case .singleCategoryPerTurn:
      String(localized: "Une seule catégorie est remplie par tour.")
    case .unknownCategory:
      String(localized: "Catégorie inconnue.")
    case .categoryAlreadyFilled:
      String(localized: "Cette catégorie est déjà remplie.")
    case .invalidDiceCount:
      String(localized: "Nombre de dés invalide (0 à 5).")
    case .invalidFigureValue:
      String(localized: "Valeur invalide.")
    case .invalidDiceSum:
      String(localized: "Somme invalide.")
    }
  }
}

extension MatchState {
  /// Explication du dernier score recalculé (doublement du Skyjo, dépassement du Mölkky, bonus
  /// du Yams), à afficher juste après la validation d'une manche.
  var lastRoundExplanationMessage: String? {
    guard let entry = rounds.last?.entries.first(where: { $0.explanation != nil }),
      let explanation = entry.explanation
    else { return nil }
    let name = participants.first { $0.id == entry.participantID }?.displayName ?? ""
    switch explanation {
    case .doubledForClosingWithoutLowest:
      return String(
        localized: "Score doublé : \(name) a fermé la manche sans le score le plus bas.")
    case .bustBackTo25:
      return String(localized: "Dépassement de 50 : retour à 25.")
    case .upperSectionBonus:
      return String(localized: "Bonus de section haute (+35)")
    }
  }
}
