import Foundation
import Supabase
import os

/// Doc utilisateur P9 — seul moyen fourni par Apple de rafraîchir une Live Activity (écran
/// verrouillé / Dynamic Island) pendant que l'app est suspendue en arrière-plan : un push APNs
/// dédié, envoyé par une fonction Edge Supabase (`supabase/functions/cacompte-live-activity-push`) plutôt
/// que par l'app elle-même (qui ne tourne justement plus à ce moment-là). Générique sur le contenu
/// (`some Encodable`) plutôt que sur `Domain.MatchActivityAttributes.ContentState` directement :
/// ce type n'existe que sous `#if os(iOS)` (`ActivityKit` indisponible sur macOS, dont `Domain`
/// doit rester buildable), alors que `Sync` cible aussi macOS — l'appelant (App, iOS uniquement)
/// passe son `ContentState` concret, `Sync` n'a besoin de rien en connaître de plus que `Encodable`.
public enum LiveActivityPushClient {
  private static let client = SupabaseClient(
    supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
  private static let logger = Logger(
    subsystem: "com.cacompte.app", category: "LiveActivityPushClient")

  private struct TokenRow: Encodable {
    let activityKey: String
    let deviceID: String
    let pushToken: String

    enum CodingKeys: String, CodingKey {
      case activityKey = "activity_key"
      case deviceID = "device_id"
      case pushToken = "push_token"
    }
  }

  /// Doc utilisateur — appelé à chaque rotation de jeton signalée par
  /// `Activity.pushTokenUpdates` (création de la Live Activity, ou rotation ultérieure par
  /// iOS) : `upsert` sur la clé primaire `(activity_key, device_id)` remplace toujours l'ancien
  /// jeton plutôt que d'en accumuler plusieurs par appareil. Doc 09 « Fin de partie » —
  /// `activityKey` identifie la session de partage (stable au changement de partie), pas
  /// forcément la seule partie courante — voir `MatchLiveActivityController.activityKey`.
  ///
  /// `returning: .minimal` explicite — sans policy `select` pour l'anon sur cette table
  /// (volontaire, `supabase/migrations`), le comportement par défaut de `upsert`
  /// (`.representation`, qui tente de relire la ligne pour construire la réponse) échoue avec
  /// une erreur RLS trompeuse même quand l'insert a réellement eu lieu.
  public static func registerToken(activityKey: String, deviceID: String, pushToken: String) async {
    do {
      try await client.from("cacompte_live_activity_tokens")
        .upsert(
          TokenRow(activityKey: activityKey, deviceID: deviceID, pushToken: pushToken),
          onConflict: "activity_key,device_id",
          returning: .minimal
        )
        .execute()
      logger.info(
        "registerToken OK key=\(activityKey, privacy: .public) device=\(deviceID, privacy: .public)"
      )
    } catch {
      logger.error(
        "registerToken FAILED key=\(activityKey, privacy: .public): \(error, privacy: .public)")
    }
  }

  private struct PushBody<Content: Encodable>: Encodable {
    let activityKey: String
    let event: String
    let contentState: Content
  }

  /// Doc utilisateur — n'appeler que côté hôte (seul appareil qui fait foi sur le journal,
  /// `LiveMatchModel`) : un pair qui pousserait aussi créerait des mises à jour concurrentes et
  /// redondantes pour la même partie. Best-effort : une fonction Edge indisponible ou un jeton
  /// périmé ne doit jamais faire échouer la validation d'une manche, seulement priver le pair
  /// concerné d'une mise à jour en arrière-plan (son prochain retour au premier plan
  /// resynchronise de toute façon tout via `MatchConnectionCoordinator`).
  public static func push(activityKey: String, event: String, contentState: some Encodable) async {
    guard
      let url = URL(
        string:
          "\(SupabaseSyncConfig.projectURL.absoluteString)/functions/v1/cacompte-live-activity-push"
      )
    else { return }
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.setValue(
      "Bearer \(SupabaseSyncConfig.anonKey)", forHTTPHeaderField: "Authorization")
    request.httpBody = try? JSONEncoder().encode(
      PushBody(activityKey: activityKey, event: event, contentState: contentState))
    do {
      let (data, response) = try await URLSession.shared.data(for: request)
      let status = (response as? HTTPURLResponse)?.statusCode ?? -1
      logger.debug(
        "push status=\(status, privacy: .public) body=\(String(data: data, encoding: .utf8) ?? "?", privacy: .public)"
      )
    } catch {
      logger.error(
        "push FAILED activityKey=\(activityKey, privacy: .public): \(error, privacy: .public)")
    }
  }
}
