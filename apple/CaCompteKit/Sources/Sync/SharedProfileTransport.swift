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
    client = SupabaseClient(
      supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
  }

  /// Upsert sur `(match_id, shared_profile_id)` (côté SQL) plutôt qu'un simple `insert` : un push
  /// retenté après une réponse perdue (mais un succès côté serveur) ne doit jamais dupliquer la
  /// ligne — l'appelant (`SharedProfileSyncCoordinator`) ne sait retenter qu'en entier, pas
  /// distinguer « jamais reçu » de « reçu mais confirmation perdue ».
  public func push(_ rows: [SharedMatchSummaryRow]) async throws {
    guard !rows.isEmpty else { return }
    try await client.rpc("cacompte_push_shared_match_summaries", params: PushParams(rows: rows))
      .execute()
  }

  public func fetchPending(for sharedProfileIDs: [UUID]) async throws -> [SharedMatchSummaryRow] {
    guard !sharedProfileIDs.isEmpty else { return [] }
    return try await client.rpc(
      "cacompte_fetch_shared_match_summaries",
      params: FetchParams(sharedProfileIDs: sharedProfileIDs)
    )
    .execute()
    .value
  }

  public func delete(matchID: UUID, sharedProfileID: UUID) async throws {
    try await client.rpc(
      "cacompte_delete_shared_match_summary",
      params: DeleteParams(matchID: matchID, sharedProfileID: sharedProfileID)
    )
    .execute()
  }
}

// Doc utilisateur — la table n'est plus accessible directement (aucune policy `anon`, voir la
// migration `secure_cacompte_shared_match_summaries`) : seules ces trois fonctions, qui exigent
// l'identifiant partagé, y donnent accès. Les clés encodées sont les noms des paramètres SQL.
private struct PushParams: Encodable {
  let rows: [SharedMatchSummaryRow]

  enum CodingKeys: String, CodingKey {
    case rows = "p_rows"
  }
}

private struct FetchParams: Encodable {
  let sharedProfileIDs: [UUID]

  enum CodingKeys: String, CodingKey {
    case sharedProfileIDs = "p_shared_profile_ids"
  }
}

private struct DeleteParams: Encodable {
  let matchID: UUID
  let sharedProfileID: UUID

  enum CodingKeys: String, CodingKey {
    case matchID = "p_match_id"
    case sharedProfileID = "p_shared_profile_id"
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
