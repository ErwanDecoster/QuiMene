import Domain
import Foundation

/// Ce que l'écran de résultats affiche pour un fait marquant.
struct InsightPresentation {
  let headline: LocalizedStringResource
  let detail: String
  let symbol: String
}

extension Insight {
  /// Titre, texte et symbole rédigés ici à partir de `id` et `value` : `StatsEngine` (`Domain`)
  /// ne produit que des valeurs (doc 06), l'app les met en mots dans la langue de
  /// l'utilisateur. `nil` pour un fait que cette version de l'app ne sait pas présenter.
  /// Miroir de `InsightText.kt` côté Android.
  func presentation(in state: MatchState) -> InsightPresentation? {
    switch (id, value) {
    case (.highestRoundScore, .single(let participantID, let value, let round)):
      InsightPresentation(
        headline: "Plus gros tour",
        detail: roundScoreDetail(state, participantID, value, round),
        symbol: "flame.fill")
    case (.bestRoundScore, .single(let participantID, let value, let round)):
      InsightPresentation(
        headline: "Meilleur tour",
        detail: roundScoreDetail(state, participantID, value, round),
        symbol: "star.fill")
    case (.mostRegular, .single(let participantID, let value, _)):
      InsightPresentation(
        headline: "Le Métronome",
        detail: deviationDetail(state, participantID, value),
        symbol: "metronome")
    case (.mostIrregular, .single(let participantID, let value, _)):
      InsightPresentation(
        headline: "Les montagnes russes",
        detail: deviationDetail(state, participantID, value),
        symbol: "chart.line.uptrend.xyaxis")
    case (.finalGap, .single(_, let gap, _)):
      InsightPresentation(
        headline: "Écart final",
        detail: String(localized: "\(Int(gap)) points entre le premier et le deuxième"),
        symbol: "arrow.left.and.right")
    case (.leadChanges, .single(_, let changes, _)):
      InsightPresentation(
        headline: "Changements de tête",
        detail: changes == 0
          ? String(localized: "Domination du début à la fin")
          : String(localized: "\(Int(changes)) changement(s) de leader"),
        symbol: "arrow.left.arrow.right")
    case (.longestLeadStreak, .single(let participantID, let streak, _)):
      InsightPresentation(
        headline: "Plus longue série en tête",
        detail: String(
          localized: "\(name(of: participantID, in: state)) — \(Int(streak)) manche(s) d'affilée"),
        symbol: "crown.fill")
    case (.roundsClosed, .perParticipant):
      InsightPresentation(
        headline: "Manches fermées",
        detail: String(localized: "Répartition des fermetures de manche"),
        symbol: "lock.fill")
    case (.doublingsSuffered, .perParticipant):
      InsightPresentation(
        headline: "Doublements subis",
        detail: String(localized: "Répartition des scores doublés"),
        symbol: "multiply.circle.fill")
    default:
      nil
    }
  }

  private func roundScoreDetail(
    _ state: MatchState, _ participantID: Participant.ID?, _ value: Double, _ round: Int?
  ) -> String {
    String(
      localized:
        "\(name(of: participantID, in: state)) — \(Int(value)) points, manche \((round ?? 0) + 1)")
  }

  private func deviationDetail(
    _ state: MatchState, _ participantID: Participant.ID?, _ value: Double
  )
    -> String
  {
    let deviation = value.formatted(.number.precision(.fractionLength(2)))
    return String(localized: "\(name(of: participantID, in: state)) — écart-type \(deviation)")
  }

  private func name(of participantID: Participant.ID?, in state: MatchState) -> String {
    state.participants.first { $0.id == participantID }?.displayName ?? "?"
  }
}
