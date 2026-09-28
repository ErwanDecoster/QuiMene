import Catalog
import Domain
import Foundation
import Store
import SwiftData

/// Captures des fiches App Store (`Scripts/store-screenshots.sh`, doc 10 « Captures des
/// stores ») — l'app est lancée par `StoreScreenshotTests` avec `-screenshots`, sur le magasin
/// dédié aux tests d'interface (`-uitesting-reset`), rempli avec `spec/screenshots/demo-data.json`.
enum StoreScreenshots {
  /// Vrai seulement pendant les captures, jamais dans un build Release. Coupe aussi les appels
  /// réseau déclenchés par l'affichage (boîte aux lettres du profil, jetons de Live Activity) :
  /// une capture ne doit rien écrire ni lire dans la base de production.
  static var isActive: Bool {
    #if DEBUG
      ProcessInfo.processInfo.arguments.contains("-screenshots")
    #else
      false
    #endif
  }
}

#if DEBUG
  extension StoreScreenshots {
    /// Variable d'environnement posée par `StoreScreenshotTests` : le contenu de
    /// `demo-data.json`, que le simulateur ne peut pas lire lui-même sous `~/Documents`.
    static let demoDataEnvironmentKey = "QUIMENE_DEMO_DATA"

    private struct DemoData: Decodable {
      struct Player: Decodable {
        let id: String
        let nickname: String
        let emoji: String
        let palette: String
        let isMe: Bool?
      }

      struct Match: Decodable {
        struct Round: Decodable {
          let scores: [Int]
          /// Place (`seatIndex`) du joueur qui ferme la manche — Skyjo seulement.
          let closedBy: Int?
        }

        let game: String
        let players: [String]
        let startedMinutesAgo: Int
        let minutesPerRound: Int
        /// `inProgress`, `auto` (fin détectée par les règles) ou `manual` (« Terminer la partie »).
        let end: String
        let rounds: [Round]
      }

      let players: [Player]
      let matches: [Match]
    }

    private struct UnknownGame: Error {
      let id: String
    }

    /// À appeler une fois le conteneur chargé, avant le premier affichage : sans profil, la
    /// création de profil (`ProfileRequirement`) recouvrirait l'app. Sans effet si le magasin a
    /// déjà des joueurs (relance au sein d'une même capture).
    @MainActor
    static func seedIfRequested(into context: ModelContext) {
      guard isActive,
        let json = ProcessInfo.processInfo.environment[demoDataEnvironmentKey],
        (try? context.fetchCount(FetchDescriptor<PlayerRecord>())) == 0
      else { return }
      do {
        let demo = try JSONDecoder().decode(DemoData.self, from: Data(json.utf8))
        try seed(demo, into: context)
      } catch {
        assertionFailure("Données de démo illisibles : \(error)")
      }
    }

    @MainActor
    private static func seed(_ demo: DemoData, into context: ModelContext) throws {
      let playerRepository = PlayerRepository(context: context)
      let matchRepository = MatchRepository(context: context)
      let catalog = GameCatalog.embedded

      var players: [String: PlayerRecord] = [:]
      for player in demo.players {
        let record = try playerRepository.create(
          nickname: player.nickname, avatarKind: "emoji", avatarValue: player.emoji,
          paletteID: player.palette)
        if player.isMe == true {
          _ = try playerRepository.sharedProfileID(for: record)
        }
        players[player.id] = record
      }

      let now = Date()
      for match in demo.matches {
        guard let definition = catalog.allGames.first(where: { $0.id == match.game }) else {
          throw UnknownGame(id: match.game)
        }
        let participants = match.players.enumerated().map { seat, key in
          Participant(displayName: players[key]?.nickname ?? key, seatIndex: seat)
        }
        let variants = VariantSelection(
          Dictionary(uniqueKeysWithValues: definition.variants.map { ($0.id, $0.defaultValue) }))
        let start = now.addingTimeInterval(-Double(match.startedMinutesAgo) * 60)
        func time(afterRounds count: Int) -> Date {
          start.addingTimeInterval(Double(count * match.minutesPerRound) * 60)
        }

        var events = [
          StampedEvent(
            lamport: 0, deviceID: "local", occurredAt: start,
            event: .matchCreated(
              gameID: definition.id, rulesVersion: definition.rulesVersion, variants: variants,
              participants: participants))
        ]
        for (index, round) in match.rounds.enumerated() {
          let inputs = participants.map { participant in
            ScoreInput(
              participantID: participant.id, rawValue: round.scores[participant.seatIndex],
              modifiers: round.closedBy == participant.seatIndex ? [.closedRound] : [])
          }
          events.append(
            StampedEvent(
              lamport: UInt64(events.count), deviceID: "local",
              occurredAt: time(afterRounds: index + 1),
              event: .roundCommitted(RoundDraft(index: index, inputs: inputs))))
        }
        if match.end == "manual" {
          events.append(
            StampedEvent(
              lamport: UInt64(events.count), deviceID: "local",
              occurredAt: time(afterRounds: match.rounds.count), event: .matchEndedManually))
        }

        let record = try matchRepository.createMirroredMatch(
          id: UUID(), events: events, catalog: catalog
        ) { participant in
          let player = players[match.players[participant.seatIndex]]
          return MatchRepository.ParticipantSeed(
            player: player, nickname: participant.displayName,
            avatarKind: player?.avatarKind ?? "emoji", avatarValue: player?.avatarValue ?? "",
            paletteID: player?.paletteID ?? "1")
        }
        guard let record else { continue }
        // Une erreur dans les scores du JSON (seuil jamais atteint…) se voit ici plutôt que sur
        // une capture qui montrerait une partie encore en cours.
        assert(
          (record.statusRaw == "ended") == (match.end != "inProgress"),
          "\(match.game) : statut \(record.statusRaw), attendu \(match.end)")
        // Seul le profil de cet appareil est partagé : rien à déposer pour un ami.
        try matchRepository.markSharedProfileSyncComplete(record)
      }
    }
  }
#endif
