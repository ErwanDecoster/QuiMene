import Catalog
import DesignSystem
import Domain
import Store
import SwiftUI
import Sync
import UIKit

/// Écran « Rejoindre », présenté en plein écran depuis la racine (`DeepLinkRouter.isPresentingJoin`)
/// — c'était un onglet jusqu'à la doc 16 (phase A), où Profil l'a remplacé. Doc utilisateur :
/// réduire le nombre de taps pour rejoindre une partie — l'ouvrir affiche directement la caméra,
/// prête à scanner. « Fermer » masque l'écran sans quitter la partie suivie (bandeau de reprise
/// dans Jeux) ; seul « Quitter la partie » déconnecte.
///
/// Le rôle n'est plus choisi ici : ce n'est pas à la personne qui rejoint de décider si elle peut
/// modifier la partie, mais à l'hôte (`ShareSessionView`, « Autoriser les contributeurs »). On
/// demande donc toujours le rôle le plus capable (`.contributor`) — l'hôte le rétrograde en
/// observateur si besoin (`MatchConnectionCoordinator.rejoin`, doc 09 « l'hôte peut assigner un
/// rôle différent de celui demandé »), et `SharedMatchView` reflète déjà le rôle réellement
/// accordé une fois connecté.
struct JoinTabView: View {
  @Environment(DeepLinkRouter.self) private var deepLinkRouter
  @Environment(\.dismiss) private var dismiss
  @Environment(\.modelContext) private var modelContext
  private var coordinator: MatchConnectionCoordinator { .shared }
  @State private var pairingCode = ""
  @State private var isConnecting = false
  @State private var connectionError: String?
  @State private var isPresentingManualCode = false

  var body: some View {
    NavigationStack {
      Group {
        if let sharedModel = coordinator.sharedModel {
          SharedMatchView(model: sharedModel, onReconnect: reconnect)
            .toolbar {
              ToolbarItem(placement: .cancellationAction) {
                Button("Fermer") { dismiss() }
              }
              ToolbarItem(placement: .primaryAction) {
                Button("Quitter la partie", role: .destructive) {
                  Task { await coordinator.stop() }
                }
              }
            }
        } else {
          scannerView
            .toolbar {
              ToolbarItem(placement: .cancellationAction) {
                Button("Fermer") { dismiss() }
              }
            }
        }
      }
    }
    .onAppear { consumePendingJoin() }
    .onChange(of: deepLinkRouter.pendingJoin) { _, _ in consumePendingJoin() }
  }

  private var scannerView: some View {
    ZStack(alignment: .bottom) {
      QRScannerView { code in
        guard let url = URL(string: code), let payload = JoinLink.parse(url) else { return }
        pairingCode = payload.pairingCode
        Task { await connect() }
      }
      .ignoresSafeArea()

      VStack(spacing: Space.sm) {
        if let connectionError {
          Text(connectionError)
            .font(.label)
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, Space.md)
            .padding(.vertical, Space.sm)
            .background(.black.opacity(0.6), in: .rect(cornerRadius: Radius.sm))
        }
        Button(isConnecting ? "Connexion…" : "Saisir un code") {
          isPresentingManualCode = true
        }
        .buttonStyle(.primary(size: .medium))
        .disabled(isConnecting)
      }
      .padding(.horizontal, Space.lg)
      .padding(.bottom, Space.lg)
    }
    .navigationTitle("Rejoindre")
    .navigationBarTitleDisplayMode(.inline)
    .sheet(isPresented: $isPresentingManualCode) {
      manualCodeSheet
    }
  }

  private var manualCodeSheet: some View {
    NavigationStack {
      Form {
        Section("Code d'appairage") {
          TextField("6 chiffres", text: $pairingCode)
            .keyboardType(.numberPad)
            .font(.system(.title2, design: .rounded, weight: .semibold))
            .monospacedDigit()
            .onChange(of: pairingCode) { _, newValue in
              pairingCode = String(newValue.filter(\.isNumber).prefix(6))
            }
        }
        if let connectionError {
          Text(connectionError).font(.label).foregroundStyle(.semanticError)
        }
        Section {
          Button(isConnecting ? "Connexion…" : "Rejoindre") {
            Task {
              await connect()
              if connectionError == nil { isPresentingManualCode = false }
            }
          }
          .disabled(pairingCode.count != 6 || isConnecting)
        }
      }
      .navigationTitle("Code d'appairage")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Fermer") { isPresentingManualCode = false }
        }
      }
    }
    .presentationDetents([.medium])
  }

  /// Doc utilisateur — un lien `quimene://join` (appareil photo système, Messages…) ou un QR
  /// scanné avant que cet onglet n'existe encore doit être consommé dès qu'il apparaît, comme
  /// les autres signaux de `DeepLinkRouter`.
  private func consumePendingJoin() {
    guard let payload = deepLinkRouter.pendingJoin else { return }
    deepLinkRouter.pendingJoin = nil
    pairingCode = payload.pairingCode
    Task { await connect() }
  }

  private func connect() async {
    isConnecting = true
    connectionError = nil
    defer { isConnecting = false }
    do {
      try await coordinator.join(
        code: pairingCode,
        deviceName: SessionDisplayName.current(in: modelContext),
        requestedRole: .contributor,
        appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
      )
    } catch {
      connectionError = Self.describe(error)
    }
  }

  /// Doc utilisateur — reprise après une connexion perdue, déclenchée par le bouton
  /// « Se reconnecter » de `SharedMatchView`.
  private func reconnect() async -> Bool {
    await coordinator.reconnectNow()
  }

  /// Code introuvable (erroné, expiré, session arrêtée) ou échec réseau : deux causes, deux
  /// messages. Plus d'hôte qui doit répondre (doc 16, phase C).
  private static func describe(_ error: Error) -> String {
    switch error {
    case OnlineSessionError.sessionNotFound:
      return "Aucune partie ne correspond à ce code. Vérifie qu'il est bien à jour."
    default:
      return "Connexion impossible (\(error.localizedDescription)). Réessaie."
    }
  }
}
