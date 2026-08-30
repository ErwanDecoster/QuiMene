import DesignSystem
import Store
import SwiftData
import SwiftUI

/// Doc 06 « Statistiques de groupe » (roadmap « après la v1 ») — qui est le/la meilleur(e) à ce
/// jeu, tous joueurs confondus. Calculé à la demande comme le reste des statistiques (doc 06
/// « Performance »).
struct GameLeaderboardView: View {
    let gameID: String
    let gameName: String

    @Environment(\.modelContext) private var modelContext
    @Environment(DeepLinkRouter.self) private var deepLinkRouter
    @State private var entries: [LeaderboardEntry] = []

    var body: some View {
        Group {
            if entries.isEmpty {
                EmptyState(
                    icon: "trophy",
                    message: "Aucune partie de \(gameName) terminée pour l'instant. Le classement se remplit après la première partie."
                )
            } else {
                List {
                    ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                        row(rank: index + 1, entry: entry)
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle(gameName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Historique", systemImage: "clock.arrow.circlepath") {
                    deepLinkRouter.pendingHistoryGameID = gameID
                }
            }
        }
        .task(id: gameID) { load() }
    }

    private func load() {
        entries = (try? LeaderboardRepository(context: modelContext).leaderboard(for: gameID)) ?? []
    }

    private func row(rank: Int, entry: LeaderboardEntry) -> some View {
        HStack(spacing: Space.md) {
            Text("\(rank)")
                .font(.h5)
                .foregroundStyle(rank == 1 ? .brandBrass : .textSecondary)
                .frame(width: 28)
            AvatarView(avatar: entry.avatar, size: .medium)
            VStack(alignment: .leading, spacing: Space.xxs) {
                Text(entry.name).font(.h6).foregroundStyle(.textPrimary)
                Text("\(entry.played) partie(s) · \(entry.wins) victoire(s)")
                    .font(.bodySmall)
                    .foregroundStyle(.textSecondary)
            }
            Spacer(minLength: 0)
            Text(entry.winRate.formatted(.percent.precision(.fractionLength(0))))
                .font(.h6)
                .foregroundStyle(rank == 1 ? .brandBrass : .textPrimary)
        }
        .padding(.vertical, Space.xs)
    }
}
