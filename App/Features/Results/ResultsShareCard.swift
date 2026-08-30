import DesignSystem
import Domain
import Store
import SwiftUI

/// Doc 06 : image 1080×1350 (4:5), mode clair uniquement — « elle finit dans une conversation
/// dont on ne connaît pas le thème » (charte §8).
struct ResultsShareCard: View {
    let gameName: String
    let standings: [Standing]
    let recordByID: [Participant.ID: ParticipantRecord]
    /// Doc utilisateur — remontée : la carte partagée ne disait presque rien de la partie qui
    /// vient de se jouer. Le badge du podium, le nombre de manches et la date en disent davantage
    /// à qui reçoit l'image sans avoir suivi la partie en direct.
    let badgeByParticipant: [Participant.ID: Badge]
    let roundCount: Int
    let playedAt: Date

    private let pointSize = CGSize(width: 360, height: 450)

    var body: some View {
        VStack(spacing: Space.lg) {
            VStack(spacing: Space.xxs) {
                Text(gameName)
                    .font(.h2)
                    .foregroundStyle(.textPrimary)
                Text("\(roundCount) manche(s) · \(playedAt.formatted(date: .abbreviated, time: .omitted))")
                    .font(.bodySmall)
                    .foregroundStyle(.textSecondary)
            }

            VStack(spacing: Space.md) {
                ForEach(standings.prefix(3), id: \.participantID) { standing in
                    if let record = recordByID[standing.participantID] {
                        HStack(spacing: Space.md) {
                            Text("\(standing.rank)")
                                .font(.h4)
                                .foregroundStyle(standing.rank == 1 ? .brandBrass : .textSecondary)
                            AvatarView(avatar: record.avatar, size: .medium)
                            VStack(alignment: .leading, spacing: Space.xxs) {
                                Text(record.nicknameSnapshot).font(.h5).foregroundStyle(.textPrimary)
                                if let badge = badgeByParticipant[standing.participantID] {
                                    Text(badge.kind.label).font(.label).foregroundStyle(.brandBrass)
                                }
                            }
                            Spacer()
                            Text(standing.score.formatted()).font(.scoreL).foregroundStyle(.textSecondary)
                        }
                    }
                }
                if standings.count > 3 {
                    Text("+ \(standings.count - 3) autre(s) joueur(s)")
                        .font(.bodySmall)
                        .foregroundStyle(.textTertiary)
                }
            }

            Spacer()

            LogoLockupHorizontal(height: 28)
        }
        .padding(Space.xl)
        .frame(width: pointSize.width, height: pointSize.height)
        .background(.neutralSurface)
        .environment(\.colorScheme, .light)
    }
}

extension View {
    /// Rendu résolu au moment de l'appel — acceptable pour un écran de résultats statique
    /// (pas de re-rendu à chaque frame).
    @MainActor
    func renderedImage(scale: CGFloat = 3) -> Image {
        let renderer = ImageRenderer(content: self)
        renderer.scale = scale
        if let uiImage = renderer.uiImage {
            return Image(uiImage: uiImage)
        }
        return Image(systemName: "photo")
    }
}
