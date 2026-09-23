import Catalog
import Domain
import Foundation
import Store
import SwiftData

/// Doc 05 « Wizard » — contrairement à Yams, une manche est simultanée : autant de `ScoreInput`
/// que de participants, saisis tous ensemble puis validés d'un coup (la somme des plis réalisés
/// doit égaler le numéro de manche — contrôle porté par `WizardRulesV1.validate()`).
@MainActor
@Observable
final class WizardRoundModel {
  private(set) var state: MatchState {
    didSet {
      MatchLiveActivityController.refresh(definition: definition, rules: rules, state: state)
    }
  }

  private(set) var validationErrorMessage: String?

  var bids: [Participant.ID: Int] = [:]
  var results: [Participant.ID: Int] = [:]

  let definition: GameDefinition
  private let rules: any GameRules
  private let match: MatchRecord
  private let repository: MatchRepository
  private let catalog: GameCatalog

  init(match: MatchRecord, context: ModelContext, catalog: GameCatalog) throws {
    self.match = match
    self.repository = MatchRepository(context: context)
    self.catalog = catalog
    self.definition = try catalog.definition(for: match.gameID, version: match.rulesVersion)
    self.rules = try catalog.rules(for: match.gameID, version: match.rulesVersion)
    self.state = try repository.loadState(match, catalog: catalog)
    MatchLiveActivityController.refresh(definition: definition, rules: rules, state: state)
  }

  var participants: [Participant] {
    state.participants.sorted { $0.seatIndex < $1.seatIndex }
  }

  var participantRecords: [ParticipantRecord] {
    match.participants.sorted { $0.seatIndex < $1.seatIndex }
  }

  var roundNumber: Int {
    state.rounds.count + 1
  }

  var totalTricks: Int {
    participants.reduce(0) { $0 + (results[$1.id] ?? 0) }
  }

  var finalStandings: [Standing] {
    rules.standings(state, definition: definition)
  }

  var isConcluded: Bool {
    state.status == .ended || state.status == .abandoned
  }

  func bid(for participantID: Participant.ID) -> Int {
    bids[participantID] ?? 0
  }

  func result(for participantID: Participant.ID) -> Int {
    results[participantID] ?? 0
  }

  func abandon() {
    state = (try? repository.abandonMatch(match, catalog: catalog)) ?? state
  }

  func submit() {
    let draft = RoundDraft(
      index: state.nextRoundIndex,
      inputs: participants.map { participant in
        let detail = ScoreDetail(
          payload: try! JSONEncoder().encode(WizardBidDetail(bid: bid(for: participant.id))))
        return ScoreInput(
          participantID: participant.id, rawValue: result(for: participant.id), detail: detail)
      })

    if case .invalid(let errors) = rules.validate(draft, in: state, definition: definition) {
      validationErrorMessage = errors.first?.message
      return
    }

    do {
      state = try repository.commitRound(draft, to: match, catalog: catalog)
      validationErrorMessage = nil
      bids = [:]
      results = [:]
    } catch {
      validationErrorMessage = "La manche n'a pas pu être enregistrée."
    }
  }

  func dismissError() {
    validationErrorMessage = nil
  }
}
