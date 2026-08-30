import Domain
import Foundation
import Store
import SwiftData
import Sync

/// Doc 14 « Profils partagés », phase 2 — pousse le résumé d'une partie tout juste conclue vers
/// chaque participant lié à l'installation d'un ami, et récupère les résumés que d'autres ont
/// poussés vers l'une de mes propres fiches liées. Déclenché par `CaCompteApp` (lancement et
/// retour au premier plan), pas par un minuteur propre — même discipline que
/// `MatchConnectionCoordinator`, plutôt qu'un nouveau système de synchronisation.
@MainActor
final class SharedProfileSyncCoordinator {
    static let shared = SharedProfileSyncCoordinator()

    private let transport = SharedProfileTransport()
    private var isSyncing = false

    private init() {}

    func sync(context: ModelContext) async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }
        await push(context: context)
        await pull(context: context)
    }

    /// Un résumé par partie conclue avec au moins un participant lié — jamais retenté
    /// indéfiniment pour une partie qui n'en a plus (fiche déliée entre-temps, par exemple).
    private func push(context: ModelContext) async {
        let repository = MatchRepository(context: context)
        guard let pendingMatches = try? repository.matchesPendingSharedProfileSync(), !pendingMatches.isEmpty else { return }

        for match in pendingMatches {
            let linkedIDs = Array(Set(match.participants.compactMap { $0.player?.sharedProfileID }))
            let standings = match.participants.compactMap { participant -> SharedMatchSummaryPayload.Entry? in
                guard let rank = participant.finalRank, let score = participant.finalScore else { return nil }
                return SharedMatchSummaryPayload.Entry(
                    sharedProfileID: participant.player?.sharedProfileID,
                    nickname: participant.nicknameSnapshot,
                    avatarKind: participant.avatarKindSnapshot,
                    avatarValue: participant.avatarValueSnapshot,
                    paletteID: participant.paletteIDSnapshot,
                    rank: rank,
                    score: score
                )
            }

            guard !linkedIDs.isEmpty, !standings.isEmpty else {
                try? repository.markSharedProfileSyncComplete(match)
                continue
            }

            let payload = SharedMatchSummaryPayload(
                gameID: match.gameID,
                rulesVersion: match.rulesVersion,
                playedAt: match.endedAt ?? match.startedAt,
                standings: standings
            )
            let rows = linkedIDs.map { SharedMatchSummaryRow(matchID: match.id, sharedProfileID: $0, payload: payload) }

            do {
                try await transport.push(rows)
                try? repository.markSharedProfileSyncComplete(match)
            } catch {
                // Échec silencieux : `pendingSharedProfileSync` reste `true`, nouvelle tentative
                // au prochain retour au premier plan (même patron que `MatchConnectionCoordinator`).
            }
        }
    }

    /// `materializeSharedSummary` est déjà un no-op si cette partie est connue localement (c'est
    /// cet appareil qui l'a jouée et poussée) — reste alors juste à nettoyer la boîte aux lettres.
    private func pull(context: ModelContext) async {
        let playerRepository = PlayerRepository(context: context)
        let matchRepository = MatchRepository(context: context)
        guard let linkedIDs = try? playerRepository.allSharedProfileIDs(), !linkedIDs.isEmpty else { return }
        guard let rows = try? await transport.fetchPending(for: linkedIDs) else { return }

        for row in rows {
            try? matchRepository.materializeSharedSummary(row.payload, matchID: row.matchID)
            try? await transport.delete(matchID: row.matchID, sharedProfileID: row.sharedProfileID)
        }
    }
}
