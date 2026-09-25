import Foundation
import Supabase
import os

/// Doc utilisateur P9 — seul moyen fourni par Apple de rafraîchir une Live Activity (écran
/// verrouillé / Dynamic Island) pendant que l'app est suspendue en arrière-plan : un push APNs
/// dédié, envoyé par une fonction Edge Supabase (`supabase/functions/quimene-live-activity-push`) plutôt
/// que par l'app elle-même (qui ne tourne justement plus à ce moment-là). Générique sur le contenu
/// (`some Encodable`) plutôt que sur `Domain.MatchActivityAttributes.ContentState` directement :
/// ce type n'existe que sous `#if os(iOS)` (`ActivityKit` indisponible sur macOS, dont `Domain`
/// doit rester buildable), alors que `Sync` cible aussi macOS — l'appelant (App, iOS uniquement)
/// passe son `ContentState` concret, `Sync` n'a besoin de rien en connaître de plus que `Encodable`.
public enum LiveActivityPushClient {
  private static let client = SupabaseClient(
    supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
  private static let logger = Logger(
    subsystem: "com.quimene.app", category: "LiveActivityPushClient")

  /// Paramètres de `quimene_register_live_activity_token` — clés encodées = noms SQL.
  private struct RegisterParams<Content: Encodable>: Encodable {
    let activityKey: String
    let deviceID: String
    let pushToken: String
    let contentState: Content

    enum CodingKeys: String, CodingKey {
      case activityKey = "p_activity_key"
      case deviceID = "p_device_id"
      case pushToken = "p_push_token"
      case contentState = "p_content_state"
    }
  }

  /// Doc utilisateur — appelé à chaque rotation de jeton signalée par
  /// `Activity.pushTokenUpdates` (création de la Live Activity, ou rotation ultérieure par
  /// iOS) : la fonction SQL remplace toujours l'ancien jeton de `(activity_key, device_id)`
  /// plutôt que d'en accumuler plusieurs par appareil. Doc 09 « Fin de partie » —
  /// `activityKey` identifie la session de partage (stable au changement de partie), pas
  /// forcément la seule partie courante — voir `MatchLiveActivityController.activityKey`.
  /// La table elle-même n'est plus accessible à l'anon (migration
  /// `secure_cacompte_live_activity_tokens`).
  /// `contentState` : contenu affiché au moment de l'inscription, conservé par le serveur pour que
  /// le balayage puisse toujours envoyer une fin valide (voir la migration
  /// `register_live_activity_token_content`).
  public static func registerToken(
    activityKey: String, deviceID: String, pushToken: String, contentState: some Encodable
  ) async {
    do {
      try await client.rpc(
        "quimene_register_live_activity_token",
        params: RegisterParams(
          activityKey: activityKey, deviceID: deviceID, pushToken: pushToken,
          contentState: contentState)
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

  /// Doc 16, phase F — appelé par l'appareil qui vient d'enregistrer un événement dans la session
  /// (créateur ou participant, `isAuthoritative`), jamais par ceux qui le reçoivent : une seule
  /// mise à jour par manche, et les écrans verrouillés suivent même quand le créateur est éteint.
  /// Android fait de même (`LiveActivityPushClient.kt`) pour les iPhone de la session.
  /// Best-effort : une fonction Edge indisponible ou un jeton périmé ne doit jamais faire échouer
  /// la validation d'une manche, seulement priver un appareil suspendu d'une mise à jour (son
  /// prochain retour au premier plan rattrape le journal de toute façon).
  public static func push(activityKey: String, event: String, contentState: some Encodable) async {
    guard
      let url = URL(
        string:
          "\(SupabaseSyncConfig.projectURL.absoluteString)/functions/v1/quimene-live-activity-push"
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
