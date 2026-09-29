import DesignSystem
import Store
import SwiftUI
import UIKit

/// Doc 03 : « La sync est désactivable : un utilisateur qui refuse iCloud garde une app
/// pleinement fonctionnelle. Le basculement recrée le `ModelContainer` ; ce n'est pas une
/// migration. » — géré par `QuiMeneApp`, qui observe `AppSettings.iCloudSyncEnabled`.
struct SettingsView: View {
  @Environment(AppSettings.self) private var settings
  @Environment(\.dismiss) private var dismiss
  @State private var showsNoMailClientAlert = false

  var body: some View {
    NavigationStack {
      @Bindable var settings = settings
      Form {
        Section {
          Toggle("Synchronisation iCloud", isOn: $settings.iCloudSyncEnabled)
            .tint(.brandInk)
        } footer: {
          Text(
            "Synchronise les joueurs et les parties entre tes appareils via iCloud. Désactivée, l'app reste pleinement fonctionnelle en local. Le changement s'applique au prochain lancement de l'app."
          )
        }

        // IOS ne permet pas à une app tierce de changer sa propre langue en direct : le seul levier
        // est le sélecteur système par app (Réglages > Qui Mène ? > Langue), qui n'existe que parce
        // que le projet déclare plusieurs langues (`knownRegions`). Ce bouton ouvre directement
        // cette page plutôt que de laisser deviner où chercher dans l'app Réglages.
        Section {
          Button {
            if let url = URL(string: UIApplication.openSettingsURLString) {
              UIApplication.shared.open(url)
            }
          } label: {
            HStack {
              Text("Langue")
                .foregroundStyle(.textPrimary)
              Spacer()
              Text(currentLanguageDisplayName)
                .foregroundStyle(.textSecondary)
            }
          }
        } footer: {
          Text("Ouvre les réglages système pour choisir la langue de l'app.")
        }

        Section {
          Button {
            requestGame()
          } label: {
            HStack {
              Text("Demander l'ajout d'un jeu")
                .foregroundStyle(.textPrimary)
              Spacer()
              Image(systemName: "envelope")
                .foregroundStyle(.textSecondary)
            }
          }
        } footer: {
          Text("Suggère un jeu à ajouter à l'app par e-mail.")
        }

        // App Store, règle 5.1.1(i) — la politique de confidentialité doit être accessible depuis
        // l'app elle-même, pas seulement depuis la fiche App Store.
        Section {
          websiteLink("Assistance", systemImage: "questionmark.circle", url: Self.supportURL)
          websiteLink(
            "Politique de confidentialité", systemImage: "hand.raised", url: Self.privacyURL)
        } footer: {
          Text("Ouvre la page dans ton navigateur.")
        }
      }
      .navigationTitle("Réglages")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Fermer") { dismiss() }
        }
      }
      .gameRequestMailFallback(isPresented: $showsNoMailClientAlert)
    }
  }

  private func requestGame() {
    if !GameRequestMail.open() {
      showsNoMailClientAlert = true
    }
  }

  private func websiteLink(
    _ title: LocalizedStringKey, systemImage: String, url: URL
  ) -> some View {
    Link(destination: url) {
      HStack {
        Text(title)
          .foregroundStyle(.textPrimary)
        Spacer()
        Image(systemName: systemImage)
          .foregroundStyle(.textSecondary)
      }
    }
  }

  /// Le site (`website/`) n'existe qu'en français et en anglais : les autres langues de l'app
  /// ouvrent la version anglaise.
  private static var websiteIsFrench: Bool {
    Bundle.main.preferredLocalizations.first == "fr"
  }

  private static var supportURL: URL {
    URL(
      string: websiteIsFrench
        ? "https://quimene.vercel.app/assistance" : "https://quimene.vercel.app/en/support")!
  }

  private static var privacyURL: URL {
    URL(
      string: websiteIsFrench
        ? "https://quimene.vercel.app/confidentialite" : "https://quimene.vercel.app/en/privacy")!
  }

  /// `Bundle.main.preferredLocalizations` (même résolution que
  /// `GameDefinition.LocalizedText.localized`) plutôt que `Locale.current`, pour refléter le
  /// réglage par app plutôt que la langue système.
  /// `Locale.current.localizedString(forLanguageCode:)` donne le nom dans la langue *actuellement
  /// affichée* — cohérent avec le reste de l'écran.
  private var currentLanguageDisplayName: String {
    let code = Bundle.main.preferredLocalizations.first ?? "fr"
    return Locale.current.localizedString(forLanguageCode: code)?.capitalized(with: Locale.current)
      ?? code
  }
}
