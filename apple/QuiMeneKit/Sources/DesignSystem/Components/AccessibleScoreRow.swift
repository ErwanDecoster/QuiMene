import Foundation
import SwiftUI

/// Doc 08 « Accessibilité » — « chaque ligne du tableau est un seul élément accessible, énoncé
/// « Alice, deuxième, 44 points, +12 cette manche »." Un seul point d'implémentation, appliqué à
/// toute ligne de tableau de scores (`ScoreBoardView`, `YamsSheetView`, `BeloteRoundView`,
/// `TarotRoundView`, `WizardRoundView`, `GameLeaderboardView`, `GamesTabView`) plutôt qu'une
/// combinaison `.accessibilityElement`/`.accessibilityLabel` réécrite à chaque écran.
extension View {
  /// Combine les enfants existants en un seul arrêt VoiceOver et pose le libellé/la valeur
  /// composés — ne change rien à la disposition visuelle de l'appelant.
  public func accessibleScoreRow(name: String, rank: Int? = nil, score: Int, delta: Int? = nil)
    -> some View
  {
    self
      .accessibilityElement(children: .combine)
      .accessibilityLabel(AccessibleScoreRow.label(name: name, rank: rank))
      .accessibilityValue(AccessibleScoreRow.value(score: score, delta: delta))
  }
}

enum AccessibleScoreRow {
  private static let ordinalFormatter: NumberFormatter = {
    let formatter = NumberFormatter()
    formatter.numberStyle = .ordinal
    return formatter
  }()

  static func label(name: String, rank: Int?) -> String {
    guard let rank, let ordinal = ordinalFormatter.string(from: NSNumber(value: rank)) else {
      return name
    }
    return "\(name), \(ordinal)"
  }

  static func value(score: Int, delta: Int?) -> String {
    let scoreText = String(localized: "\(score) points")
    guard let delta else { return scoreText }
    let sign = delta >= 0 ? "+" : ""
    let deltaText = String(localized: "\(sign)\(delta) cette manche")
    return "\(scoreText), \(deltaText)"
  }
}
