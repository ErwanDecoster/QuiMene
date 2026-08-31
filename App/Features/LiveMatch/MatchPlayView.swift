import Catalog
import DesignSystem
import Domain
import Store
import SwiftData
import SwiftUI

/// Point d'aiguillage unique entre les écrans de saisie : `categorySheet` (Yams) a besoin d'une
/// grille dédiée, tout le reste réutilise le pavé numérique de `LiveMatchView`. Centralisé ici
/// pour qu'un futur jeu `structured`/`rank` n'ait qu'un cas à ajouter, à un seul endroit.
///
/// Doc utilisateur (audit qualité, [15](../../../docs/15-plan-qualite-code.md)) — le `default:`
/// d'origine routait aussi bien les jeux à pavé numérique que le cas où `definition` est `nil`
/// (version de règles disparue du catalogue après une mise à jour de l'app) vers `LiveMatchView`,
/// qui plantait alors sur son propre `try!` en reconstruisant le même lookup. Le switch est
/// maintenant exhaustif sur `EntryKind` : le cas `nil` a sa propre branche, qui n'ouvre aucun
/// écran de saisie plutôt que de crasher.
struct MatchPlayView: View {
  let match: MatchRecord
  let context: ModelContext
  let catalog: GameCatalog

  var body: some View {
    let definition = try? catalog.definition(for: match.gameID, version: match.rulesVersion)
    Group {
      switch definition?.scoring.entry.kind {
      case .categorySheet:
        YamsSheetView(match: match, context: context, catalog: catalog)
      case .structured:
        BeloteRoundView(match: match, context: context, catalog: catalog)
      case .integer, .rank, .predictionAndResult:
        LiveMatchView(match: match, context: context, catalog: catalog)
      case nil:
        unavailable
      }
    }
    .userActivity(MatchContinuation.activityType) { activity in
      MatchContinuation.configure(activity, for: match, gameName: definition?.name.fr)
    }
  }

  private var unavailable: some View {
    EmptyState(
      icon: "exclamationmark.triangle",
      message: "Cette partie n'est plus disponible — les règles de ce jeu ont changé depuis."
    )
  }
}
