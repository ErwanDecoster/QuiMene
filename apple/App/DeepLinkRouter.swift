import Foundation
import Observation

/// Doc utilisateur — pont entre les points d'entrée déclenchés hors de tout écran particulier
/// (`.onOpenURL` pour un lien `quimene://`, `.onContinueUserActivity` pour une reprise Handoff) et
/// l'onglet visé (Jeux, Rejoindre ou Historique selon le signal), qui peut être plusieurs onglets
/// plus loin au moment où l'un ou l'autre arrive. Pas de pattern d'environnement existant dans le
/// projet pour ça (aucun `@Entry`
/// ailleurs) — un unique objet observable partagé, sur le même principe que `AppSettings`.
/// `.shared` (plutôt qu'une instance injectée) : utilisable hors de l'environnement SwiftUI de la
/// scène (App Intents, retirés de la v1.0 mais prévus à nouveau — voir roadmap P9).
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
  /// Doc utilisateur — bascule sur l'onglet Historique, déjà filtré sur ce jeu (déclenché depuis
  /// `GameLeaderboardView`, même patron que les autres signaux de cette classe).
  var pendingHistoryGameID: String?
}
