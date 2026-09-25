import CryptoKit
import Domain
import Foundation
import Supabase

/// Doc 16, phase E — historique partagé : une partie terminée, **complète** (journal d'événements
/// et fiches de ses joueurs), déposée dans la boîte aux lettres de chaque ami lié qui y a joué. Elle
/// remplace les résumés du doc 14 (classement final seulement, en clair).
///
/// Chiffrement de bout en bout : la boîte d'un profil se désigne par une empreinte de son
/// identifiant partageable (`lookupKey`), et son contenu est scellé avec une clé qui en est dérivée
/// (`key`). Le serveur ne voit ni l'identifiant, ni les joueurs, ni les scores ; seuls ceux qui
/// connaissent l'identifiant — ses amis liés, et lui-même — peuvent déposer ou lire. Le contenu
/// est un `SharedMatchPackage` (Domain).
public enum MailboxCrypto {
  /// Adresse de la boîte d'un profil côté serveur : une empreinte, jamais l'identifiant.
  public static func lookupKey(for profileID: UUID) -> String {
    let digest = SHA256.hash(data: Data("quimene.mailbox.lookup:\(profileID.uuidString)".utf8))
    return digest.map { String(format: "%02x", $0) }.joined()
  }

  /// Clé de la boîte : HKDF-SHA256 de l'identifiant (en majuscules, comme `uuidString`).
  public static func key(for profileID: UUID) -> SymmetricKey {
    HKDF<SHA256>.deriveKey(
      inputKeyMaterial: SymmetricKey(data: Data(profileID.uuidString.utf8)),
      salt: Data("quimene.mailbox".utf8), info: Data("quimene.mailbox.v1".utf8),
      outputByteCount: 32)
  }

  public static func seal(_ package: SharedMatchPackage, for profileID: UUID) throws -> String {
    let json = try JSONEncoder().encode(package)
    return try SessionCrypto.encrypt(json, key: key(for: profileID)).base64EncodedString()
  }

  public static func open(_ ciphertext: String, for profileID: UUID) -> SharedMatchPackage? {
    guard let data = Data(base64Encoded: ciphertext),
      let json = try? SessionCrypto.decrypt(data, key: key(for: profileID))
    else { return nil }
    return try? JSONDecoder().decode(SharedMatchPackage.self, from: json)
  }
}

/// Une partie déposée dans une boîte, encore scellée.
public struct MailboxItem: Codable, Sendable, Equatable {
  public let mailboxKey: String
  public let matchID: UUID
  public let ciphertext: String

  public init(mailboxKey: String, matchID: UUID, ciphertext: String) {
    self.mailboxKey = mailboxKey
    self.matchID = matchID
    self.ciphertext = ciphertext
  }

  enum CodingKeys: String, CodingKey {
    case mailboxKey = "mailbox_key"
    case matchID = "match_id"
    case ciphertext
  }
}

/// Fonctions SQL de la migration `create_quimene_match_mailbox`. Dépôt idempotent par
/// `(mailbox_key, match_id)` : un envoi retenté ne duplique jamais une partie.
public struct MatchMailboxTransport: Sendable {
  private let client: SupabaseClient

  public init() {
    client = SupabaseClient(
      supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
  }

  public func deposit(_ items: [MailboxItem]) async throws {
    guard !items.isEmpty else { return }
    try await client.rpc("quimene_mailbox_deposit", params: DepositParams(items: items)).execute()
  }

  public func fetch(mailboxKey: String) async throws -> [MailboxItem] {
    try await client.rpc("quimene_mailbox_fetch", params: FetchParams(mailboxKey: mailboxKey))
      .execute()
      .value
  }

  public func remove(mailboxKey: String, matchID: UUID) async throws {
    try await client.rpc(
      "quimene_mailbox_remove", params: RemoveParams(mailboxKey: mailboxKey, matchID: matchID)
    )
    .execute()
  }
}

private struct DepositParams: Encodable {
  let items: [MailboxItem]
  enum CodingKeys: String, CodingKey { case items = "p_items" }
}

private struct FetchParams: Encodable {
  let mailboxKey: String
  enum CodingKeys: String, CodingKey { case mailboxKey = "p_mailbox_key" }
}

private struct RemoveParams: Encodable {
  let mailboxKey: String
  let matchID: UUID
  enum CodingKeys: String, CodingKey {
    case mailboxKey = "p_mailbox_key"
    case matchID = "p_match_id"
  }
}
