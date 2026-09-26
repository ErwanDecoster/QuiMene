import SwiftUI

// Dialogues communs aux écrans de partie : un seul endroit pour leur texte et leur rôle, au lieu
// d'une copie par écran de saisie (générique, Belote, Tarot, Wizard, Yams) et par liste.

extension View {
  /// Confirmation « Abandonner cette partie ? » — destructive, et irréversible.
  func abandonMatchConfirmation(
    isPresented: Binding<Bool>, onAbandon: @escaping @MainActor () -> Void
  )
    -> some View
  {
    confirmationDialog(
      "Abandonner cette partie ?", isPresented: isPresented, titleVisibility: .visible
    ) {
      Button("Abandonner", role: .destructive, action: onAbandon)
    } message: {
      Text(
        "La partie sera classée comme abandonnée dans l'historique, avec le classement atteint jusque-là. Cette action ne peut pas être annulée."
      )
    }
  }

  /// Alerte « Saisie invalide », affichée tant que `message` n'est pas `nil`.
  func invalidEntryAlert(message: String?, onDismiss: @escaping @MainActor () -> Void)
    -> some View
  {
    alert(
      "Saisie invalide",
      isPresented: Binding(get: { message != nil }, set: { if !$0 { onDismiss() } })
    ) {
      Button("OK") {}
    } message: {
      Text(message ?? "")
    }
  }
}
