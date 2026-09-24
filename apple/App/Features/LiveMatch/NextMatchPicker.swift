import Catalog
import DesignSystem
import Domain
import SwiftUI

/// Doc 16, phase C — « Partie suivante » d'une session en ligne, proposée à tous (créateur et
/// participants) sur l'écran de résultats. Mêmes joueurs ; seuls les jeux à saisie de score
/// simple, les seuls jouables à plusieurs appareils aujourd'hui (même écran de saisie partout),
/// et compatibles avec le nombre de joueurs. Le jeu qui vient de se terminer est proposé en tête.
struct NextMatchPicker: View {
  let playerCount: Int
  let currentGameID: String
  let onPick: (GameDefinition) -> Void

  @Environment(\.dismiss) private var dismiss
  private var catalog: GameCatalog { .embedded }

  static func isShareable(_ definition: GameDefinition) -> Bool {
    guard definition.engine != TarotRulesV1.engineID else { return false }
    switch definition.scoring.entry.kind {
    case .integer, .rank: return true
    default: return false
    }
  }

  private var games: [GameDefinition] {
    catalog.allGames
      .filter {
        Self.isShareable($0) && ($0.players.min...$0.players.max).contains(playerCount)
      }
      .sorted { lhs, rhs in
        if lhs.id == currentGameID { return true }
        if rhs.id == currentGameID { return false }
        return lhs.name.localized.localizedStandardCompare(rhs.name.localized) == .orderedAscending
      }
  }

  var body: some View {
    NavigationStack {
      List {
        Section {
          ForEach(games) { definition in
            Button {
              onPick(definition)
              dismiss()
            } label: {
              HStack(spacing: Space.md) {
                Image(systemName: definition.symbol)
                  .foregroundStyle(.brandInk)
                  .frame(width: 28)
                  .accessibilityHidden(true)
                Text(definition.name.localized)
                  .font(.bodyText)
                  .foregroundStyle(.textPrimary)
                if definition.id == currentGameID {
                  Spacer(minLength: 0)
                  Text("Rejouer")
                    .font(.label)
                    .foregroundStyle(.textSecondary)
                }
              }
            }
          }
        } footer: {
          Text("Avec les mêmes joueurs. Tous les appareils de la session passent à la nouvelle partie.")
        }
      }
      .navigationTitle("Partie suivante")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Annuler") { dismiss() }
        }
      }
    }
  }
}

/// Bouton « Partie suivante » posé sous l'écran de résultats d'une partie partagée.
struct NextMatchBar: View {
  let isBusy: Bool
  let action: () -> Void

  var body: some View {
    Button {
      action()
    } label: {
      if isBusy {
        ProgressView().frame(maxWidth: .infinity)
      } else {
        Text("Partie suivante").frame(maxWidth: .infinity)
      }
    }
    .buttonStyle(.primary(size: .large))
    .disabled(isBusy)
    .padding(.horizontal, Space.lg)
    .padding(.vertical, Space.sm)
    .background(.bar)
  }
}
