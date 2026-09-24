import CryptoKit
import Domain
import Foundation
import Supabase

/// Doc 16, phase C — client des sessions en ligne (`quimene_session_*`, migration
/// `create_quimene_sessions`). Remplace le protocole « hôte autoritaire » de `LiveSession` : le
/// journal de la session vit sur le serveur et fait foi pour tous les appareils, créateur compris.
///
/// Format d'un événement stocké, commun à iOS et Android : le `StampedEvent` encodé en JSON (même
/// encodeur que `WireCodec`), scellé en AES-GCM avec la clé de session (`SessionCrypto
/// .deriveKey`, code d'appairage + `session_id`), puis en base64. Son `lamport` vaut le numéro de
/// séquence attribué par le serveur : l'ordre de rejeu (`MatchEngine.replay`) est donc exactement
/// l'ordre du serveur.

/// Ce que renvoie la résolution d'un code : de quoi rejoindre, et jusqu'où lire.
public struct OnlineSessionInfo: Sendable, Equatable {
  public let sessionID: UUID
  public let ownerDeviceID: String
  public let allowsContributors: Bool
  public let lastSeq: Int64

  public init(sessionID: UUID, ownerDeviceID: String, allowsContributors: Bool, lastSeq: Int64) {
    self.sessionID = sessionID
    self.ownerDeviceID = ownerDeviceID
    self.allowsContributors = allowsContributors
    self.lastSeq = lastSeq
  }
}

/// Un événement tel que stocké par le serveur, encore chiffré.
public struct RawSessionEvent: Sendable, Equatable {
  public let seq: Int64
  public let eventID: UUID
  public let matchID: UUID
  public let deviceID: String
  public let ciphertext: String

  public init(seq: Int64, eventID: UUID, matchID: UUID, deviceID: String, ciphertext: String) {
    self.seq = seq
    self.eventID = eventID
    self.matchID = matchID
    self.deviceID = deviceID
    self.ciphertext = ciphertext
  }
}

/// Un événement lisible du journal de la session.
public struct SessionEventRecord: Sendable, Equatable, Identifiable {
  public let seq: Int64
  public let matchID: UUID
  public let event: StampedEvent

  public var id: Int64 { seq }
}

public enum OnlineSessionError: Error, Sendable, Equatable {
  /// Le code d'appairage est déjà pris par une autre session vivante : en tirer un autre.
  case pairingCodeTaken
  /// Aucun code ne correspond (erroné, expiré, ou session fermée).
  case sessionNotFound
  /// Un autre appareil a ajouté un événement entre-temps : l'appelant revalide contre l'état à
  /// jour (déjà rattrapé par `OnlineSession.append`) puis réessaie.
  case staleSequence
  case sessionClosed
  case notSessionOwner
  case eventTooLarge
}

/// Accès au serveur, isolé derrière un protocole pour que `OnlineSession` se teste sans réseau
/// (`InMemorySessionBackend`, SyncTests).
public protocol OnlineSessionBackend: Sendable {
  func open(sessionID: UUID, pairingCode: String, ownerDeviceID: String, allowsContributors: Bool)
    async throws
  func resolve(pairingCode: String) async throws -> OnlineSessionInfo?
  func append(
    sessionID: UUID, expectedSeq: Int64, eventID: UUID, matchID: UUID, deviceID: String,
    ciphertext: String
  ) async throws -> Int64
  func events(sessionID: UUID, after seq: Int64) async throws -> [RawSessionEvent]
  func close(sessionID: UUID, ownerDeviceID: String) async throws
}

/// Une session ouverte ou rejointe par cet appareil : son journal lisible, le rattrapage, l'ajout.
public actor OnlineSession {
  public nonisolated let sessionID: UUID
  public nonisolated let pairingCode: String
  public nonisolated let deviceID: String

  /// Lot maximal renvoyé par `quimene_session_events_after`.
  static let pageSize = 500

  private let key: SymmetricKey
  private let backend: any OnlineSessionBackend
  public private(set) var records: [SessionEventRecord] = []
  /// Dernier numéro vu, lisible ou non : un événement indéchiffrable (autre version, données
  /// corrompues) est sauté, mais il occupe bien sa place dans la numérotation.
  public private(set) var lastSeq: Int64 = 0

  public init(sessionID: UUID, pairingCode: String, deviceID: String, backend: any OnlineSessionBackend) {
    self.sessionID = sessionID
    self.pairingCode = pairingCode
    self.deviceID = deviceID
    self.backend = backend
    key = SessionCrypto.deriveKey(pairingCode: pairingCode, sessionID: sessionID)
  }

  /// Rattrape tout ce qui suit le dernier numéro connu, par lots ; renvoie les nouveaux
  /// événements lisibles, dans l'ordre.
  @discardableResult
  public func sync() async throws -> [SessionEventRecord] {
    var fresh: [SessionEventRecord] = []
    while true {
      let batch = try await backend.events(sessionID: sessionID, after: lastSeq)
      for raw in batch where raw.seq > lastSeq {
        lastSeq = raw.seq
        if let record = Self.open(raw, key: key) {
          records.append(record)
          fresh.append(record)
        }
      }
      if batch.count < Self.pageSize { break }
    }
    return fresh
  }

  /// Ajoute un événement à la suite du journal connu. Si un autre appareil l'a devancé, rattrape
  /// d'abord puis lève `staleSequence` : l'appelant revalide son événement contre l'état à jour
  /// (la manche qu'il voulait saisir a peut-être déjà été saisie ailleurs) avant de réessayer.
  ///
  /// `eventID`/`occurredAt` : à fournir pour publier un événement qui existe déjà en local (le
  /// journal d'une partie commencée hors ligne). Garder son identifiant est indispensable :
  /// `MatchEngine` tire l'identifiant de la partie de celui de son `matchCreated`, qui doit donc
  /// rester celui de la partie locale sur tous les appareils.
  @discardableResult
  public func append(
    _ event: MatchEvent, matchID: UUID, eventID: UUID = UUID(), occurredAt: Date = Date()
  ) async throws -> SessionEventRecord {
    let expected = lastSeq + 1
    let stamped = StampedEvent(
      id: eventID, lamport: UInt64(expected), deviceID: deviceID, occurredAt: occurredAt,
      event: event)
    let ciphertext = try Self.seal(stamped, key: key)
    let seq: Int64
    do {
      seq = try await backend.append(
        sessionID: sessionID, expectedSeq: expected, eventID: stamped.id, matchID: matchID,
        deviceID: deviceID, ciphertext: ciphertext)
    } catch OnlineSessionError.staleSequence {
      try await sync()
      throw OnlineSessionError.staleSequence
    }
    guard seq == expected, seq == lastSeq + 1 else {
      // Réponse d'un ajout déjà enregistré (envoi retenté) : le journal fait foi.
      try await sync()
      guard let record = records.first(where: { $0.event.id == stamped.id }) else {
        throw OnlineSessionError.staleSequence
      }
      return record
    }
    lastSeq = seq
    let record = SessionEventRecord(seq: seq, matchID: matchID, event: stamped)
    records.append(record)
    return record
  }

  /// Journal d'une partie de la session, prêt pour `MatchEngine.replay`.
  public func events(forMatch matchID: UUID) -> [StampedEvent] {
    records.filter { $0.matchID == matchID }.map(\.event)
  }

  /// La partie courante : celle dont l'événement `matchCreated` est le plus récent.
  public func currentMatchID() -> UUID? {
    records.last { if case .matchCreated = $0.event.event { true } else { false } }?.matchID
  }

  // MARK: - Format

  static func seal(_ stamped: StampedEvent, key: SymmetricKey) throws -> String {
    let json = try JSONEncoder().encode(stamped)
    return try SessionCrypto.encrypt(json, key: key).base64EncodedString()
  }

  static func open(_ raw: RawSessionEvent, key: SymmetricKey) -> SessionEventRecord? {
    guard let data = Data(base64Encoded: raw.ciphertext),
      let json = try? SessionCrypto.decrypt(data, key: key),
      let stamped = try? JSONDecoder().decode(StampedEvent.self, from: json)
    else { return nil }
    return SessionEventRecord(seq: raw.seq, matchID: raw.matchID, event: stamped)
  }
}

// MARK: - Supabase

/// `OnlineSessionBackend` sur les fonctions SQL de la migration `create_quimene_sessions`.
public struct SupabaseSessionBackend: OnlineSessionBackend {
  private let client: SupabaseClient

  public init() {
    client = SupabaseClient(
      supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
  }

  public func open(
    sessionID: UUID, pairingCode: String, ownerDeviceID: String, allowsContributors: Bool
  ) async throws {
    try await call {
      try await client.rpc(
        "quimene_session_open",
        params: OpenParams(
          sessionID: sessionID, pairingCode: pairingCode, ownerDeviceID: ownerDeviceID,
          allowsContributors: allowsContributors)
      ).execute()
    }
  }

  public func resolve(pairingCode: String) async throws -> OnlineSessionInfo? {
    let rows: [ResolvedRow] = try await call {
      try await client.rpc("quimene_session_resolve", params: ["p_pairing_code": pairingCode])
        .execute().value
    }
    return rows.first.map {
      OnlineSessionInfo(
        sessionID: $0.sessionID, ownerDeviceID: $0.ownerDeviceID,
        allowsContributors: $0.allowsContributors, lastSeq: $0.lastSeq)
    }
  }

  public func append(
    sessionID: UUID, expectedSeq: Int64, eventID: UUID, matchID: UUID, deviceID: String,
    ciphertext: String
  ) async throws -> Int64 {
    try await call {
      try await client.rpc(
        "quimene_session_append",
        params: AppendParams(
          sessionID: sessionID, expectedSeq: expectedSeq, eventID: eventID, matchID: matchID,
          deviceID: deviceID, ciphertext: ciphertext)
      ).execute().value
    }
  }

  public func events(sessionID: UUID, after seq: Int64) async throws -> [RawSessionEvent] {
    let rows: [EventRow] = try await call {
      try await client.rpc(
        "quimene_session_events_after",
        params: EventsParams(sessionID: sessionID, afterSeq: seq)
      ).execute().value
    }
    return rows.map {
      RawSessionEvent(
        seq: $0.seq, eventID: $0.eventID, matchID: $0.matchID, deviceID: $0.deviceID,
        ciphertext: $0.ciphertext)
    }
  }

  public func close(sessionID: UUID, ownerDeviceID: String) async throws {
    try await call {
      try await client.rpc(
        "quimene_session_close",
        params: CloseParams(sessionID: sessionID, ownerDeviceID: ownerDeviceID)
      ).execute()
    }
  }

  /// Traduit les exceptions levées par les fonctions SQL (`raise exception '<code>'`) en
  /// erreurs du domaine `Sync` ; le reste (réseau…) passe tel quel.
  private func call<T>(_ body: () async throws -> T) async throws -> T {
    do {
      return try await body()
    } catch let error as PostgrestError {
      switch error.message {
      case "pairing_code_taken": throw OnlineSessionError.pairingCodeTaken
      case "stale_seq": throw OnlineSessionError.staleSequence
      case "session_closed": throw OnlineSessionError.sessionClosed
      case "not_session_owner": throw OnlineSessionError.notSessionOwner
      case "event_too_large": throw OnlineSessionError.eventTooLarge
      default: throw error
      }
    }
  }

  private struct OpenParams: Encodable {
    let sessionID: UUID
    let pairingCode: String
    let ownerDeviceID: String
    let allowsContributors: Bool

    enum CodingKeys: String, CodingKey {
      case sessionID = "p_session_id"
      case pairingCode = "p_pairing_code"
      case ownerDeviceID = "p_owner_device_id"
      case allowsContributors = "p_allows_contributors"
    }
  }

  private struct ResolvedRow: Decodable {
    let sessionID: UUID
    let ownerDeviceID: String
    let allowsContributors: Bool
    let lastSeq: Int64

    enum CodingKeys: String, CodingKey {
      case sessionID = "session_id"
      case ownerDeviceID = "owner_device_id"
      case allowsContributors = "allows_contributors"
      case lastSeq = "last_seq"
    }
  }

  private struct AppendParams: Encodable {
    let sessionID: UUID
    let expectedSeq: Int64
    let eventID: UUID
    let matchID: UUID
    let deviceID: String
    let ciphertext: String

    enum CodingKeys: String, CodingKey {
      case sessionID = "p_session_id"
      case expectedSeq = "p_expected_seq"
      case eventID = "p_event_id"
      case matchID = "p_match_id"
      case deviceID = "p_device_id"
      case ciphertext = "p_ciphertext"
    }
  }

  private struct EventsParams: Encodable {
    let sessionID: UUID
    let afterSeq: Int64

    enum CodingKeys: String, CodingKey {
      case sessionID = "p_session_id"
      case afterSeq = "p_after_seq"
    }
  }

  private struct EventRow: Decodable {
    let seq: Int64
    let eventID: UUID
    let matchID: UUID
    let deviceID: String
    let ciphertext: String

    enum CodingKeys: String, CodingKey {
      case seq
      case eventID = "event_id"
      case matchID = "match_id"
      case deviceID = "device_id"
      case ciphertext
    }
  }

  private struct CloseParams: Encodable {
    let sessionID: UUID
    let ownerDeviceID: String

    enum CodingKeys: String, CodingKey {
      case sessionID = "p_session_id"
      case ownerDeviceID = "p_owner_device_id"
    }
  }
}

// MARK: - Canal temps réel

/// Un appareil présent dans la session (présence Realtime), pour la liste « connectés ».
public struct SessionPresence: Sendable, Equatable, Codable, Identifiable {
  public var id: String { deviceID }

  public let deviceID: String
  public let deviceName: String
  public let isOwner: Bool

  public init(deviceID: String, deviceName: String, isOwner: Bool) {
    self.deviceID = deviceID
    self.deviceName = deviceName
    self.isOwner = isOwner
  }
}

/// Canal `session:<id>` : la notification « un événement vient d'arriver » (envoyée par le
/// déclencheur SQL, seulement son numéro) et la présence des appareils. Le contenu, lui, se lit
/// toujours via `OnlineSession.sync()` — une notification perdue ne perd donc rien : le prochain
/// rattrapage (retour au premier plan, notification suivante) comble le trou.
public final class SessionChannel: @unchecked Sendable {
  private let client: SupabaseClient
  private let channel: RealtimeChannelV2
  private let me: SessionPresence
  private var tasks: [Task<Void, Never>] = []
  private var statusSubscription: RealtimeSubscription?
  private var present: [String: SessionPresence] = [:]

  public let notifications: AsyncStream<Int64>
  private let notificationContinuation: AsyncStream<Int64>.Continuation
  public let presence: AsyncStream<[SessionPresence]>
  private let presenceContinuation: AsyncStream<[SessionPresence]>.Continuation

  public init(sessionID: UUID, me: SessionPresence) {
    client = SupabaseClient(
      supabaseURL: SupabaseSyncConfig.projectURL, supabaseKey: SupabaseSyncConfig.anonKey)
    self.me = me
    // Minuscules : c'est la forme texte d'un `uuid` Postgres, celle qu'utilise le déclencheur
    // (`'session:' || session_id::text`). `uuidString` est en majuscules — le canal de
    // l'app et celui des notifications étaient deux canaux différents.
    channel = client.channel("session:\(sessionID.uuidString.lowercased())") { config in
      config.presence.key = me.deviceID
    }
    (notifications, notificationContinuation) = AsyncStream.makeStream()
    (presence, presenceContinuation) = AsyncStream.makeStream()
  }

  public func connect() async throws {
    let channel = channel
    let notificationContinuation = notificationContinuation
    tasks.append(
      Task {
        // Un simple signal « rattraper » : son contenu (le numéro) n'est qu'indicatif, une
        // notification au format inattendu doit quand même déclencher le rattrapage.
        for await payload in channel.broadcastStream(event: "event") {
          let inner = payload["payload"]?.objectValue ?? payload
          notificationContinuation.yield(Int64(inner["seq"]?.intValue ?? 0))
        }
      })
    tasks.append(
      Task { [weak self] in
        for await action in channel.presenceChange() {
          self?.apply(action)
        }
      })
    // `track()` n'est jamais rejoué par un ré-abonnement du SDK après
    // une mise en arrière-plan ; le refaire à chaque passage à `.subscribed`.
    let me = me
    statusSubscription = channel.onStatusChange { status in
      guard status == .subscribed else { return }
      Task { try? await channel.track(me) }
    }
    try await channel.subscribeWithError()
    try await channel.track(me)
  }

  public func disconnect() async {
    statusSubscription?.cancel()
    statusSubscription = nil
    for task in tasks { task.cancel() }
    tasks = []
    await channel.untrack()
    await channel.unsubscribe()
    notificationContinuation.finish()
    presenceContinuation.finish()
  }

  private func apply(_ action: any PresenceAction) {
    for (key, value) in action.joins {
      if let decoded = try? value.decodeState(as: SessionPresence.self) {
        present[key] = decoded
      }
    }
    for key in action.leaves.keys {
      present[key] = nil
    }
    presenceContinuation.yield(present.values.sorted { $0.deviceName < $1.deviceName })
  }
}
