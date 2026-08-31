import Catalog
import DesignSystem
import Domain
import Store
import SwiftData
import SwiftUI
import UIKit

struct LiveMatchView: View {
    @State private var model: LiveMatchModel
    @State private var draftTexts: [Participant.ID: String] = [:]
    @FocusState private var focusedParticipantID: Participant.ID?
    @State private var isConfirmingManualEnd = false
    @State private var isConfirmingAbandon = false
    @State private var isConfirmingShareSwitch = false
    @State private var isPresentingShareSession = false
    @State private var isPresentingRoundHistory = false

    init(match: MatchRecord, context: ModelContext, catalog: GameCatalog) {
        _model = State(initialValue: try! LiveMatchModel(match: match, context: context, catalog: catalog))
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
                liveView
                    .navigationTitle("\(model.definition.name.fr) · Manche \(model.state.rounds.count + 1)")
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    private var liveView: some View {
        List {
            ScoreBoardView(
                participants: model.participants,
                totals: model.totals,
                ranks: Dictionary(uniqueKeysWithValues: model.currentStandings.map { ($0.participantID, $0.rank) }),
                requiresCloserSelection: model.requiresCloserSelection,
                allowsNegative: model.definition.scoring.entry.allowsNegative,
                canEdit: true,
                submitLabel: "Terminé",
                validationMessage: model.validationErrorMessage,
                readOnlyMessage: nil,
                closedParticipantID: $model.closedParticipantID,
                draftTexts: $draftTexts,
                focusedParticipantID: $focusedParticipantID
            ) { participantID, value in
                if let value {
                    model.setScore(value, for: participantID)
                } else {
                    model.clearScore(for: participantID)
                }
            } onSubmit: {
                finishRound()
            }
        }
        .listStyle(.plain)
        .toolbar {
            if !model.state.rounds.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button("Annuler la dernière manche") {
                        model.undoLastRound()
                        draftTexts = [:]
                    }
                }
            }
            ToolbarItem(placement: .navigationBarLeading) {
                Menu {
                    Button {
                        isPresentingShareSession = true
                    } label: {
                        Label(
                            model.isSharing ? "Voir la session partagée" : "Partager en direct",
                            systemImage: model.isSharing ? "wifi" : "wifi.circle"
                        )
                    }
                    Button {
                        isPresentingRoundHistory = true
                    } label: {
                        Label("Voir les manches", systemImage: "list.bullet")
                    }
                    if model.canEndManually {
                        Button("Terminer la partie") {
                            isConfirmingManualEnd = true
                        }
                    }
                    Button("Abandonner la partie", role: .destructive) {
                        isConfirmingAbandon = true
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .onAppear {
            focusedParticipantID = model.currentParticipant?.id
            if model.needsShareSwitchConfirmation {
                isConfirmingShareSwitch = true
            } else {
                model.attachToActiveSessionIfNeeded()
            }
        }
        .onChange(of: focusedParticipantID) { _, newValue in
            guard let newValue else { return }
            model.focus(on: newValue)
        }
        // Doc 09 « Fin de partie » — une manche acceptée d'un contributeur distant n'est plus
        // reçue directement par ce modèle (portée par `LiveShareCoordinator`, qui survit à cet
        // écran) : ce jeton republié à chaque événement distant est ce qui déclenche le rechargement.
        .onChange(of: LiveShareCoordinator.shared.remoteEventToken) { _, _ in
            model.refreshFromRemote()
        }
        .confirmationDialog(
            "Terminer la partie ?",
            isPresented: $isConfirmingManualEnd,
            titleVisibility: .visible
        ) {
            Button("Terminer la partie", role: .destructive) {
                model.endManually()
            }
        } message: {
            Text("Le classement final sera calculé à partir des manches jouées. Cette action ne peut pas être annulée.")
        }
        // Doc utilisateur — remontée : ouvrir cet écran alors qu'une session partage déjà une
        // *autre* partie encore en cours substituait ce que voient les pairs connectés sans
        // prévenir. Ne s'affiche jamais pour l'enchaînement volontaire (doc 09) — seulement
        // quand la partie remplacée est, elle aussi, encore en cours.
        .confirmationDialog(
            "Remplacer la partie partagée ?",
            isPresented: $isConfirmingShareSwitch,
            titleVisibility: .visible
        ) {
            Button("Partager cette partie à la place") {
                model.confirmShareSwitch()
            }
            Button("Annuler", role: .cancel) {}
        } message: {
            Text("Une session est en cours de partage sur \(model.pendingShareSwitchGameName ?? "une autre partie"). Continuer ici la remplacera : les personnes connectées verront cette partie à la place.")
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
            Text("La partie sera classée comme abandonnée dans l'historique, avec le classement atteint jusque-là. Cette action ne peut pas être annulée.")
        }
        .sheet(isPresented: $isPresentingShareSession) {
            ShareSessionView { allowsContributors in
                try await model.startSharing(deviceName: UIDevice.current.name, allowsContributors: allowsContributors)
            }
        }
        .sheet(isPresented: $isPresentingRoundHistory) {
            RoundHistoryView(state: model.state, definition: model.definition)
        }
        // Doc utilisateur — sans ça, la manche d'un contributeur distant se contente de faire
        // monter les totaux (déjà animés juste au-dessus) sans qu'on comprenne pourquoi. Le
        // bandeau nomme l'appareil, le retour haptique attire l'œil même sans le regarder. Même
        // bandeau pour l'explication d'un score modifié par une règle (doublement Skyjo…).
        .overlay(alignment: .top) {
            VStack(spacing: Space.sm) {
                if let message = model.roundExplanationMessage {
                    Banner(LocalizedStringResource(stringLiteral: message))
                }
                if let message = model.remoteActivityMessage {
                    Banner(LocalizedStringResource(stringLiteral: message))
                }
            }
            .padding(.horizontal, Space.lg)
            .padding(.top, Space.sm)
            .frame(maxWidth: .infinity)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
        .animation(.default, value: model.remoteActivityMessage)
        .animation(.default, value: model.roundExplanationMessage)
        .sensoryFeedback(.success, trigger: model.remoteActivityMessage) { oldValue, newValue in
            newValue != nil
        }
    }

    /// Doc utilisateur — les joueurs n'annoncent jamais leur score dans l'ordre des sièges :
    /// « Terminé » est donc toujours disponible et valide directement la manche avec ce qui a
    /// été saisi, plutôt que d'avancer champ par champ jusqu'au dernier joueur.
    private func finishRound() {
        let committed = model.commitRound()
        if committed {
            draftTexts = [:]
        }
        focusedParticipantID = model.currentParticipant?.id
    }
}
