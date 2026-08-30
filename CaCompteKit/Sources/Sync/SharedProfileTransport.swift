import Domain
import Foundation
import Supabase

/// Doc 14 « Profils partagés », phase 2 — boîte aux lettres transitoire : un résumé de partie
/// terminée, poussé une fois par participant lié, retiré dès que l'appareil concerné l'a
/// récupéré. Jamais une copie durable côté serveur — même discipline que `cacompte_open_games`
/// (`SupabaseTransport`), juste une fenêtre de purge plus généreuse (un ami peut rester hors
/// ligne des semaines, pas seulement le temps d'une soirée).
public struct SharedProfileTransport: Sendable {
    private let client: SupabaseClient

    public init() {
        client = SupabaseClient(supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
    }

    /// `upsert` sur `(match_id, shared_profile_id)` plutôt qu'un simple `insert` : un push
    /// retenté après une réponse perdue (mais un succès côté serveur) ne doit jamais dupliquer la
    /// ligne — l'appelant (`SharedProfileSyncCoordinator`) ne sait retenter qu'en entier, pas
    /// distinguer « jamais reçu » de « reçu mais confirmation perdue ».
    public func push(_ rows: [SharedMatchSummaryRow]) async throws {
        guard !rows.isEmpty else { return }
        try await client.from("cacompte_shared_match_summaries")
            .upsert(rows, onConflict: "match_id,shared_profile_id")
            .execute()
    }

    public func fetchPending(for sharedProfileIDs: [UUID]) async throws -> [SharedMatchSummaryRow] {
        guard !sharedProfileIDs.isEmpty else { return [] }
        return try await client.from("cacompte_shared_match_summaries")
            .select()
            .in("shared_profile_id", values: sharedProfileIDs.map(\.uuidString))
            .execute()
            .value
    }

    public func delete(matchID: UUID, sharedProfileID: UUID) async throws {
        try await client.from("cacompte_shared_match_summaries")
            .delete()
            .eq("match_id", value: matchID.uuidString)
            .eq("shared_profile_id", value: sharedProfileID.uuidString)
            .execute()
    }
}

public struct SharedMatchSummaryRow: Sendable, Codable {
    public let matchID: UUID
    public let sharedProfileID: UUID
    public let payload: SharedMatchSummaryPayload

    public init(matchID: UUID, sharedProfileID: UUID, payload: SharedMatchSummaryPayload) {
        self.matchID = matchID
        self.sharedProfileID = sharedProfileID
        self.payload = payload
    }

    enum CodingKeys: String, CodingKey {
        case matchID = "match_id"
        case sharedProfileID = "shared_profile_id"
        case payload
    }
}
