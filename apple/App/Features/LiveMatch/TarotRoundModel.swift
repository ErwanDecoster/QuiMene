import Catalog
import Domain
import Foundation
import Store
import SwiftData

/// Doc 05 « Tarot » — une donne est représentée par 1 ou 2 `ScoreInput` (jamais un par
/// participant) : celui du preneur (toujours), et celui du partenaire à 5 joueurs. Les
/// défenseurs ne saisissent jamais rien, leur part est calculée par `TarotRulesV1.score()`.
@MainActor
@Observable
final class TarotRoundModel {
  private(set) var state: MatchState {
    didSet {
      MatchLiveActivityController.refresh(definition: definition, rules: rules, state: state)
    }
  }

  private(set) var validationErrorMessage: String?

  var isPassed = false
  var takerID: Participant.ID
  var partnerID: Participant.ID?
  var points: Int = 46
  var contract: Int = 0
  var bouts: Int = 0
  var poignee: Int = 0
  var petitAuBout = false
  var chelemAnnounced = false
  var chelemAchieved = false

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
    let loadedState = try repository.loadState(match, catalog: catalog)
    self.state = loadedState
    self.takerID =
      loadedState.participants.sorted { $0.seatIndex < $1.seatIndex }.first?.id ?? UUID()
    MatchLiveActivityController.refresh(definition: definition, rules: rules, state: state)
  }

  var participants: [Participant] {
    state.participants.sorted { $0.seatIndex < $1.seatIndex }
  }

  var needsPartner: Bool {
    state.participants.count == 5
  }

  var partnerChoices: [Participant] {
    participants.filter { $0.id != takerID }
  }

  var finalStandings: [Standing] {
    rules.standings(state, definition: definition)
  }

  var isConcluded: Bool {
    state.status == .ended || state.status == .abandoned
  }

  var participantRecords: [ParticipantRecord] {
    match.participants.sorted { $0.seatIndex < $1.seatIndex }
  }

  func abandon() {
    state = (try? repository.abandonMatch(match, catalog: catalog)) ?? state
  }

  func submit() {
    let draft: RoundDraft
    if isPassed {
      draft = RoundDraft(
        index: state.rounds.count,
        inputs: [ScoreInput(participantID: takerID, rawValue: 0, modifiers: ["passed"])])
    } else {
      var modifiers: Set<ModifierID> = ["isTaker"]
      if petitAuBout { modifiers.insert("petitAuBout") }
      if chelemAnnounced { modifiers.insert("chelemAnnounced") }
      if chelemAchieved { modifiers.insert("chelemAchieved") }

      let detail = ScoreDetail(
        payload: try! JSONEncoder().encode(
          TarotHandDetail(contract: contract, bouts: bouts, poignee: poignee)))

      var inputs = [
        ScoreInput(participantID: takerID, rawValue: points, detail: detail, modifiers: modifiers)
      ]
      // Défense contre un partenaire choisi avant un changement de preneur qui le rendrait
      // identique au preneur : `validate()` réclame alors « partenaire requis » plutôt que de
      // laisser passer une donne où le preneur est aussi son propre partenaire.
      if needsPartner, let partnerID, partnerID != takerID {
        inputs.append(ScoreInput(participantID: partnerID, rawValue: 0, modifiers: ["isPartner"]))
      }
      draft = RoundDraft(index: state.rounds.count, inputs: inputs)
    }

    if case .invalid(let errors) = rules.validate(draft, in: state, definition: definition) {
      validationErrorMessage = errors.first?.message
      return
    }

    do {
      state = try repository.commitRound(draft, to: match, catalog: catalog)
      validationErrorMessage = nil
      resetDraft()
    } catch {
      validationErrorMessage = "La donne n'a pas pu être enregistrée."
    }
  }

  func dismissError() {
    validationErrorMessage = nil
  }

  private func resetDraft() {
    isPassed = false
    partnerID = nil
    points = 46
    contract = 0
    bouts = 0
    poignee = 0
    petitAuBout = false
    chelemAnnounced = false
    chelemAchieved = false
  }
}
