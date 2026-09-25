import Catalog
import DesignSystem
import Domain
import Store
import SwiftData
import SwiftUI

struct WizardRoundView: View {
  @State private var model: WizardRoundModel
  @State private var isConfirmingAbandon = false
  @State private var isPresentingRoundHistory = false

  /// Doc utilisateur (audit qualité, 15) — même garantie que `LiveMatchView` : `MatchPlayView`
  /// ne route ici qu'après avoir vérifié que `definition` résout.
  init(match: MatchRecord, context: ModelContext, catalog: GameCatalog) {
    _model = State(
      initialValue: try! WizardRoundModel(match: match, context: context, catalog: catalog))
    myParticipantID = match.myParticipantID
  }

  /// Doc 16 — ma place, marquée « Moi ».
  private let myParticipantID: UUID?

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
          Section {
            trickBanner
          }
          Section("Manche \(model.roundNumber)") {
            ForEach(model.participants) { participant in
              participantRow(participant)
            }
          }
          Button("Valider la manche") {
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
      RoundHistoryView(
        state: model.state, definition: model.definition, myParticipantID: myParticipantID)
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

  /// Doc 05 : « la somme des plis réalisés doit égaler le numéro de la manche ». Aide visuelle
  /// seulement — le bouton de validation reste toujours actif, `rules.validate()` reste le seul
  /// vrai garde-fou (même convention que Belote/Yams, qui ne désactivent jamais leur bouton par
  /// anticipation).
  private var trickBanner: some View {
    let matches = model.totalTricks == model.roundNumber
    return HStack {
      Text("Plis distribués")
        .font(.label)
        .foregroundStyle(.textSecondary)
      Spacer()
      Text("\(model.totalTricks) / \(model.roundNumber)")
        .font(.h6)
        .foregroundStyle(matches ? .semanticSuccess : .semanticError)
    }
    .accessibilityElement(children: .combine)
    .accessibilityLabel("Plis distribués, \(model.totalTricks) sur \(model.roundNumber)")
  }

  private func participantRow(_ participant: Participant) -> some View {
    VStack(alignment: .leading, spacing: Space.xs) {
      HStack(spacing: Space.sm) {
        Text(participant.displayName).font(.h6).foregroundStyle(.textPrimary)
        if participant.id == myParticipantID { MeBadge() }
      }
      Stepper(
        "Annonce : \(model.bid(for: participant.id))",
        value: Binding(
          get: { model.bid(for: participant.id) },
          set: { model.bids[participant.id] = $0 }),
        in: 0...model.roundNumber
      )
      .accessibilityLabel("\(participant.displayName), annonce")
      .accessibilityValue("\(model.bid(for: participant.id))")
      Stepper(
        "Réalisé : \(model.result(for: participant.id))",
        value: Binding(
          get: { model.result(for: participant.id) },
          set: { model.results[participant.id] = $0 }),
        in: 0...model.roundNumber
      )
      .accessibilityLabel("\(participant.displayName), plis réalisés")
      .accessibilityValue("\(model.result(for: participant.id))")
    }
    .padding(.vertical, Space.xxs)
  }
}
