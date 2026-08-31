import Catalog
import DesignSystem
import Domain
import Store
import SwiftUI

/// Doc 14 « Profils partagés », phase 2 — le détail d'une partie reçue de l'installation d'un
/// ami. Seul le classement final est connu (un résumé, pas le journal d'événements complet —
/// voir `MatchRecord.isImportedSummary`) : pas de courbe d'évolution ni de manche par manche ici,
/// contrairement à `ResultsView`.
struct ReceivedMatchDetailView: View {
  let match: MatchRecord
  let catalog: GameCatalog

  private var gameName: String {
    (try? catalog.definition(for: match.gameID, version: match.rulesVersion))?.name.fr
      ?? match.gameID
  }

  private var sortedParticipants: [ParticipantRecord] {
    match.participants.sorted { ($0.finalRank ?? .max) < ($1.finalRank ?? .max) }
  }

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: Space.xl) {
        VStack(alignment: .leading, spacing: Space.xxs) {
          Text(gameName).font(.h2).foregroundStyle(.textPrimary)
          Text(match.startedAt.formatted(date: .abbreviated, time: .omitted))
            .font(.bodySmall)
            .foregroundStyle(.textSecondary)
        }

        VStack(spacing: Space.md) {
          ForEach(sortedParticipants, id: \.id) { participant in
            HStack(spacing: Space.md) {
              Text(participant.finalRank.map { "\($0)" } ?? "—")
                .font(.h3)
                .foregroundStyle(participant.finalRank == 1 ? .brandBrass : .textSecondary)
                .frame(width: 32)
              AvatarView(avatar: participant.avatar, size: .medium)
              Text(participant.nicknameSnapshot).font(.h5).foregroundStyle(.textPrimary)
              Spacer()
              Text((participant.finalScore ?? 0).formatted())
                .font(.scoreXL)
                .foregroundStyle(participant.finalRank == 1 ? .brandBrass : .textSecondary)
            }
            .padding(Space.md)
            .background(
              participant.finalRank == 1 ? Color.brandBrass.opacity(0.08) : Color.neutralSurface,
              in: .rect(cornerRadius: Radius.md)
            )
          }
        }

        Text(
          "Partie reçue de l'appareil d'un ami : le détail manche par manche n'est pas disponible ici."
        )
        .font(.bodySmall)
        .foregroundStyle(.textTertiary)
      }
      .padding(Space.lg)
    }
    .background(.neutralBg)
    .navigationTitle("Résultats")
    .navigationBarTitleDisplayMode(.inline)
  }
}
