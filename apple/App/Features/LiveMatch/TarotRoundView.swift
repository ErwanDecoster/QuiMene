import Catalog
import DesignSystem
import Domain
import Store
import SwiftData
import SwiftUI

struct TarotRoundView: View {
  @State private var model: TarotRoundModel
  @State private var isConfirmingAbandon = false
  @State private var isPresentingRoundHistory = false

  private let contractNames = [
    "Petite", "Garde", "Garde sans le chien", "Garde contre le chien",
  ]
  private let poigneeNames = ["Aucune", "Simple", "Double", "Triple"]

  /// Doc utilisateur (audit qualité, 15) — même garantie que `LiveMatchView` : `MatchPlayView`
  /// ne route ici qu'après avoir vérifié que `definition` résout.
  init(match: MatchRecord, context: ModelContext, catalog: GameCatalog) {
    _model = State(
      initialValue: try! TarotRoundModel(match: match, context: context, catalog: catalog))
  }

  var body: some View {
    Group {
      if model.isConcluded {
        ResultsView(
          state: model.state,
          definition: model.definition,
          standings: model.finalStandings,
          participantRecords: model.participantRecords
        )
      } else {
        Form {
          scoresSection
          handSection
          Button("Valider la donne") {
            model.submit()
          }
          .buttonStyle(.primary(size: .medium))
          .frame(maxWidth: .infinity)
          .listRowBackground(Color.clear)
        }
        .navigationTitle(model.definition.name.localized)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
          ToolbarItem(placement: .navigationBarLeading) {
            Button("Abandonner la partie", role: .destructive) {
              isConfirmingAbandon = true
            }
          }
          ToolbarItem(placement: .primaryAction) {
            Button {
              isPresentingRoundHistory = true
            } label: {
              Label("Voir les manches", systemImage: "list.bullet")
            }
          }
        }
      }
    }
    .sheet(isPresented: $isPresentingRoundHistory) {
      RoundHistoryView(state: model.state, definition: model.definition)
    }
    .alert(
      "Saisie invalide",
      isPresented: Binding(
        get: { model.validationErrorMessage != nil }, set: { if !$0 { model.dismissError() } })
    ) {
      Button("OK") {}
    } message: {
      Text(model.validationErrorMessage ?? "")
    }
    .confirmationDialog(
      "Abandonner cette partie ?",
      isPresented: $isConfirmingAbandon,
      titleVisibility: .visible
    ) {
      Button("Abandonner", role: .destructive) {
        model.abandon()
      }
    } message: {
      Text(
        "La partie sera classée comme abandonnée dans l'historique, avec le classement atteint jusque-là. Cette action ne peut pas être annulée."
      )
    }
  }

  private var scoresSection: some View {
    Section("Scores") {
      ForEach(model.participants) { participant in
        let total = model.state.total(for: participant.id)
        let rank = model.finalStandings.first { $0.participantID == participant.id }?.rank
        HStack {
          Text(participant.displayName).font(.h6).foregroundStyle(.textPrimary)
          Spacer()
          Text(total.formatted()).font(.scoreL).foregroundStyle(.textPrimary)
        }
        .accessibleScoreRow(name: participant.displayName, rank: rank, score: total)
      }
    }
  }

  @ViewBuilder
  private var handSection: some View {
    Section("Cette donne") {
      Toggle("Personne ne prend", isOn: $model.isPassed)
        .tint(.brandInk)

      if !model.isPassed {
        Picker("Preneur", selection: $model.takerID) {
          ForEach(model.participants) { participant in
            Text(participant.displayName).tag(participant.id)
          }
        }

        if model.needsPartner {
          Picker("Partenaire (roi appelé)", selection: $model.partnerID) {
            Text("Aucun").tag(Participant.ID?.none)
            ForEach(model.partnerChoices) { participant in
              Text(participant.displayName).tag(Participant.ID?.some(participant.id))
            }
          }
        }

        Picker("Contrat", selection: $model.contract) {
          ForEach(Array(contractNames.enumerated()), id: \.offset) { index, name in
            Text(name).tag(index)
          }
        }
        .pickerStyle(.segmented)

        Stepper("Points du preneur : \(model.points)", value: $model.points, in: 0...91)
        Stepper("Bouts : \(model.bouts)", value: $model.bouts, in: 0...3)

        Toggle("Petit au bout", isOn: $model.petitAuBout)
          .tint(.brandInk)

        Picker("Poignée", selection: $model.poignee) {
          ForEach(Array(poigneeNames.enumerated()), id: \.offset) { index, name in
            Text(name).tag(index)
          }
        }

        Toggle("Chelem annoncé", isOn: $model.chelemAnnounced)
          .tint(.brandInk)
        Toggle("Chelem réalisé", isOn: $model.chelemAchieved)
          .tint(.brandInk)
      }
    }
  }
}
