import SwiftUI

/// Charte graphique §12.2 — un constat + une action. L'illustration dessinée (trait 2 px +
/// aplat `brand/brass`, §10) n'existe pas encore : un SF Symbol tient sa place en attendant
/// (voir README « Actions manuelles en attente »).
public struct EmptyState: View {
  private let icon: String
  private let message: LocalizedStringResource
  private let actionTitle: LocalizedStringResource?
  private let action: (() -> Void)?

  public init(
    icon: String,
    message: LocalizedStringResource,
    actionTitle: LocalizedStringResource? = nil,
    action: (() -> Void)? = nil
  ) {
    self.icon = icon
    self.message = message
    self.actionTitle = actionTitle
    self.action = action
  }

  public var body: some View {
    VStack(spacing: Space.lg) {
      Image(systemName: icon)
        .font(.system(size: IconSize.xl))
        .foregroundStyle(.textTertiary)
      Text(message)
        .font(.bodyText)
        .foregroundStyle(.textSecondary)
        .multilineTextAlignment(.center)
        // Doc utilisateur (audit qualité, 15) — remontée en testant l'italien : sans ça, le
        // `maxHeight: 160` ci-dessous propose une hauteur trop courte pour un message de deux
        // lignes (traduction plus longue, ou français en Dynamic Type AX5) et le texte tronque
        // en silence (« Aggiungi un g… ») plutôt que de passer à la ligne. `fixedSize` force ce
        // texte précis à réclamer sa hauteur naturelle ; le VStack grandit au-delà de 160 si
        // besoin plutôt que de couper le message.
        .fixedSize(horizontal: false, vertical: true)
      if let actionTitle, let action {
        Button(actionTitle, action: action)
          .buttonStyle(.primary(size: .medium))
      }
    }
    .padding(Space.xxl)
    .frame(maxWidth: .infinity)
    .frame(maxHeight: 160)
  }
}
