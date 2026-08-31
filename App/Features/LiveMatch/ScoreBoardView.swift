import DesignSystem
import Domain
import SwiftUI

/// Doc utilisateur — remontée : `LiveMatchView` (hôte) et `SharedMatchView` (pair) affichaient la
/// même chose — classement, lignes de score, sélection du joueur qui ferme, validation — via deux
/// implémentations indépendantes qui avaient fini par diverger (classement absent côté pair,
/// bouton de signe manquant côté pair, aucune validation locale avant l'envoi côté contributeur).
/// Un seul composant, utilisé par les deux écrans : une différence de comportement entre hôte et
/// pair devient impossible par construction, plutôt qu'un correctif à refaire des deux côtés.
///
/// Doc utilisateur — remontée : porte uniquement du contenu de liste (`Section`/lignes), jamais
/// `.toolbar`/`.safeAreaInset`. Cette vue est utilisée comme *contenu* d'un `List` parent
/// (`List { ScoreBoardView(...) }`) — lui poser ces deux modificateurs faisait dupliquer tout le
/// rendu (liste *et* barre de clavier) : SwiftUI matérialise deux fois une vue-contenu de liste
/// qui porte elle-même un `.toolbar`/`.safeAreaInset`, une fois comme ligne, une fois pour en
/// extraire les préférences de chrome d'écran. Le clavier/la barre de validation restent donc
/// posés par l'écran appelant, via les fonctions statiques ci-dessous — mêmes boutons, mêmes
/// libellés, sans le bug.
struct ScoreBoardView: View {
  let participants: [Participant]
  let totals: [Participant.ID: Int]
  let ranks: [Participant.ID: Int]
  let requiresCloserSelection: Bool
  /// `false` pour un observateur : lecture seule, aucun champ de score.
  let canEdit: Bool
  let validationMessage: String?
  /// Doc utilisateur — « Tu observes cette partie… » : n'a de sens que côté pair, `nil` pour
  /// l'hôte.
  let readOnlyMessage: String?

  @Binding var closedParticipantID: Participant.ID?
  @Binding var draftTexts: [Participant.ID: String]
  var focusedParticipantID: FocusState<Participant.ID?>.Binding

  /// Répercute une frappe sur le modèle appelant (`LiveMatchModel.setScore`/`clearScore`, pour
  /// que les totaux affichés bougent en direct) — no-op pour `SharedMatchModel`, qui ne connaît
  /// les scores qu'une fois la manche proposée.
  let onScoreChange: (Participant.ID, Int?) -> Void

  private var rankedParticipants: [Participant] {
    participants.sorted { lhs, rhs in
      let l = ranks[lhs.id] ?? .max
      let r = ranks[rhs.id] ?? .max
      if l != r { return l < r }
      return lhs.seatIndex < rhs.seatIndex
    }
  }

  var body: some View {
    if canEdit, requiresCloserSelection {
      Section {
        Text("A fermé la manche").font(.label).foregroundStyle(.textSecondary)
        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: Space.sm) {
            ForEach(participants) { participant in
              Chip(
                LocalizedStringResource(stringLiteral: participant.displayName),
                isSelected: closedParticipantID == participant.id
              ) {
                closedParticipantID = participant.id
              }
            }
          }
        }
      }
    }

    Section {
      ForEach(rankedParticipants) { participant in
        HStack(spacing: Space.md) {
          if let rank = ranks[participant.id] {
            Text("\(rank)")
              .font(.label)
              .foregroundStyle(.textSecondary)
              .frame(minWidth: 18, alignment: .leading)
          }
          Text(participant.displayName)
            .font(participant.id == focusedParticipantID.wrappedValue ? .h6 : .bodyText)
            .foregroundStyle(.textPrimary)
          Spacer()
          Text((totals[participant.id] ?? 0).formatted())
            .font(.scoreL)
            .foregroundStyle(.textSecondary)
            .contentTransition(.numericText())
            .animation(.default, value: totals[participant.id])
          if canEdit {
            scoreField(for: participant)
          }
        }
        .padding(.vertical, Space.xs)
        .contentShape(Rectangle())
        .onTapGesture {
          guard canEdit else { return }
          focusedParticipantID.wrappedValue = participant.id
        }
      }
    }

    if let validationMessage {
      Text(validationMessage).font(.label).foregroundStyle(.semanticError)
    }

    if let readOnlyMessage {
      Section {
        Text(readOnlyMessage).font(.bodySmall).foregroundStyle(.textTertiary)
      }
    }
  }

  /// Charte §5.4 — jamais vide en apparence (placeholder `0`), bordure au focus uniquement.
  /// Clavier système (`.numberPad`) : pas de touche « − », d'où le bouton de signe posé par
  /// l'écran appelant (`keyboardAccessory` ci-dessous) pour les jeux qui acceptent les valeurs
  /// négatives.
  private func scoreField(for participant: Participant) -> some View {
    TextField("0", text: textBinding(for: participant.id))
      .keyboardType(.numberPad)
      .multilineTextAlignment(.trailing)
      .font(.scoreM)
      .foregroundStyle(.textPrimary)
      .padding(.horizontal, Space.md)
      .frame(width: 88, height: ButtonHeight.medium)
      .background(.neutralFill, in: .rect(cornerRadius: Radius.sm))
      .overlay {
        RoundedRectangle(cornerRadius: Radius.sm)
          .strokeBorder(
            .brandInk, lineWidth: focusedParticipantID.wrappedValue == participant.id ? 2 : 0)
      }
      .focused(focusedParticipantID, equals: participant.id)
  }

  private func textBinding(for participantID: Participant.ID) -> Binding<String> {
    Binding(
      get: { draftTexts[participantID] ?? "" },
      set: { newValue in
        let sign = newValue.hasPrefix("-") ? "-" : ""
        let digits = newValue.filter(\.isNumber)
        let normalized = digits.isEmpty ? sign : sign + digits
        draftTexts[participantID] = normalized
        onScoreChange(participantID, Int(normalized))
      }
    )
  }
}

extension ScoreBoardView {
  /// Doc utilisateur — posée par l'écran appelant sur son propre `.toolbar`, jamais par
  /// `ScoreBoardView` elle-même (voir la note en tête de fichier). Même bouton de signe et même
  /// bouton de validation pour l'hôte et le pair — seul le libellé change (« Terminé »/« Envoyer »).
  @ToolbarContentBuilder
  static func keyboardAccessory(
    allowsNegative: Bool,
    currentParticipantID: Participant.ID?,
    submitLabel: LocalizedStringResource,
    onToggleSign: @escaping (Participant.ID) -> Void,
    onSubmit: @escaping () -> Void
  ) -> some ToolbarContent {
    ToolbarItemGroup(placement: .keyboard) {
      if allowsNegative, let currentParticipantID {
        Button {
          onToggleSign(currentParticipantID)
        } label: {
          Image(systemName: "plusminus")
        }
      }
      Spacer()
      Button(submitLabel) { onSubmit() }
    }
  }

  /// Doc utilisateur — la barre d'accessoires du clavier (juste au-dessus) disparaît avec lui :
  /// sur iPad notamment, le bouton natif de fermeture du clavier laissait l'écran sans aucun
  /// moyen de valider la manche en cours (bug remonté). Ce bouton prend le relais, mais
  /// uniquement quand le clavier est masqué — sinon il doublonne celui déjà présent au-dessus.
  /// Posée par l'écran appelant sur son propre `.safeAreaInset`, jamais par `ScoreBoardView`.
  @ViewBuilder
  static func submitBar(
    isKeyboardVisible: Bool, submitLabel: LocalizedStringResource, onSubmit: @escaping () -> Void
  ) -> some View {
    if !isKeyboardVisible {
      Button(submitLabel) { onSubmit() }
        .buttonStyle(.primary(size: .medium))
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Space.lg)
        .padding(.vertical, Space.sm)
        .background(.bar)
    }
  }
}
