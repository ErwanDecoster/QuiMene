import Foundation
import Observation

/// Pont entre les points d'entrée déclenchés hors de tout écran particulier (`.onOpenURL` pour un
/// lien `quimene://`, `.onContinueUserActivity` pour une reprise Handoff) et l'onglet visé (Jeux,
/// Rejoindre ou Historique selon le signal), qui peut être plusieurs onglets plus loin au moment où
/// l'un ou l'autre arrive. Un unique objet observable partagé, sur le même principe que
/// `AppSettings` ; `.shared` (plutôt qu'une instance injectée) le rend utilisable hors de
/// l'environnement SwiftUI de la scène.
@MainActor
@Observable
final class DeepLinkRouter {
  static let shared = DeepLinkRouter()

  var pendingJoin: JoinLink.Payload?
  var pendingContinuedMatchID: UUID?
  var wantsResume = false
  /// Doc 16, phase A — « Rejoindre » n'est plus un onglet mais un écran plein écran, ouvert
  /// depuis un bouton (Jeux, Profil), un lien ou un QR scanné par l'appareil photo système.
  var isPresentingJoin = false
  /// Doc 16, phase A — toucher sa propre fiche dans Joueurs ouvre l'onglet Profil, pas une
  /// page de fiche parmi d'autres : c'est là que vit mon profil.
  var wantsProfileTab = false
  /// Bascule sur l'onglet Historique, déjà filtré sur ce jeu (déclenché depuis
  /// `GameLeaderboardView`, même patron que les autres signaux de cette classe).
  var pendingHistoryGameID: String?
}
