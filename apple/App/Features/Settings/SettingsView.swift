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

        Section {
          Picker("Tri des joueurs", selection: $settings.playerSortMode) {
            Text("Automatique").tag(AppSettings.PlayerSortMode.automatic)
            Text("Manuel").tag(AppSettings.PlayerSortMode.manual)
          }
        } footer: {
          Text(
            "Automatique : les joueurs les plus actifs (nombre de parties jouées) en premier. Manuel : réordonne-les toi-même dans l'onglet Joueurs."
          )
        }

        // Doc utilisateur (audit qualité, 15) — iOS ne permet pas à une app tierce de changer sa
        // propre langue en direct : le seul levier est le sélecteur système par app (Réglages >
        // Qui Mène ? > Langue), qui n'existe que parce que le projet déclare plusieurs langues
        // (`knownRegions`). Ce bouton ouvre directement cette page plutôt que de laisser deviner
        // où chercher dans l'app Réglages.
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

  /// Doc utilisateur — `Bundle.main.preferredLocalizations` (même résolution que
  /// `GameDefinition.LocalizedText.localized`) plutôt que `Locale.current`, pour refléter le
  /// réglage par app plutôt que la langue système. `Locale.current.localizedString(forLanguageCode:)`
  /// donne le nom dans la langue *actuellement affichée* — cohérent avec le reste de l'écran.
  private var currentLanguageDisplayName: String {
    let code = Bundle.main.preferredLocalizations.first ?? "fr"
    return Locale.current.localizedString(forLanguageCode: code)?.capitalized(with: Locale.current)
      ?? code
  }
}
