import DesignSystem
import PhotosUI
import Store
import SwiftData
import SwiftUI

struct PlayerEditorView: View {
  @Environment(\.dismiss) private var dismiss
  @State private var model: PlayerEditorModel
  @State private var photoPickerItem: PhotosPickerItem?
  @State private var isPresentingDeleteConfirmation = false
  @State private var isPresentingProfileScanner = false
  @FocusState private var isNicknameFocused: Bool

  init(mode: PlayerEditorMode, context: ModelContext) {
    _model = State(initialValue: PlayerEditorModel(mode: mode, context: context))
  }

  var body: some View {
    NavigationStack {
      Form {
        Section("Pseudo") {
          TextField("Pseudo", text: $model.nickname)
            .focused($isNicknameFocused)
        }

        Section("Avatar") {
          Picker("Source", selection: $model.avatarKind) {
            Text("Emoji").tag("emoji")
            Text("Photo").tag("photo")
          }
          .pickerStyle(.segmented)

          avatarSourceEditor
        }

        if model.isEditing {
          Section {
            if let shareURL = model.shareURL {
              // Doc 14, phase 4 — c'est *la* fiche que cet appareil partage comme la
              // sienne : ni « Suivre un profil reçu » (elle ne peut pas aussi suivre
              // quelqu'un d'autre), une seule action possible, la délier.
              VStack(spacing: Space.sm) {
                QRCodeView(url: shareURL)
                  .frame(width: 160, height: 160)
                Text(
                  "Fais scanner ce code par l'ami avec qui tu veux partager ton historique, depuis sa propre fiche « Suivre un profil reçu »."
                )
                .font(.bodySmall)
                .foregroundStyle(.textSecondary)
                .multilineTextAlignment(.center)
              }
              .frame(maxWidth: .infinity)
              .padding(.vertical, Space.sm)
              Button("Ne plus partager", role: .destructive) {
                model.unlinkProfile()
              }
            } else if let linkedName = model.linkedProfileName,
              let linkedDate = model.linkedProfileDate
            {
              // Doc 14, phase 4 — remontée : cette fiche suit déjà un ami, elle ne
              // doit plus pouvoir être partagée comme si c'était la mienne (elle
              // rediffuserait l'identité de l'ami, pas la sienne propre) — pas de
              // bouton « Partager » ici, seulement « Suivre quelqu'un d'autre » ou
              // délier.
              // Doc utilisateur — remontée : « Lier un autre profil » ne laissait pas
              // deviner qu'il s'agit d'une action importante (elle remplace le suivi
              // actuel) ; le nom de la personne actuellement suivie est maintenant
              // rappelé directement dans le bouton, avant même d'ouvrir le scanner.
              Text(
                "Tu suis **\(linkedName)**, depuis le \(linkedDate.formatted(date: .abbreviated, time: .omitted))"
              )
              .font(.bodySmall)
              .foregroundStyle(.textSecondary)
              Button("Suivre quelqu'un d'autre (remplace \(linkedName))") {
                isPresentingProfileScanner = true
              }
              Button("Ne plus suivre ce profil", role: .destructive) {
                model.unlinkProfile()
              }
            } else {
              // Doc 14, phase 4 — fiche encore vierge : les deux choix restent
              // possibles, au choix (« c'est moi » vs « je suis en train de suivre
              // quelqu'un »).
              Button("Partager ce profil (c'est moi)") {
                model.ensureSharedProfileID()
              }
              if let shareConflictMessage = model.shareConflictMessage {
                Text(shareConflictMessage)
                  .font(.bodySmall)
                  .foregroundStyle(.semanticError)
              }
              Button("Suivre un profil reçu") {
                isPresentingProfileScanner = true
              }
            }
          } header: {
            Text("Profil partagé")
          } footer: {
            // Doc utilisateur — remontée : la phrase précédente (deux notions denses
            // dans une seule phrase à tiret) était difficile à suivre. Une phrase par
            // action, à l'impératif comme les boutons eux-mêmes.
            Text(
              "Partage ta fiche pour que tes amis puissent te suivre. Suis un ami pour retrouver vos parties jouées ensemble dans son historique."
            )
          }

          Section {
            if model.isArchivedPlayer {
              Button("Supprimer ce joueur", role: .destructive) {
                isPresentingDeleteConfirmation = true
              }
            } else {
              Button("Archiver ce joueur", role: .destructive) {
                try? model.archive()
                dismiss()
              }
            }
          }
        }
      }
      .navigationTitle(model.isEditing ? "Modifier le joueur" : "Ajouter un joueur")
      .onAppear {
        // Doc utilisateur — remontée : à la création d'un joueur, le champ de saisie du
        // pseudo doit déjà être prêt à recevoir la frappe, pas seulement affiché. Pas au
        // moment de modifier un joueur existant : ouvrirait le clavier sans y avoir été
        // invité, pour une fiche déjà remplie.
        if !model.isEditing {
          isNicknameFocused = true
        }
      }
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Annuler") { dismiss() }
        }
        ToolbarItem(placement: .confirmationAction) {
          Button("Enregistrer") {
            try? model.save()
            dismiss()
          }
          .disabled(!model.canSave)
        }
      }
      .confirmationDialog(
        "Supprimer définitivement ce joueur ?",
        isPresented: $isPresentingDeleteConfirmation,
        titleVisibility: .visible
      ) {
        Button("Supprimer", role: .destructive) {
          try? model.delete()
          dismiss()
        }
      } message: {
        Text(
          "La fiche joueur sera définitivement supprimée. Les parties déjà jouées restent dans l'historique, mais ne pointeront plus vers ce joueur. Cette action ne peut pas être annulée."
        )
      }
      .task(id: photoPickerItem) {
        guard let photoPickerItem,
          let data = try? await photoPickerItem.loadTransferable(type: Data.self)
        else {
          return
        }
        model.setPhotoData(AvatarPhotoProcessor.process(data))
      }
      // Doc 14, phase 4 — remontée : scanner puis confirmer utilisait deux présentations
      // système distinctes (plein écran, puis feuille) — les enchaîner dans le même geste
      // faisait sensiblement traîner la transition (SwiftUI attend que la première se
      // referme avant d'ouvrir la seconde). Une seule présentation, dont le contenu bascule
      // en interne entre scan et confirmation, plus rien à attendre entre les deux.
      .fullScreenCover(isPresented: $isPresentingProfileScanner) {
        ProfileLinkScanFlow(
          currentlyLinkedID: model.sharedProfileID,
          currentlyLinkedName: model.linkedProfileName,
          conflictingPlayerName: { model.conflictingPlayerName(forSharedProfileID: $0) }
        ) { payload, adoptNameAndAvatar in
          model.linkProfile(
            id: payload.id,
            name: payload.name,
            avatarKind: payload.avatarKind,
            avatarValue: payload.avatarValue,
            paletteID: payload.paletteID,
            adoptNameAndAvatar: adoptNameAndAvatar
          )
        }
      }
    }
  }

  @ViewBuilder
  private var avatarSourceEditor: some View {
    switch model.avatarKind {
    case "photo":
      PhotosPicker("Choisir une photo", selection: $photoPickerItem, matching: .images)
      if let photoData = model.photoData, let uiImage = UIImage(data: photoData) {
        Image(uiImage: uiImage)
          .resizable()
          .scaledToFill()
          .frame(width: 96, height: 96)
          .clipShape(Circle())
      }
    default:
      VStack(spacing: Space.md) {
        AvatarView(
          avatar: Avatar(
            kind: .emoji(model.emojiValue),
            palette: PlayerPalette(index: Int(model.paletteID) ?? 1)
          ),
          size: .large
        )

        if model.hasManualAvatarOverride {
          Button("Réinitialiser l'avatar généré") {
            model.resetToGeneratedAvatar()
          }
          .buttonStyle(.tertiary(size: .small))
        } else {
          Text("Dérivé automatiquement du pseudo — modifiable ci-dessous.")
            .font(.label)
            .foregroundStyle(.textSecondary)
            .multilineTextAlignment(.center)
        }

        emojiGrid
        paletteRow
      }
      .frame(maxWidth: .infinity)
      .padding(.vertical, Space.sm)
    }
  }

  private var emojiGrid: some View {
    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: Space.sm) {
      ForEach(Avatar.curatedEmoji, id: \.self) { emoji in
        Button {
          model.selectEmoji(emoji)
        } label: {
          Text(emoji)
            .font(.system(size: 28))
            .frame(width: 40, height: 40)
            .background(
              emoji == model.emojiValue ? Color.brandInk.opacity(0.12) : Color.clear,
              in: .rect(cornerRadius: Radius.sm)
            )
        }
        .buttonStyle(.plain)
        // Doc 08 « Accessibilité » — le fond coloré qui marque la sélection n'est sinon jamais
        // annoncé à VoiceOver.
        .accessibilityAddTraits(emoji == model.emojiValue ? .isSelected : [])
      }
    }
  }

  private var paletteRow: some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: Space.sm) {
        ForEach(1...10, id: \.self) { index in
          let palette = PlayerPalette(index: index)
          Button {
            model.selectPalette(String(index))
          } label: {
            Circle()
              .fill(palette.color)
              .frame(width: 32, height: 32)
              .overlay {
                if String(index) == model.paletteID {
                  Circle().strokeBorder(.textPrimary, lineWidth: 2)
                }
              }
          }
          .buttonStyle(.plain)
          .accessibilityLabel(palette.accessibilityName)
        }
      }
      .padding(.horizontal, Space.xs)
    }
  }
}

/// Doc 14, phase 3 « Limites de confiance » — dernier moment où un scan malencontreux (mauvais
/// code, code retransmis par quelqu'un d'autre que la personne concernée) peut encore être
/// rattrapé : montre qui le lien prétend représenter avant d'écrire quoi que ce soit, avec le
/// choix d'adopter aussi son pseudo/avatar plutôt que de garder ceux déjà choisis sur cette fiche.
private struct ConfirmProfileLinkView: View {
  let payload: ProfileShareLink.Payload
  /// Non-`nil` si un identifiant déjà utilisé par une *autre* fiche locale — probablement une
  /// erreur (cette fiche-ci et l'autre représenteraient alors la même personne), mais pas
  /// bloqué en dur : peut arriver légitimement après une fiche recréée.
  let conflictingPlayerName: String?
  /// Doc utilisateur — remontée : re-lier une fiche déjà liée à un ami (Théo) vers un autre
  /// profil (Marie) remplaçait le lien existant sans le dire — Théo continuait de croire
  /// recevoir les parties jouées avec cette fiche. Non-`nil` uniquement quand ce lien change
  /// vraiment de personne : rescanner le code de la même personne (pour rafraîchir son pseudo
  /// ou son avatar) ne déclenche pas cet avertissement — voir `ProfileLinkScanFlow`.
  let existingLinkName: String?
  let onConfirm: (_ adoptNameAndAvatar: Bool) -> Void
  let onCancel: () -> Void

  @State private var adoptNameAndAvatar = true
  /// Doc utilisateur — remontée : rien n'indiquait qu'un tap sur « Suivre » avait été pris en
  /// compte, ce qui pouvait se lire comme un écran figé. `Task { @MainActor in }` cède la main
  /// une fois avant d'appeler `onConfirm` (potentiellement bloquant — écriture SwiftData) pour
  /// laisser SwiftUI le temps d'afficher cet indicateur avant que le travail ne démarre.
  @State private var isLinking = false

  private var canAdoptAvatar: Bool { payload.avatarKind != "photo" }

  private var confirmButtonTitle: String {
    if existingLinkName != nil { return "Remplacer le lien" }
    if conflictingPlayerName != nil { return "Suivre quand même" }
    return "Suivre"
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          VStack(spacing: Space.md) {
            if canAdoptAvatar {
              AvatarView(
                avatar: Avatar(
                  kind: .emoji(payload.avatarValue),
                  palette: PlayerPalette(index: Int(payload.paletteID) ?? 1)
                ),
                size: .large
              )
            }
            Text(payload.name).font(.h4).foregroundStyle(.textPrimary)
          }
          .frame(maxWidth: .infinity)
          .padding(.vertical, Space.sm)
        }

        if let existingLinkName {
          Section {
            Text(
              "Cette fiche suit actuellement **\(existingLinkName)**. Continuer la fera suivre **\(payload.name)** à la place : \(existingLinkName) ne recevra plus les parties jouées avec cette fiche."
            )
            .font(.bodySmall)
            .foregroundStyle(.semanticError)
          }
        }

        if let conflictingPlayerName {
          Section {
            Text(
              "Ce profil est déjà suivi par la fiche « \(conflictingPlayerName) » sur cet appareil. Continuer le fera suivre aussi par celle-ci — à ne faire que si c'est la même personne (par exemple une fiche recréée)."
            )
            .font(.bodySmall)
            .foregroundStyle(.semanticError)
          }
        }

        Section {
          Toggle("Adopter aussi son pseudo et son avatar", isOn: $adoptNameAndAvatar)
            .tint(.brandInk)
          if !canAdoptAvatar {
            Text("Son avatar est une photo : seul le pseudo peut être repris.")
              .font(.label)
              .foregroundStyle(.textTertiary)
          }
        }
      }
      .navigationTitle("Suivre ce profil ?")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Annuler") { onCancel() }
            .disabled(isLinking)
        }
        ToolbarItem(placement: .confirmationAction) {
          if isLinking {
            ProgressView()
          } else {
            Button(confirmButtonTitle) {
              isLinking = true
              Task { @MainActor in
                onConfirm(adoptNameAndAvatar)
              }
            }
          }
        }
      }
    }
  }
}

/// Doc 14, phase 4 — remontée : scanner puis confirmer utilisait deux présentations système
/// distinctes (plein écran, puis feuille) — les enchaîner faisait parfois sensiblement traîner la
/// transition. Un seul plein écran, dont le contenu bascule en interne entre scan et
/// confirmation : plus de seconde présentation système à attendre.
private struct ProfileLinkScanFlow: View {
  /// Identifiant et nom du lien déjà en place sur cette fiche, le cas échéant (doc utilisateur —
  /// remontée : re-lier une fiche déjà liée à un ami vers un autre profil le remplaçait sans
  /// avertissement). `currentlyLinkedID` sert à distinguer un vrai changement de personne d'un
  /// simple rescan du même code.
  let currentlyLinkedID: UUID?
  let currentlyLinkedName: String?
  let conflictingPlayerName: (UUID) -> String?
  let onConfirm: (ProfileShareLink.Payload, _ adoptNameAndAvatar: Bool) -> Void

  @Environment(\.dismiss) private var dismiss
  @State private var scannedPayload: ProfileShareLink.Payload?

  var body: some View {
    if let scannedPayload {
      ConfirmProfileLinkView(
        payload: scannedPayload,
        conflictingPlayerName: conflictingPlayerName(scannedPayload.id),
        existingLinkName: scannedPayload.id == currentlyLinkedID ? nil : currentlyLinkedName
      ) { adoptNameAndAvatar in
        onConfirm(scannedPayload, adoptNameAndAvatar)
        dismiss()
      } onCancel: {
        dismiss()
      }
    } else {
      ZStack(alignment: .topTrailing) {
        QRScannerView { code in
          guard let url = URL(string: code), let payload = ProfileShareLink.parse(url) else {
            return
          }
          scannedPayload = payload
        }
        .ignoresSafeArea()

        Button {
          dismiss()
        } label: {
          Image(systemName: "xmark.circle.fill")
            .font(.system(size: 32))
            .foregroundStyle(.white, .black.opacity(0.5))
        }
        .padding()
      }
    }
  }
}
