import Foundation
import SwiftUI

#if canImport(UIKit)
  import UIKit
#endif

/// Doc utilisateur — demande d'ajout d'un jeu : l'app reste volontairement simple (pas de
/// formulaire ni de backend dédié), un `mailto:` pré-rempli suffit. Point d'entrée unique
/// partagé par la recherche sans résultat (`GamesTabView`) et les réglages (`SettingsView`), pour
/// que les deux chemins produisent exactement le même sujet/corps.
enum GameRequestMail {
  static let recipient = "contact@erwan-decoster.com"

  /// Doc utilisateur — tente d'ouvrir un client mail préempli ; retourne `false` si aucun n'est
  /// disponible (app Mail supprimée, aucun compte configuré…) — `canOpenURL` renvoie alors
  /// `false` plutôt que d'échouer silencieusement au moment d'`open`, ce qui laisse l'appelant
  /// proposer un repli (voir `gameRequestMailFallback`) au lieu d'un tap sans effet visible.
  @MainActor
  @discardableResult
  static func open(searchTerm: String? = nil) -> Bool {
    #if canImport(UIKit)
      guard let url = url(searchTerm: searchTerm), UIApplication.shared.canOpenURL(url) else {
        return false
      }
      UIApplication.shared.open(url)
      return true
    #else
      return false
    #endif
  }

  private static func url(searchTerm: String?) -> URL? {
    let subject: LocalizedStringResource = "Demande d'ajout d'un jeu — Qui Mène ?"
    var components = URLComponents()
    components.scheme = "mailto"
    components.path = recipient
    components.queryItems = [
      URLQueryItem(name: "subject", value: String(localized: subject)),
      URLQueryItem(name: "body", value: body(searchTerm: searchTerm)),
    ]
    return components.url
  }

  private static func body(searchTerm: String?) -> String {
    let intro: LocalizedStringResource
    if let searchTerm, !searchTerm.isEmpty {
      intro = "Le jeu que je cherche : \(searchTerm)"
    } else {
      intro = "Le jeu que je souhaite voir ajouté :"
    }
    let rulesPrompt: LocalizedStringResource = "Règles ou lien vers les règles (facultatif) :"
    return "\(String(localized: intro))\n\n\n\(String(localized: rulesPrompt))\n\n"
  }
}

extension View {
  /// Doc utilisateur — repli affiché quand `GameRequestMail.open()` échoue : montre l'adresse et
  /// permet de la copier, plutôt qu'un tap sur « Demander ce jeu » qui ne produirait aucun effet
  /// visible sur un appareil sans client mail configuré.
  func gameRequestMailFallback(isPresented: Binding<Bool>) -> some View {
    alert("Aucune messagerie configurée", isPresented: isPresented) {
      Button("Copier l'adresse") {
        #if canImport(UIKit)
          UIPasteboard.general.string = GameRequestMail.recipient
        #endif
      }
      Button("OK", role: .cancel) {}
    } message: {
      Text("Envoie ta demande à \(GameRequestMail.recipient) depuis l'application de ton choix.")
    }
  }
}
