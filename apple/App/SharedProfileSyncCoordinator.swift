import Domain
import Foundation
import Store
import SwiftData
import Sync

/// Doc 16, phase E — historique partagé : chaque partie terminée avec un ami lié lui est déposée,
/// **complète** et chiffrée, dans sa boîte aux lettres (`MatchMailboxTransport`) ; les parties que
/// d'autres m'ont déposées sont enregistrées ici comme si je les avais jouées. Remplace les
/// résumés du doc 14. Déclenché au lancement et au retour au premier plan (`QuiMeneApp`), dès
/// qu'une partie se termine (`ResultsView`, dépôt immédiat), et à l'ouverture de l'Historique (avec
/// « tirer pour actualiser ») pour relever sa boîte sans relancer l'app.
@MainActor
@Observable
final class SharedProfileSyncCoordinator {
  static let shared = SharedProfileSyncCoordinator()

  /// Change à chaque partie reçue : l'Historique affiché se recharge.
  private(set) var receivedToken = UUID()

  @ObservationIgnored private let transport = MatchMailboxTransport()
  @ObservationIgnored private var isSyncing = false
  @ObservationIgnored private var needsAnotherPass = false

  private init() {}

  /// Une demande pendant un passage en cours n'est pas perdue : un passage de plus suit.
  func sync(context: ModelContext) async {
    guard !isSyncing else {
      needsAnotherPass = true
      return
    }
    isSyncing = true
    defer { isSyncing = false }
    repeat {
      needsAnotherPass = false
      await deposit(context: context)
      await collect(context: context)
    } while needsAnotherPass
  }

  /// Une partie terminée avec au moins un ami lié (pas moi) : déposée chez chacun, une fois.
  /// Jamais retentée indéfiniment pour une partie qui n'en a plus (fiche déliée entre-temps).
  private func deposit(context: ModelContext) async {
    let repository = MatchRepository(context: context)
    guard let pending = try? repository.matchesPendingSharedProfileSync(), !pending.isEmpty
    else { return }
    let myID = (try? PlayerRepository(context: context).myOwnSharedPlayer())?.sharedProfileID

    for match in pending {
      let recipients = Set(match.participants.compactMap { $0.player?.sharedProfileID })
        .subtracting([myID].compactMap { $0 })
      guard !match.isImportedSummary, !recipients.isEmpty,
        let events = try? repository.currentLog(for: match), !events.isEmpty
      else {
        try? repository.markSharedProfileSyncComplete(match)
        continue
      }
      let package = SharedMatchPackage(
        matchID: match.id,
        participants: match.participants.map(Self.packaged),
        events: events)
      let items = recipients.compactMap { recipient in
        (try? MailboxCrypto.seal(package, for: recipient)).map {
          MailboxItem(
            mailboxKey: MailboxCrypto.lookupKey(for: recipient), matchID: match.id, ciphertext: $0)
        }
      }
      do {
        try await transport.deposit(items)
        try? repository.markSharedProfileSyncComplete(match)
      } catch {
        // Échec silencieux : `pendingSharedProfileSync` reste `true`, nouvelle tentative au
        // prochain retour au premier plan.
      }
    }
  }

  /// Ma boîte : chaque partie reçue est enregistrée (sans effet si je l'ai déjà, jouée ici ou
  /// suivie dans une session), puis retirée. Un dépôt illisible est retiré aussi, pour ne pas
  /// être relu indéfiniment.
  private func collect(context: ModelContext) async {
    guard let myID = (try? PlayerRepository(context: context).myOwnSharedPlayer())?.sharedProfileID
    else { return }
    let mailboxKey = MailboxCrypto.lookupKey(for: myID)
    guard let items = try? await transport.fetch(mailboxKey: mailboxKey) else { return }
    let repository = MatchRepository(context: context)
    var received = false
    for item in items {
      if let package = MailboxCrypto.open(item.ciphertext, for: myID) {
        guard (try? repository.importSharedMatch(package, catalog: .embedded)) != nil else {
          continue
        }
        received = true
      }
      try? await transport.remove(mailboxKey: mailboxKey, matchID: item.matchID)
    }
    if received { receivedToken = UUID() }
  }

  /// Un joueur tel que le destinataire l'affichera. Une photo ne voyage pas : repli sur l'emoji
  /// dérivé du pseudo, comme pour toute nouvelle fiche.
  private static func packaged(_ participant: ParticipantRecord) -> SharedMatchPackage.Participant {
    let isPhoto = participant.avatarKindSnapshot == "photo"
    let seed = LiveShareCoordinator.generatedSeed(for: participant.nicknameSnapshot)
    return SharedMatchPackage.Participant(
      participantID: participant.id,
      sharedProfileID: participant.player?.sharedProfileID,
      nickname: participant.nicknameSnapshot,
      avatarKind: isPhoto ? seed.avatarKind : participant.avatarKindSnapshot,
      avatarValue: isPhoto ? seed.avatarValue : participant.avatarValueSnapshot,
      paletteID: participant.paletteIDSnapshot)
  }
}
