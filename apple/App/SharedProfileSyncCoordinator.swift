import Domain
import Foundation
import Store
import SwiftData
import Sync

/// Doc 14 « Profils partagés », phase 2 — pousse le résumé d'une partie tout juste conclue vers
/// chaque participant lié à l'installation d'un ami, et récupère les résumés que d'autres ont
/// poussés vers l'une de mes propres fiches liées. Déclenché par `QuiMeneApp` (lancement et
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
    guard let pendingMatches = try? repository.matchesPendingSharedProfileSync(),
      !pendingMatches.isEmpty
    else { return }

    for match in pendingMatches {
      let linkedIDs = Array(Set(match.participants.compactMap { $0.player?.sharedProfileID }))
      let standings = match.participants.compactMap {
        participant -> SharedMatchSummaryPayload.Entry? in
        guard let rank = participant.finalRank, let score = participant.finalScore else {
          return nil
        }
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
      let rows = linkedIDs.map {
        SharedMatchSummaryRow(matchID: match.id, sharedProfileID: $0, payload: payload)
      }

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
  /// cet appareil qui l'a jouée et poussée).
  private func pull(context: ModelContext) async {
    let playerRepository = PlayerRepository(context: context)
    let matchRepository = MatchRepository(context: context)
    guard let linkedIDs = try? playerRepository.allSharedProfileIDs(), !linkedIDs.isEmpty else {
      return
    }
    guard let rows = try? await transport.fetchPending(for: linkedIDs) else { return }

    let myOwnID = (try? playerRepository.myOwnSharedPlayer())?.sharedProfileID

    for row in rows {
      let isMyOwnIdentity = row.sharedProfileID == myOwnID
      // Doc 14, phase 4 — remontée : suivre un ami donnait accès à *toutes* ses parties,
      // même jouées avec des tiers sans rapport. Une fiche qui suit un ami (pas la mienne)
      // ne matérialise donc une partie que si j'y étais moi-même — mon propre identifiant
      // partagé apparaît alors parmi les *autres* participants du résumé. Ma propre fiche
      // partagée, elle, reçoit tout sans filtre : je veux consolider l'intégralité de mes
      // parties, où qu'elles aient été jouées.
      let isRelevant =
        isMyOwnIdentity || row.payload.standings.contains { $0.sharedProfileID == myOwnID }
      if isRelevant {
        try? matchRepository.materializeSharedSummary(row.payload, matchID: row.matchID)
      }

      // Doc 14, phase 4 — remontée : supprimer après chaque lecture, quel que soit
      // l'appareil, faisait perdre la partie aux autres appareils qui suivent la même
      // personne si l'un d'eux la lisait (et donc la supprimait) en premier — plusieurs
      // amis peuvent suivre la même personne (doc 14 « Limites de confiance »). Seul
      // l'appareil qui fait autorité sur cet identifiant (le sien — une seule fiche
      // partagée par appareil, phase 4) nettoie la boîte aux lettres ; les autres laissent
      // la purge programmée (30 jours, hors de ce dépôt) s'en charger.
      if isMyOwnIdentity {
        try? await transport.delete(matchID: row.matchID, sharedProfileID: row.sharedProfileID)
      }
    }
  }
}
