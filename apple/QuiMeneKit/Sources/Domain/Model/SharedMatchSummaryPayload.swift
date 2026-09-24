import Foundation

/// Doc 14 « Profils partagés », phase 2 — le contenu transporté vers l'installation d'un ami
/// pour qu'une partie terminée apparaisse dans son propre historique. Volontairement un résumé,
/// jamais le journal d'événements complet (`StampedEvent`) : `finalRank`/`finalScore` suffisent à
/// des statistiques justes (`LeaderboardRepository`/`ProfileRepository` ne lisent jamais le
/// détail manche par manche), pour une fraction du poids. Dans `Domain` plutôt que `Store` ou
/// `Sync` : `Store` (matérialise en `MatchRecord`) et `Sync` (transporte via Supabase) en ont
/// besoin tous les deux, et aucun des deux ne dépend de l'autre.
public struct SharedMatchSummaryPayload: Sendable, Codable, Equatable {
  public struct Entry: Sendable, Codable, Equatable {
    /// `nil` pour un participant que le destinataire ne connaît pas forcément (l'hôte, un
    /// autre joueur non lié) — affiché quand même via les champs *snapshot*, comme
    /// `ParticipantRecord` le fait déjà pour tout joueur supprimé.
    public let sharedProfileID: UUID?
    public let nickname: String
    public let avatarKind: String
    public let avatarValue: String
    public let paletteID: String
    public let rank: Int
    public let score: Int

    public init(
      sharedProfileID: UUID?,
      nickname: String,
      avatarKind: String,
      avatarValue: String,
      paletteID: String,
      rank: Int,
      score: Int
    ) {
      self.sharedProfileID = sharedProfileID
      self.nickname = nickname
      self.avatarKind = avatarKind
      self.avatarValue = avatarValue
      self.paletteID = paletteID
      self.rank = rank
      self.score = score
    }

    enum CodingKeys: String, CodingKey {
      case sharedProfileID = "shared_profile_id"
      case nickname
      case avatarKind = "avatar_kind"
      case avatarValue = "avatar_value"
      case paletteID = "palette_id"
      case rank
      case score
    }
  }

  public let gameID: String
  public let rulesVersion: Int
  public let playedAt: Date
  public let standings: [Entry]

  public init(gameID: String, rulesVersion: Int, playedAt: Date, standings: [Entry]) {
    self.gameID = gameID
    self.rulesVersion = rulesVersion
    self.playedAt = playedAt
    self.standings = standings
  }

  enum CodingKeys: String, CodingKey {
    case gameID = "game_id"
    case rulesVersion = "rules_version"
    case playedAt = "played_at"
    case standings
  }
}
