import SwiftUI

#if canImport(UIKit)
  import UIKit
#endif

/// Charte graphique §5.5 — bandeau flottant (toast) : `elev/3`, une action maximum.
/// L'auto-disparition (4 s, 8 s avec action) est une responsabilité de l'appelant, pas de la vue.
public struct Banner: View {
  private let message: LocalizedStringResource
  private let actionTitle: LocalizedStringResource?
  private let action: (() -> Void)?

  public init(
    _ message: LocalizedStringResource,
    actionTitle: LocalizedStringResource? = nil,
    action: (() -> Void)? = nil
  ) {
    self.message = message
    self.actionTitle = actionTitle
    self.action = action
  }

  public var body: some View {
    HStack(spacing: Space.md) {
      Text(message)
        .font(.bodySmall)
        .foregroundStyle(.textPrimary)
      Spacer(minLength: 0)
      if let actionTitle, let action {
        Button(actionTitle, action: action)
          .font(.label)
          .foregroundStyle(.brandInk)
      }
    }
    .padding(.horizontal, Space.lg)
    .padding(.vertical, Space.md)
    .frame(minHeight: 48)
    .background(.regularMaterial, in: .rect(cornerRadius: Radius.lg))
  }
}

extension Banner {
  /// Doc 08 « Accessibilité » — `Banner` est un signal purement visuel par défaut : rien
  /// n'informe VoiceOver de son apparition, contrairement à une alerte système. À appeler par
  /// l'écran qui affiche le bandeau (même site que le déclenchement de son animation), pas par
  /// `Banner` lui-même, qui reste une vue pure sans effet de bord.
  public static func announce(_ message: LocalizedStringResource) {
    #if canImport(UIKit)
      UIAccessibility.post(notification: .announcement, argument: String(localized: message))
    #endif
  }
}
