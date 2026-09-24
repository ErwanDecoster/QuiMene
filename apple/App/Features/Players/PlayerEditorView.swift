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
          // Doc 16, phase A — ajouter un ami se fait depuis Profil (« Ajouter un ami ») et,
          // bientôt, en rejoignant sa partie ; mon propre profil se gère sur sa page. Il ne
          // reste ici que le lien d'un ami déjà ajouté, pour pouvoir le retirer.
          if !model.isMyOwnSharedProfile, model.sharedProfileID != nil {
            Section {
              if let linkedName = model.linkedProfileName {
                if let linkedDate = model.linkedProfileDate {
                  Text(
                    "Lié au profil de **\(linkedName)** depuis le \(linkedDate.formatted(date: .abbreviated, time: .omitted))."
                  )
                  .font(.bodySmall)
                  .foregroundStyle(.textSecondary)
                } else {
                  Text("Lié au profil de **\(linkedName)**.")
                    .font(.bodySmall)
                    .foregroundStyle(.textSecondary)
                }
              }
              Button("Retirer des amis", role: .destructive) {
                model.unlinkProfile()
              }
            } header: {
              Text("Ami")
            } footer: {
              Text("Vos prochaines parties jouées ensemble n'arriveront plus dans son historique.")
            }
          }

          // Doc 16, phase A — mon profil ne s'archive ni ne se supprime comme une fiche
          // ordinaire : « Supprimer mon profil » (onglet Profil) en nomme les conséquences.
          if !model.isMyOwnSharedProfile {
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
      }
      .navigationTitle(
        model.isCreatingProfile
          ? "Créer mon profil" : model.isEditing ? "Modifier le joueur" : "Ajouter un joueur")
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
