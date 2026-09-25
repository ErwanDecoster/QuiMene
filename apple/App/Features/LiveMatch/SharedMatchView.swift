import DesignSystem
import Domain
import Store
import SwiftData
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
  @State private var isPickingNextMatch = false
  @Environment(\.modelContext) private var modelContext
  /// Mes amis liés (doc 14) : leur place porte un lien.
  @Query(filter: #Predicate<PlayerRecord> { $0.sharedProfileID != nil && !$0.sharedProfileIsMine })
  private var friends: [PlayerRecord]

  var body: some View {
    Group {
      if let definition = model.definition, let state = model.state {
        if model.needsIdentity {
          WhoAreYouView(model: model)
        } else if model.isConcluded {
          resultsView(definition: definition, state: state)
        } else {
          liveView(definition: definition, state: state)
        }
      } else {
        ProgressView("Connexion à la partie…")
          .frame(maxWidth: .infinity, maxHeight: .infinity)
      }
    }
    .navigationTitle(navigationTitle)
    .navigationBarTitleDisplayMode(.inline)
    // Doc 16, phase D — ma place retenue : le créateur devient mon ami (liaison dans les deux sens).
    .task(id: model.ownerToBefriend?.id) {
      if let owner = model.ownerToBefriend {
        FriendLinking.ensureFriend(owner, in: modelContext)
      }
    }
  }

  /// Doc 16, phase C — même écran de résultats que le créateur, puis « Partie suivante » : un
  /// participant peut enchaîner même si le créateur est absent.
  private func resultsView(definition: GameDefinition, state: MatchState) -> some View {
    ResultsView(
      state: state,
      definition: definition,
      standings: model.currentStandings,
      participantRecords: model.transientRecords,
      myParticipantID: model.myParticipantID
    )
    .safeAreaInset(edge: .bottom) {
      if model.canPropose {
        VStack(spacing: Space.xs) {
          if let reason = model.latestRejectionReason {
            Text(reason).font(.label).foregroundStyle(.semanticError)
          }
          NextMatchBar(isBusy: model.isSubmitting) { isPickingNextMatch = true }
        }
      }
    }
    .sheet(isPresented: $isPickingNextMatch) {
      NextMatchPicker(playerCount: state.participants.count, currentGameID: state.gameID) {
        definition in
        Task { await model.startNextMatch(definition: definition) }
      }
    }
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
          if model.isSessionClosed {
            Text("Le créateur a arrêté la session. Le tableau affiché est le dernier reçu.")
              .font(.label)
              .foregroundStyle(.textSecondary)
          } else {
            // Doc 16, phase C — plus d'hôte à rejoindre : seule la connexion de cet appareil
            // compte. Le tableau reste celui du dernier rattrapage, la saisie est bloquée.
            Text("Hors connexion. Le tableau affiché est le dernier reçu.")
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

      identitySection

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
          : model.isSpectator
            ? String(localized: "Tu regardes la partie : la saisie se fait sur les appareils des joueurs.")
            : String(localized: "Tu observes cette partie : seul le créateur saisit les scores."),
        profileBadges: model.profileBadges(friendProfileIDs: Set(friends.compactMap(\.sharedProfileID))),
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
    }
    // Doc utilisateur — posé au niveau de l'écran, pas dans `ScoreBoardView` : un enfant de
    // liste qui porte lui-même `.safeAreaInset` faisait dupliquer tout le rendu (voir la note
    // en tête de `ScoreBoardView.swift`). Pas de `ToolbarItemGroup(placement: .keyboard)` ici :
    // cet écran vit dans le plein écran « Rejoindre » posé à la racine (doc 16, phase A), où
    // SwiftUI n'affiche pas la barre d'accessoires du clavier — ni signe, ni « Envoyer ». La
    // barre du bas, remontée au-dessus du clavier par la zone sûre, en tient lieu.
    .safeAreaInset(edge: .bottom) {
      if model.canPropose {
        if keyboardObserver.isVisible {
          ScoreBoardView.keyboardBar(
            allowsNegative: definition.scoring.entry.allowsNegative,
            currentParticipantID: focusedParticipantID,
            submitLabel: "Envoyer",
            onToggleSign: toggleSign
          ) {
            Task { await sendRound() }
          }
        } else {
          ScoreBoardView.submitBar(isKeyboardVisible: false, submitLabel: "Envoyer") {
            Task { await sendRound() }
          }
        }
      }
    }
    .accessibleAnimation(.default, value: keyboardObserver.isVisible)
    .sheet(isPresented: $isPresentingRoundHistory) {
      RoundHistoryView(
        state: state, definition: definition, myParticipantID: model.myParticipantID)
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
    .accessibleAnimation(.default, value: model.roundExplanationMessage)
    // Doc 08 « Accessibilité » — voir la même remontée dans `LiveMatchView.swift`.
    .onChange(of: model.roundExplanationMessage) { _, newValue in
      if let newValue { Banner.announce(LocalizedStringResource(stringLiteral: newValue)) }
    }
  }

  /// Doc 16, phase D — qui je suis dans cette partie, et comment en changer.
  @ViewBuilder
  private var identitySection: some View {
    if model.isSpectator {
      Section {
        HStack {
          Text("Tu regardes la partie.").font(.bodyText).foregroundStyle(.textSecondary)
          Spacer(minLength: Space.sm)
          Button("Je joue aussi") { Task { await model.chooseAgain() } }
        }
      }
    } else if let seat = model.mySeat {
      Section {
        HStack {
          Text("Tu joues : \(seat.displayName)").font(.bodyText).foregroundStyle(.textSecondary)
          Spacer(minLength: Space.sm)
          if model.canChangeSeat {
            Button("Changer") { Task { await model.chooseAgain() } }
          }
        }
      }
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

    // Doc 16, phase C — la saisie n'est effacée qu'une fois acceptée par le serveur ; devancée
    // ou hors ligne, elle reste en place (`latestRejectionReason` dit pourquoi).
    guard await model.propose(inputs) else { return }
    draftTexts = [:]
    closedParticipantID = nil
  }
}

/// Doc 16, phase D — « Qui es-tu dans cette partie ? », à l'arrivée dans une session : toucher sa
/// place la revendique (premier arrivé, premier servi), ou « Je regarde seulement ». Un ami déjà
/// lié par le créateur n'y passe jamais : sa place est reconnue d'office.
private struct WhoAreYouView: View {
  let model: SharedMatchModel

  var body: some View {
    List {
      Section {
        ForEach(model.participants) { participant in
          let status = model.seatStatus(of: participant)
          Button {
            Task { await model.claim(participant) }
          } label: {
            HStack(spacing: Space.md) {
              AvatarView(avatar: Avatar.generated(for: participant.displayName), size: .medium)
              Text(participant.displayName)
                .font(.bodyText)
                .foregroundStyle(status == .taken ? .textTertiary : .textPrimary)
              Spacer(minLength: 0)
              if status == .taken {
                Text("Déjà prise").font(.label).foregroundStyle(.textTertiary)
              }
            }
          }
          .disabled(status == .taken || model.me == nil || model.isClaiming)
        }
      } header: {
        Text("Qui es-tu dans cette partie ?")
      } footer: {
        Text(
          "Ton profil est lié à cette place chez le créateur, qui en est averti et peut annuler."
        )
      }

      if let message = model.identityMessage {
        Section {
          Text(message).font(.label).foregroundStyle(.semanticError)
        }
      }

      Section {
        Button("Je regarde seulement") { model.watchOnly() }
      } footer: {
        Text("Tu suis la partie sans saisir de score.")
      }
    }
  }
}
