import Foundation

/// Doc 06 « Statistiques de groupe » (roadmap « après la v1 ») — un joueur dans le classement
/// d'un jeu donné. Champs à plat plutôt qu'une référence à `PlayerRecord` : même choix que
/// `ProfileStats.Nemesis`, pour rester `Sendable` sans faire fuiter un modèle SwiftData hors de
/// `Store`.
public struct LeaderboardEntry: Sendable, Equatable, Identifiable {
  public let playerID: UUID
  public let name: String
  public let avatarKind: String
  public let avatarValue: String
  public let avatarPhoto: Data?
  public let paletteID: String
  public let played: Int
  public let wins: Int
  public let winRate: Double
  /// `(nbJoueurs − rang) / (nbJoueurs − 1)`, moyenné — départage à taux de victoire égal,
  /// même mesure que `ProfileStats.averageNormalizedRank` (doc 06).
  public let averageNormalizedRank: Double

  public var id: UUID { playerID }

  public init(
    playerID: UUID,
    name: String,
    avatarKind: String,
    avatarValue: String,
    avatarPhoto: Data?,
    paletteID: String,
    played: Int,
    wins: Int,
    winRate: Double,
    averageNormalizedRank: Double
  ) {
    self.playerID = playerID
    self.name = name
    self.avatarKind = avatarKind
    self.avatarValue = avatarValue
    self.avatarPhoto = avatarPhoto
    self.paletteID = paletteID
    self.played = played
    self.wins = wins
    self.winRate = winRate
    self.averageNormalizedRank = averageNormalizedRank
  }
}
