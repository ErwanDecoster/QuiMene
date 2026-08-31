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
    @State private var pendingProfileLink: ProfileShareLink.Payload?

    init(mode: PlayerEditorMode, context: ModelContext) {
        _model = State(initialValue: PlayerEditorModel(mode: mode, context: context))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Pseudo") {
                    TextField("Pseudo", text: $model.nickname)
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
                            // sienne : ni « Lier un profil reçu » (elle ne peut pas aussi suivre
                            // quelqu'un d'autre), une seule action possible, la délier.
                            VStack(spacing: Space.sm) {
                                QRCodeView(url: shareURL)
                                    .frame(width: 160, height: 160)
                                Text("Fais scanner ce code par l'ami avec qui tu veux partager ton historique, depuis sa propre fiche « Lier un profil reçu ».")
                                    .font(.bodySmall)
                                    .foregroundStyle(.textSecondary)
                                    .multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Space.sm)
                            Button("Ne plus partager", role: .destructive) {
                                model.unlinkProfile()
                            }
                        } else {
                            // Doc 14, phase 4 — cette fiche n'est pas *la mienne* (elle peut déjà
                            // suivre un ami, ou n'être encore ni l'un ni l'autre) : les deux
                            // actions restent possibles, au choix (« c'est moi » vs « je suis en
                            // train de suivre quelqu'un »).
                            if let linkedName = model.linkedProfileName, let linkedDate = model.linkedProfileDate {
                                Text("Liée à **\(linkedName)**, le \(linkedDate.formatted(date: .abbreviated, time: .omitted))")
                                    .font(.bodySmall)
                                    .foregroundStyle(.textSecondary)
                            }
                            Button("Partager ce profil (c'est moi)") {
                                model.ensureSharedProfileID()
                            }
                            if let shareConflictMessage = model.shareConflictMessage {
                                Text(shareConflictMessage)
                                    .font(.bodySmall)
                                    .foregroundStyle(.semanticError)
                            }
                            Button(model.linkedProfileName == nil ? "Lier un profil reçu" : "Lier un autre profil") {
                                isPresentingProfileScanner = true
                            }
                            if model.linkedProfileName != nil {
                                Button("Ne plus suivre ce profil", role: .destructive) {
                                    model.unlinkProfile()
                                }
                            }
                        }
                    } header: {
                        Text("Profil partagé")
                    } footer: {
                        Text("« Partager » désigne cette fiche comme la tienne — une seule par appareil. « Lier » relie cette fiche à celle d'un ami sur son propre appareil : les parties jouées ensemble pourront apparaître dans son historique, sans lui montrer tes autres parties.")
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
                Text("La fiche joueur sera définitivement supprimée. Les parties déjà jouées restent dans l'historique, mais ne pointeront plus vers ce joueur. Cette action ne peut pas être annulée.")
            }
            .task(id: photoPickerItem) {
                guard let photoPickerItem, let data = try? await photoPickerItem.loadTransferable(type: Data.self) else {
                    return
                }
                model.setPhotoData(AvatarPhotoProcessor.process(data))
            }
            .fullScreenCover(isPresented: $isPresentingProfileScanner) {
                profileScannerCover
            }
            // Doc 14, phase 3 « Limites de confiance » — remontée : rien n'affichait le pseudo
            // scanné avant de lier, un scan malencontreux (mauvais code, code retransmis) passait
            // inaperçu. Confirmation systématique, avec le choix d'adopter aussi le pseudo/avatar
            // de la personne représentée.
            .sheet(item: $pendingProfileLink) { payload in
                ConfirmProfileLinkView(
                    payload: payload,
                    conflictingPlayerName: model.conflictingPlayerName(forSharedProfileID: payload.id)
                ) { adoptNameAndAvatar in
                    model.linkProfile(
                        id: payload.id,
                        name: payload.name,
                        avatarKind: payload.avatarKind,
                        avatarValue: payload.avatarValue,
                        paletteID: payload.paletteID,
                        adoptNameAndAvatar: adoptNameAndAvatar
                    )
                    pendingProfileLink = nil
                } onCancel: {
                    pendingProfileLink = nil
                }
            }
        }
    }

    /// Doc 14 « Profils partagés » — même patron que le scanner de code d'appairage
    /// (`JoinTabView`) : caméra plein écran, un bouton de fermeture superposé.
    private var profileScannerCover: some View {
        ZStack(alignment: .topTrailing) {
            QRScannerView { code in
                isPresentingProfileScanner = false
                guard let url = URL(string: code), let payload = ProfileShareLink.parse(url) else { return }
                pendingProfileLink = payload
            }
            .ignoresSafeArea()

            Button {
                isPresentingProfileScanner = false
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(.white, .black.opacity(0.5))
            }
            .padding()
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
    let onConfirm: (_ adoptNameAndAvatar: Bool) -> Void
    let onCancel: () -> Void

    @State private var adoptNameAndAvatar = true

    private var canAdoptAvatar: Bool { payload.avatarKind != "photo" }

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

                if let conflictingPlayerName {
                    Section {
                        Text("Ce profil est déjà lié à la fiche « \(conflictingPlayerName) » sur cet appareil. Continuer liera aussi celle-ci — à ne faire que si c'est la même personne (par exemple une fiche recréée).")
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
            .navigationTitle("Lier ce profil ?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuler") { onCancel() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(conflictingPlayerName == nil ? "Lier" : "Lier quand même") {
                        onConfirm(adoptNameAndAvatar)
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
