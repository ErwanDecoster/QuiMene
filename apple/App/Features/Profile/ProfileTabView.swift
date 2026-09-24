import DesignSystem
import Domain
import Store
import SwiftData
import SwiftUI

/// Doc 16, phase A — onglet Profil. « Mon profil » est la fiche joueur que cet appareil désigne
/// comme la sienne (`PlayerRecord.sharedProfileIsMine`, doc 14 phase 4) : pas de modèle à part,
/// pour que l'utilisateur reste un joueur comme les autres dans ses propres parties. Regroupe
/// ce qui le concerne lui : identité, QR pour être ajouté, statistiques, amis liés, et les
/// accès qui n'ont pas leur place ailleurs (rejoindre une partie, réglages).
struct ProfileTabView: View {
  @Environment(\.modelContext) private var modelContext
  @Environment(DeepLinkRouter.self) private var deepLinkRouter
  @Query(sort: \PlayerRecord.sortIndex) private var allPlayers: [PlayerRecord]
  @State private var isPresentingProfileCreation = false
  @State private var isPresentingProfileEditor = false
  @State private var isPresentingAddFriend = false
  @State private var isPresentingSettings = false
  @State private var isConfirmingProfileDeletion = false

  private var me: PlayerRecord? { allPlayers.first { $0.sharedProfileIsMine } }

  private var friends: [PlayerRecord] {
    allPlayers.filter { $0.sharedProfileID != nil && !$0.sharedProfileIsMine && !$0.isArchived }
  }

  var body: some View {
    NavigationStack {
      List {
        if let me {
          profileSections(me)
        } else {
          Section {
            ProfileIntroView(
              onCreate: { isPresentingProfileCreation = true }
            )
          }
        }

        Section {
          Button {
            deepLinkRouter.isPresentingJoin = true
          } label: {
            Label("Rejoindre une partie", systemImage: "qrcode.viewfinder")
          }
          Button {
            isPresentingSettings = true
          } label: {
            Label("Réglages", systemImage: "gearshape")
          }
        }

        if me != nil {
          Section {
            Button("Supprimer mon profil", role: .destructive) {
              isConfirmingProfileDeletion = true
            }
          }
        }
      }
      .navigationTitle("Profil")
      .confirmationDialog(
        "Supprimer ton profil ?",
        isPresented: $isConfirmingProfileDeletion,
        titleVisibility: .visible
      ) {
        Button("Supprimer mon profil", role: .destructive) {
          if let me {
            try? PlayerRepository(context: modelContext).unlinkSharedProfile(for: me)
          }
        }
      } message: {
        Text(
          "Tes amis ne recevront plus les parties jouées avec toi. Ta fiche et son historique sont conservés, et tu créeras ensuite un nouveau profil."
        )
      }
      .sheet(isPresented: $isPresentingProfileCreation) {
        PlayerEditorView(mode: .createProfile, context: modelContext)
      }
      .sheet(isPresented: $isPresentingProfileEditor) {
        if let me {
          PlayerEditorView(mode: .edit(me), context: modelContext)
        }
      }
      .sheet(isPresented: $isPresentingSettings) {
        SettingsView()
      }
      .fullScreenCover(isPresented: $isPresentingAddFriend) {
        AddFriendFlow()
      }
    }
  }

  @ViewBuilder
  private func profileSections(_ me: PlayerRecord) -> some View {
    Section {
      HStack(spacing: Space.lg) {
        AvatarView(avatar: me.avatar, size: .large)
        VStack(alignment: .leading, spacing: Space.xs) {
          Text(me.nickname)
            .font(.h3)
            .foregroundStyle(.textPrimary)
          Button("Modifier le profil") {
            isPresentingProfileEditor = true
          }
          .font(.label)
          .buttonStyle(.borderless)
        }
        Spacer(minLength: 0)
      }
      .padding(.vertical, Space.sm)

      NavigationLink {
        MyProfileQRView(player: me)
      } label: {
        Label("M'ajouter comme ami", systemImage: "qrcode")
      }
      NavigationLink {
        ProfileView(player: me)
      } label: {
        Label("Mes statistiques", systemImage: "chart.bar")
      }
    }

    Section {
      if friends.isEmpty {
        Text("Aucun ami lié pour l'instant.")
          .font(.bodySmall)
          .foregroundStyle(.textSecondary)
      } else {
        ForEach(friends) { friend in
          NavigationLink {
            ProfileView(player: friend)
          } label: {
            HStack(spacing: Space.md) {
              AvatarView(avatar: friend.avatar, size: .small)
              Text(friend.nickname)
                .font(.bodyText)
                .foregroundStyle(.textPrimary)
            }
          }
        }
      }
      Button {
        isPresentingAddFriend = true
      } label: {
        Label("Ajouter un ami", systemImage: "person.badge.plus")
      }
    } header: {
      Text("Amis")
    } footer: {
      Text("Vos parties jouées ensemble apparaissent dans vos deux historiques.")
    }
  }
}

/// Présentation du profil quand il n'existe pas encore — partagée par l'onglet Profil et
/// l'écran du premier lancement, pour que les deux disent exactement la même chose.
private struct ProfileIntroView: View {
  let onCreate: () -> Void

  var body: some View {
    VStack(spacing: Space.lg) {
      Image(systemName: "person.crop.circle.badge.plus")
        .font(.system(size: 56))
        .foregroundStyle(.brandInk)
        .accessibilityHidden(true)
      Text("Créer mon profil")
        .font(.h3)
        .foregroundStyle(.textPrimary)
      VStack(alignment: .leading, spacing: Space.sm) {
        benefit("person.2", "Rejoindre les parties de tes amis et y être reconnu.")
        benefit("clock.arrow.circlepath", "Retrouver vos parties jouées ensemble dans ton historique.")
        benefit("lock", "Un pseudo et un avatar, rien de plus : aucun compte à créer.")
      }
      Button {
        onCreate()
      } label: {
        Text("Créer mon profil").frame(maxWidth: .infinity)
      }
      .buttonStyle(.borderedProminent)
      .controlSize(.large)
      .tint(.brandInk)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, Space.lg)
  }

  private func benefit(_ icon: String, _ text: LocalizedStringKey) -> some View {
    Label {
      Text(text).font(.bodySmall).foregroundStyle(.textSecondary)
    } icon: {
      Image(systemName: icon).foregroundStyle(.brandInk)
    }
  }
}

/// Doc 16, phase A — le profil est obligatoire : tant qu'aucune fiche n'est « la mienne », cet
/// écran recouvre l'app (`ProfileRequirement`) et se referme de lui-même dès qu'elle existe —
/// créée ici, ou arrivée d'un autre appareil par iCloud.
struct ProfileRequirement: ViewModifier {
  @Query(filter: #Predicate<PlayerRecord> { $0.sharedProfileIsMine })
  private var myProfiles: [PlayerRecord]

  func body(content: Content) -> some View {
    content.fullScreenCover(isPresented: .constant(myProfiles.isEmpty)) {
      ProfileOnboardingView()
    }
  }
}

struct ProfileOnboardingView: View {
  @Environment(\.modelContext) private var modelContext
  @Environment(AppSettings.self) private var settings
  @State private var isPresentingCreation = false

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: Space.lg) {
          ProfileIntroView(
            onCreate: { isPresentingCreation = true }
          )
          if settings.iCloudSyncEnabled {
            Text(
              "Déjà un profil sur un autre appareil ? Avec iCloud, il arrive tout seul d'ici quelques instants."
            )
            .font(.label)
            .foregroundStyle(.textTertiary)
            .multilineTextAlignment(.center)
          }
        }
        .padding(Space.lg)
      }
      .background(.neutralBg)
    }
    .interactiveDismissDisabled()
    .sheet(isPresented: $isPresentingCreation) {
      PlayerEditorView(mode: .createProfile, context: modelContext)
    }
  }
}

/// QR à faire scanner par un ami (« Ajouter un ami » de son onglet Profil) : le lie à cette
/// fiche sur son appareil, pour que vos parties communes arrivent dans vos deux historiques.
private struct MyProfileQRView: View {
  let player: PlayerRecord

  private var url: URL? {
    guard let id = player.sharedProfileID else { return nil }
    return ProfileShareLink.url(
      id: id, name: player.nickname, avatarKind: player.avatarKind,
      avatarValue: player.avatarValue, paletteID: player.paletteID)
  }

  var body: some View {
    ScrollView {
      VStack(spacing: Space.xl) {
        AvatarView(avatar: player.avatar, size: .large)
        Text(player.nickname)
          .font(.h3)
          .foregroundStyle(.textPrimary)
        if let url {
          QRCodeView(url: url)
            .frame(width: 240, height: 240)
            .accessibilityLabel("Code QR de ton profil")
        }
        Text(
          "Fais scanner ce code par un ami, depuis « Ajouter un ami » dans son onglet Profil. Vos parties jouées ensemble apparaîtront dans vos deux historiques."
        )
        .font(.bodySmall)
        .foregroundStyle(.textSecondary)
        .multilineTextAlignment(.center)
        .frame(maxWidth: 360)
      }
      .frame(maxWidth: .infinity)
      .padding(Space.xl)
    }
    .background(.neutralBg)
    .navigationTitle("M'ajouter comme ami")
    .navigationBarTitleDisplayMode(.inline)
  }
}

/// « Ajouter un ami » — scanner son QR, puis dire qui il est dans mes joueurs : une fiche
/// existante (celle que j'utilise déjà pour lui) ou une nouvelle, créée avec son pseudo et son
/// avatar. Un seul plein écran dont le contenu bascule, comme `ProfileLinkScanFlow`.
private struct AddFriendFlow: View {
  @Environment(\.modelContext) private var modelContext
  @Environment(\.dismiss) private var dismiss
  @Query(sort: \PlayerRecord.sortIndex) private var allPlayers: [PlayerRecord]
  @State private var payload: ProfileShareLink.Payload?

  var body: some View {
    if let payload {
      chooseFiche(for: payload)
    } else {
      ZStack(alignment: .topTrailing) {
        QRScannerView { code in
          guard let url = URL(string: code), let scanned = ProfileShareLink.parse(url) else {
            return
          }
          payload = scanned
        }
        .ignoresSafeArea()

        Button {
          dismiss()
        } label: {
          Image(systemName: "xmark.circle.fill")
            .font(.system(size: 32))
            .foregroundStyle(.white, .black.opacity(0.5))
        }
        .accessibilityLabel("Fermer")
        .padding()
      }
    }
  }

  private func chooseFiche(for payload: ProfileShareLink.Payload) -> some View {
    let isMe = allPlayers.contains { $0.sharedProfileIsMine && $0.sharedProfileID == payload.id }
    let alreadyLinked = allPlayers.first {
      !$0.sharedProfileIsMine && $0.sharedProfileID == payload.id
    }
    // Fiches actives qui ne représentent encore personne (ni moi, ni un ami déjà lié).
    let candidates = allPlayers.filter { !$0.isArchived && $0.sharedProfileID == nil }

    return NavigationStack {
      List {
        Section {
          VStack(spacing: Space.sm) {
            AvatarView(avatar: Self.avatar(for: payload), size: .large)
            Text(payload.name).font(.h4).foregroundStyle(.textPrimary)
          }
          .frame(maxWidth: .infinity)
          .padding(.vertical, Space.sm)
        }

        if isMe {
          Section {
            Text("C'est ton propre profil.").foregroundStyle(.textSecondary)
          }
        } else if let alreadyLinked {
          Section {
            Text("\(payload.name) est déjà ton ami, sur la fiche « \(alreadyLinked.nickname) ».")
              .foregroundStyle(.textSecondary)
          }
        } else {
          Section {
            Button {
              createFiche(for: payload)
            } label: {
              Label("Créer la fiche « \(payload.name) »", systemImage: "person.crop.circle.badge.plus")
            }
          } footer: {
            Text("Nouvelle fiche avec son pseudo et son avatar.")
          }

          if !candidates.isEmpty {
            Section("Ou c'est une de mes fiches") {
              ForEach(candidates) { player in
                Button {
                  link(payload, to: player)
                } label: {
                  HStack(spacing: Space.md) {
                    AvatarView(avatar: player.avatar, size: .medium)
                    Text(player.nickname)
                      .font(.bodyText)
                      .foregroundStyle(.textPrimary)
                  }
                }
              }
            }
          }
        }
      }
      .navigationTitle("Ajouter un ami")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button(isMe || alreadyLinked != nil ? "Fermer" : "Annuler") { dismiss() }
        }
      }
    }
  }

  /// Un avatar photo ne transite pas par le QR (`ProfileShareLink`) : repli sur l'avatar dérivé
  /// du pseudo, comme pour toute nouvelle fiche.
  private static func avatar(for payload: ProfileShareLink.Payload) -> Avatar {
    let emoji: String
    if payload.avatarKind == "emoji", !payload.avatarValue.isEmpty {
      emoji = payload.avatarValue
    } else if case .emoji(let value) = Avatar.generated(for: payload.name).kind {
      emoji = value
    } else {
      emoji = Avatar.curatedEmoji.first ?? "🙂"
    }
    return Avatar(kind: .emoji(emoji), palette: PlayerPalette(index: Int(payload.paletteID) ?? 1))
  }

  private func createFiche(for payload: ProfileShareLink.Payload) {
    let repository = PlayerRepository(context: modelContext)
    let avatar = Self.avatar(for: payload)
    guard case .emoji(let emoji) = avatar.kind,
      let player = try? repository.create(
        nickname: payload.name, avatarKind: "emoji", avatarValue: emoji,
        paletteID: payload.paletteID)
    else { return }
    link(payload, to: player)
  }

  private func link(_ payload: ProfileShareLink.Payload, to player: PlayerRecord) {
    try? PlayerRepository(context: modelContext).linkSharedProfile(
      payload.id, name: payload.name, for: player)
    dismiss()
  }
}
