import DesignSystem
import Domain
import SwiftUI

/// Doc 09 — l'écran d'un pair non-hôte. Observateur : lecture seule, le tableau se met à jour
/// tout seul à mesure que l'hôte diffuse. Contributeur : le même tableau que `LiveMatchView`
/// (`ScoreBoardView`, partagé entre les deux écrans), mais « Envoyer » propose la manche à l'hôte
/// au lieu de l'écrire directement — elle n'apparaît aux autres qu'une fois acceptée.
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
  @FocusState private var focusedParticipantID: Participant.ID?
  @State private var isPresentingRoundHistory = false
  @State private var keyboardObserver = KeyboardObserver()
  /// Doc utilisateur — remontée : rien ne validait localement avant d'envoyer une proposition à
  /// l'hôte. Une manche invalide (Skyjo : aucun joueur désigné comme ayant fermé) était acceptée
  /// *optimistiquement* en local, montrée un instant, puis rejetée et retirée par l'hôte — assez
  /// vite pour donner l'impression que rien n'empêchait de l'ajouter. `ScoreBoardView.validationMessage`
  /// combine ce contrôle local et un rejet distant tardif dans le même message.
  @State private var validationErrorMessage: String?

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
    return "\(definition.name.localized) · Manche \(roundNumber)"
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
            Text(
              reconnectFailed
                ? "Toujours pas de connexion — nouvelle tentative automatique en cours."
                : "Reprise automatique en cours."
            )
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

      ScoreBoardView(
        participants: model.participants,
        totals: model.totals,
        ranks: Dictionary(
          uniqueKeysWithValues: model.currentStandings.map { ($0.participantID, $0.rank) }),
        requiresCloserSelection: definition.requiresCloserSelection,
        canEdit: model.canPropose,
        validationMessage: validationErrorMessage ?? model.latestRejectionReason,
        readOnlyMessage: model.canPropose
          ? nil
          : "Tu observes cette partie : la saisie se fait sur l'appareil de l'hôte ou d'un contributeur.",
        closedParticipantID: $closedParticipantID,
        draftTexts: $draftTexts,
        focusedParticipantID: $focusedParticipantID
      ) { _, _ in
        // Doc utilisateur — contrairement à l'hôte, un contributeur n'a pas de totaux
        // « en direct » à mettre à jour pendant la saisie : `model.totals` ne reflète que
        // les manches déjà acceptées par l'hôte, jamais un brouillon local.
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
      if model.canPropose {
        ScoreBoardView.keyboardAccessory(
          allowsNegative: definition.scoring.entry.allowsNegative,
          currentParticipantID: focusedParticipantID,
          submitLabel: "Envoyer",
          onToggleSign: toggleSign
        ) {
          Task { await sendRound() }
        }
      }
    }
    // Doc utilisateur — posé au niveau de l'écran, pas dans `ScoreBoardView` : un enfant de
    // liste qui porte lui-même `.safeAreaInset` faisait dupliquer tout le rendu (voir la note
    // en tête de `ScoreBoardView.swift`).
    .safeAreaInset(edge: .bottom) {
      if model.canPropose {
        ScoreBoardView.submitBar(
          isKeyboardVisible: keyboardObserver.isVisible, submitLabel: "Envoyer"
        ) {
          Task { await sendRound() }
        }
      }
    }
    .animation(.default, value: keyboardObserver.isVisible)
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

  private func toggleSign(for participantID: Participant.ID) {
    var text = draftTexts[participantID] ?? ""
    if text.hasPrefix("-") {
      text.removeFirst()
    } else {
      text = "-" + text
    }
    draftTexts[participantID] = text
  }

  /// Doc utilisateur — remontée : valide localement *avant* d'envoyer, exactement comme
  /// `LiveMatchModel.commitRound` côté hôte, plutôt que de compter uniquement sur le rejet
  /// distant de l'hôte (`SharedMatchModel.validate`, mêmes règles de jeu des deux côtés). Une
  /// manche invalide n'est ainsi plus jamais montrée comme acceptée, même un instant.
  private func sendRound() async {
    let inputs = model.participants.map { participant in
      ScoreInput(
        participantID: participant.id,
        rawValue: Int(draftTexts[participant.id] ?? "") ?? 0,
        modifiers: participant.id == closedParticipantID ? [.closedRound] : []
      )
    }

    if let result = model.validate(inputs), case .invalid(let errors) = result {
      validationErrorMessage = errors.first?.message
      return
    }
    validationErrorMessage = nil

    await model.propose(inputs)
    draftTexts = [:]
    closedParticipantID = nil
  }
}
