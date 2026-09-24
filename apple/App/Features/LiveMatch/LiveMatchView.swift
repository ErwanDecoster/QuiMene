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
  @State private var keyboardObserver = KeyboardObserver()
  @State private var isPickingNextMatch = false
  @Environment(DeepLinkRouter.self) private var deepLinkRouter

  /// Doc utilisateur (audit qualité, 15) — `MatchPlayView` a déjà vérifié que `definition`
  /// résout avant de router ici (sinon il affiche un `EmptyState` sans jamais construire cette
  /// vue), donc les deux lookups catalogue internes à `LiveMatchModel.init` refont le même
  /// calcul déterministe et ne peuvent pas échouer à nouveau. Risque résiduel accepté, pas
  /// couvert : un journal d'événements corrompu ferait échouer `repository.loadState` malgré
  /// tout — pas encore de parcours de secours pour ce cas précis.
  init(match: MatchRecord, context: ModelContext, catalog: GameCatalog) {
    _model = State(
      initialValue: try! LiveMatchModel(match: match, context: context, catalog: catalog))
  }

  var body: some View {
    content
      // Doc 16, phase C — un participant a lancé la partie suivante pendant que cet écran
      // affichait la précédente : le créateur la suit, comme tous les appareils de la session.
      .onChange(of: LiveShareCoordinator.shared.remoteStartedToken) { _, _ in
        guard let started = LiveShareCoordinator.shared.remoteStartedMatch,
          started.previousMatchID == model.matchID
        else { return }
        deepLinkRouter.pendingContinuedMatchID = started.newMatchID
      }
  }

  private var content: some View {
    Group {
      if model.isConcluded {
        ResultsView(
          state: model.state,
          definition: model.definition,
          standings: model.finalStandings,
          participantRecords: model.participantRecords
        )
        // Doc 16, phase C — dans une session en ligne, tout le monde peut enchaîner.
        .safeAreaInset(edge: .bottom) {
          if model.isSharing {
            NextMatchBar(isBusy: model.isSubmitting) { isPickingNextMatch = true }
          }
        }
        .sheet(isPresented: $isPickingNextMatch) {
          NextMatchPicker(
            playerCount: model.participants.count, currentGameID: model.definition.id
          ) { definition in
            Task {
              if let next = await model.startNextMatch(definition: definition) {
                deepLinkRouter.pendingContinuedMatchID = next
              }
            }
          }
        }
      } else {
        liveView
          .navigationTitle(
            "\(model.definition.name.localized) · Manche \(model.state.rounds.count + 1)"
          )
          .navigationBarTitleDisplayMode(.inline)
      }
    }
  }

  private var liveView: some View {
    List {
      ScoreBoardView(
        participants: model.participants,
        totals: model.totals,
        ranks: Dictionary(
          uniqueKeysWithValues: model.currentStandings.map { ($0.participantID, $0.rank) }),
        requiresCloserSelection: model.requiresCloserSelection,
        canEdit: true,
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
      ScoreBoardView.keyboardAccessory(
        allowsNegative: model.definition.scoring.entry.allowsNegative,
        currentParticipantID: focusedParticipantID,
        submitLabel: submitLabel,
        onToggleSign: toggleSign,
        onSubmit: finishRound
      )
    }
    // Doc utilisateur — posé au niveau de l'écran, pas dans `ScoreBoardView` : un enfant de
    // liste qui porte lui-même `.safeAreaInset` faisait dupliquer tout le rendu (voir la note
    // en tête de `ScoreBoardView.swift`).
    .safeAreaInset(edge: .bottom) {
      ScoreBoardView.submitBar(
        isKeyboardVisible: keyboardObserver.isVisible, submitLabel: submitLabel, onSubmit: finishRound
      )
    }
    .accessibleAnimation(.default, value: keyboardObserver.isVisible)
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
      Text(
        "Le classement final sera calculé à partir des manches jouées. Cette action ne peut pas être annulée."
      )
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
      Text(
        "Une session est en cours de partage sur \(model.pendingShareSwitchGameName ?? "une autre partie"). Continuer ici la remplacera : les personnes connectées verront cette partie à la place."
      )
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
    .sheet(isPresented: $isPresentingShareSession) {
      ShareSessionView { allowsContributors in
        try await model.startSharing(
          deviceName: UIDevice.current.name, allowsContributors: allowsContributors)
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
    .accessibleAnimation(.default, value: model.remoteActivityMessage)
    .accessibleAnimation(.default, value: model.roundExplanationMessage)
    // Doc 08 « Accessibilité » — `Banner` est purement visuel par défaut ; sans annonce
    // explicite, VoiceOver ne signale jamais son apparition.
    .onChange(of: model.remoteActivityMessage) { _, newValue in
      if let newValue { Banner.announce(LocalizedStringResource(stringLiteral: newValue)) }
    }
    .onChange(of: model.roundExplanationMessage) { _, newValue in
      if let newValue { Banner.announce(LocalizedStringResource(stringLiteral: newValue)) }
    }
    .sensoryFeedback(.success, trigger: model.remoteActivityMessage) { oldValue, newValue in
      newValue != nil
    }
  }

  private func toggleSign(for participantID: Participant.ID) {
    var text = draftTexts[participantID] ?? ""
    if text.hasPrefix("-") {
      text.removeFirst()
    } else {
      text = "-" + text
    }
    draftTexts[participantID] = text
    if let value = Int(text) {
      model.setScore(value, for: participantID)
    } else {
      model.clearScore(for: participantID)
    }
  }

  /// Doc utilisateur — les joueurs n'annoncent jamais leur score dans l'ordre des sièges :
  /// « Terminé » est donc toujours disponible et valide directement la manche avec ce qui a
  /// été saisi, plutôt que d'avancer champ par champ jusqu'au dernier joueur.
  ///
  /// Doc 16, phase C — dans une partie partagée en ligne, la manche passe d'abord par le serveur :
  /// la saisie n'est effacée qu'une fois acceptée ; devancée ou hors ligne, elle reste en place.
  private func finishRound() {
    Task {
      if await model.submitRound() {
        draftTexts = [:]
      }
      focusedParticipantID = model.currentParticipant?.id
    }
  }

  /// Hors ligne dans une partie partagée, le bouton dit pourquoi la saisie ne part pas (doc 16).
  private var submitLabel: LocalizedStringResource {
    model.isOfflineShared ? "Hors connexion" : "Terminé"
  }
}
