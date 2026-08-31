import DesignSystem
import Domain
import SwiftUI

/// Doc 09 — l'écran d'un pair non-hôte. Observateur : lecture seule, le tableau se met à jour
/// tout seul à mesure que l'hôte diffuse. Contributeur : les mêmes champs que `LiveMatchView`,
/// mais « Envoyer » propose la manche à l'hôte au lieu de l'écrire directement — elle n'apparaît
/// aux autres qu'une fois acceptée.
struct SharedMatchView: View {
    let model: SharedMatchModel
    /// Doc utilisateur P9 — `MatchConnectionCoordinator` retente déjà seul, toutes les quelques
    /// secondes, tant que cet écran affiche une perte de connexion : ce bouton ne sert qu'à forcer
    /// une tentative immédiate plutôt que d'attendre la prochaine. Retourne si la tentative a
    /// abouti, pour qu'un échec affiche un vrai message plutôt qu'un aller-retour silencieux vers
    /// l'état initial.
    let onReconnect: () async -> Bool
    @State private var isReconnecting = false
    @State private var reconnectFailed = false
    @State private var draftTexts: [Participant.ID: String] = [:]
    @State private var closedParticipantID: Participant.ID?
    @State private var focusedParticipantID: Participant.ID?
    @State private var isPresentingRoundHistory = false

    var body: some View {
        Group {
            if let definition = model.definition, let state = model.state {
                liveView(definition: definition, state: state)
            } else {
                ProgressView("Connexion à la partie…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var navigationTitle: String {
        guard let definition = model.definition else { return "Partie partagée" }
        let roundNumber = (model.state?.rounds.count ?? 0) + 1
        return "\(definition.name.fr) · Manche \(roundNumber)"
    }

    private func requiresCloserSelection(_ definition: GameDefinition) -> Bool {
        definition.scoring.modifiers.contains { $0.kind == .exclusiveFlag && $0.required }
    }

    private func liveView(definition: GameDefinition, state: MatchState) -> some View {
        List {
            if !model.isHostConnected {
                Section {
                    if model.isConcluded {
                        Text("La partie est terminée.")
                            .font(.label)
                            .foregroundStyle(.textSecondary)
                    } else {
                        Text("Connexion à l'hôte perdue. Le tableau affiché est le dernier reçu.")
                            .font(.label)
                            .foregroundStyle(.semanticError)
                        Text(reconnectFailed ? "Toujours pas de connexion — nouvelle tentative automatique en cours." : "Reprise automatique en cours.")
                            .font(.bodySmall)
                            .foregroundStyle(.textTertiary)
                        Button(isReconnecting ? "Reconnexion…" : "Réessayer maintenant") {
                            Task {
                                isReconnecting = true
                                reconnectFailed = false
                                let succeeded = await onReconnect()
                                isReconnecting = false
                                reconnectFailed = !succeeded
                            }
                        }
                        .disabled(isReconnecting)
                    }
                }
            }

            if model.canPropose, requiresCloserSelection(definition) {
                Section {
                    Text("A fermé la manche").font(.label).foregroundStyle(.textSecondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Space.sm) {
                            ForEach(model.participants) { participant in
                                Chip(LocalizedStringResource(stringLiteral: participant.displayName), isSelected: closedParticipantID == participant.id) {
                                    closedParticipantID = participant.id
                                }
                            }
                        }
                    }
                }
            }

            Section {
                ForEach(model.participants) { participant in
                    HStack(spacing: Space.md) {
                        Text(participant.displayName)
                            .font(.bodyText)
                            .foregroundStyle(.textPrimary)
                        Spacer()
                        Text((model.totals[participant.id] ?? 0).formatted())
                            .font(.scoreL)
                            .foregroundStyle(.textSecondary)
                        if model.canPropose {
                            scoreField(for: participant)
                        }
                    }
                    .padding(.vertical, Space.xs)
                    .contentShape(Rectangle())
                    .onTapGesture { focusedParticipantID = participant.id }
                }
            }

            if let reason = model.latestRejectionReason {
                Text(reason).font(.label).foregroundStyle(.semanticError)
            }

            if !model.canPropose {
                Section {
                    Text("Tu observes cette partie : la saisie se fait sur l'appareil de l'hôte ou d'un contributeur.")
                        .font(.bodySmall)
                        .foregroundStyle(.textTertiary)
                }
            }
        }
        .listStyle(.plain)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    isPresentingRoundHistory = true
                } label: {
                    Label("Voir les manches", systemImage: "list.bullet")
                }
            }
        }
        // Doc utilisateur — remontée : le bouton +/- manquait entièrement ici (un contributeur
        // ne pouvait pas saisir de score négatif). Même pavé custom que `LiveMatchView`, qui
        // porte le signe comme une touche parmi les chiffres plutôt que dans une barre
        // d'accessoires séparée.
        .safeAreaInset(edge: .bottom) {
            if model.canPropose {
                VStack(spacing: 0) {
                    if let focusedParticipantID {
                        ScoreKeypad(
                            allowsNegative: definition.scoring.entry.allowsNegative,
                            onDigit: { appendDigit($0, for: focusedParticipantID) },
                            onToggleSign: { toggleSign(for: focusedParticipantID) },
                            onDelete: { deleteLastDigit(for: focusedParticipantID) }
                        )
                    }
                    Button("Envoyer") {
                        Task { await sendRound() }
                    }
                    .buttonStyle(.primary(size: .medium))
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, Space.lg)
                    .padding(.vertical, Space.sm)
                    .background(.bar)
                }
            }
        }
        .animation(.default, value: focusedParticipantID)
        .sheet(isPresented: $isPresentingRoundHistory) {
            RoundHistoryView(state: state, definition: definition)
        }
        .overlay(alignment: .top) {
            if let message = model.roundExplanationMessage {
                Banner(LocalizedStringResource(stringLiteral: message))
                    .padding(.horizontal, Space.lg)
                    .padding(.top, Space.sm)
                    .frame(maxWidth: .infinity)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.default, value: model.roundExplanationMessage)
    }

    private func scoreField(for participant: Participant) -> some View {
        let text = draftTexts[participant.id] ?? ""
        return Text(text.isEmpty ? "0" : text)
            .font(.scoreM)
            .foregroundStyle(text.isEmpty ? .textTertiary : .textPrimary)
            .multilineTextAlignment(.trailing)
            .padding(.horizontal, Space.md)
            .frame(width: 88, height: ButtonHeight.medium, alignment: .trailing)
            .background(.neutralFill, in: .rect(cornerRadius: Radius.sm))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.sm)
                    .strokeBorder(.brandInk, lineWidth: focusedParticipantID == participant.id ? 2 : 0)
            }
            .contentShape(Rectangle())
            .onTapGesture { focusedParticipantID = participant.id }
    }

    private func appendDigit(_ digit: Int, for participantID: Participant.ID) {
        draftTexts[participantID] = (draftTexts[participantID] ?? "") + String(digit)
    }

    private func deleteLastDigit(for participantID: Participant.ID) {
        guard var text = draftTexts[participantID], !text.isEmpty else { return }
        text.removeLast()
        draftTexts[participantID] = text
    }

    private func toggleSign(for participantID: Participant.ID) {
        var text = draftTexts[participantID] ?? ""
        if text.hasPrefix("-") {
            text.removeFirst()
        } else {
            text = "-" + text
        }
        draftTexts[participantID] = text
    }

    private func sendRound() async {
        let inputs = model.participants.map { participant in
            ScoreInput(
                participantID: participant.id,
                rawValue: Int(draftTexts[participant.id] ?? "") ?? 0,
                modifiers: participant.id == closedParticipantID ? [.closedRound] : []
            )
        }
        await model.propose(inputs)
        draftTexts = [:]
        closedParticipantID = nil
    }
}
