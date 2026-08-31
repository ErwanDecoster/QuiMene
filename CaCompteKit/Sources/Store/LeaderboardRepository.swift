import Domain
import Foundation
import SwiftData

/// Doc 06 « Statistiques de groupe » (roadmap « après la v1 ») — classement des joueurs sur un
/// jeu donné, à travers toutes leurs parties terminées. Même politique que `ProfileRepository` :
/// calcul à la demande, rien de pré-agrégé (« un agrégat stocké est un agrégat qui finira
/// désynchronisé »).
@MainActor
public struct LeaderboardRepository {
  private let context: ModelContext

  public init(context: ModelContext) {
    self.context = context
  }

  public func leaderboard(for gameID: String) throws -> [LeaderboardEntry] {
    // Doc `ProfileRepository.stats` — filtrage en mémoire plutôt qu'un `#Predicate`
    // chaînant la relation optionnelle `player`, qui ne compile pas avec ce traducteur de
    // prédicat SwiftData.
    let participations = try context.fetch(FetchDescriptor<ParticipantRecord>())
      .filter {
        $0.player != nil && $0.match?.gameID == gameID
          && $0.match?.statusRaw == MatchStatus.ended.rawValue
      }

    struct Aggregate {
      let player: PlayerRecord
      var played = 0
      var wins = 0
      var normalizedRankSum = 0.0
      var normalizedRankCount = 0
    }

    var byPlayer: [UUID: Aggregate] = [:]
    for participation in participations {
      guard let player = participation.player else { continue }
      var aggregate = byPlayer[player.id] ?? Aggregate(player: player)
      aggregate.played += 1
      if participation.finalRank == 1 {
        aggregate.wins += 1
      }
      if let rank = participation.finalRank, let match = participation.match {
        let playerCount = match.participants.count
        if playerCount > 1 {
          aggregate.normalizedRankSum += Double(playerCount - rank) / Double(playerCount - 1)
          aggregate.normalizedRankCount += 1
        }
      }
      byPlayer[player.id] = aggregate
    }

    return byPlayer.values
      .map { aggregate in
        LeaderboardEntry(
          playerID: aggregate.player.id,
          name: aggregate.player.nickname,
          avatarKind: aggregate.player.avatarKind,
          avatarValue: aggregate.player.avatarValue,
          avatarPhoto: aggregate.player.avatarPhoto,
          paletteID: aggregate.player.paletteID,
          played: aggregate.played,
          wins: aggregate.wins,
          winRate: aggregate.played > 0 ? Double(aggregate.wins) / Double(aggregate.played) : 0,
          averageNormalizedRank: aggregate.normalizedRankCount > 0
            ? aggregate.normalizedRankSum / Double(aggregate.normalizedRankCount) : 0
        )
      }
      // Doc 03 n°5 — pas d'ordre implicite : à égalité parfaite de taux de victoire et de
      // rang moyen normalisé, le tri retombe sur le nombre de parties puis le nom, pour ne
      // jamais dépendre de l'ordre d'itération (non garanti) d'un `Dictionary`.
      .sorted { lhs, rhs in
        if lhs.winRate != rhs.winRate { return lhs.winRate > rhs.winRate }
        if lhs.averageNormalizedRank != rhs.averageNormalizedRank {
          return lhs.averageNormalizedRank > rhs.averageNormalizedRank
        }
        if lhs.played != rhs.played { return lhs.played > rhs.played }
        return lhs.name < rhs.name
      }
  }
}
