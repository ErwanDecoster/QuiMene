import Foundation

/// Doc 16, phase D — « Qui es-tu ? ». Qui est qui dans une session en ligne, publié dans le même
/// journal chiffré que les parties (`OnlineSession`), sous une enveloppe distincte
/// (`{"identity": …}`) : une version de l'app qui ne la connaît pas ne sait pas la lire et la
/// saute, comme tout événement illisible, sans casser le rejeu des parties.
///
/// Une place se désigne par son siège et son pseudo (`SeatRef`), pas par l'identifiant du
/// participant : celui-ci change à chaque partie de la session (doc 16, phase C), le siège et le
/// pseudo restent.

/// Ce qu'un appareil dit de son profil : de quoi le reconnaître (`id`, l'identifiant partageable
/// du doc 14) et de quoi lui créer une fiche chez les autres. Jamais de photo (trop lourde) :
/// repli sur l'avatar dérivé du pseudo, comme pour le QR de profil.
public struct ProfileCard: Codable, Hashable, Sendable {
  public let id: UUID
  public let name: String
  public let avatarKind: String
  public let avatarValue: String
  public let paletteID: String

  public init(id: UUID, name: String, avatarKind: String, avatarValue: String, paletteID: String) {
    self.id = id
    self.name = name
    self.avatarKind = avatarKind
    self.avatarValue = avatarValue
    self.paletteID = paletteID
  }
}

/// Une place autour de la table, stable d'une partie de la session à la suivante.
public struct SeatRef: Codable, Hashable, Sendable {
  public let seatIndex: Int
  public let displayName: String

  public init(seatIndex: Int, displayName: String) {
    self.seatIndex = seatIndex
    self.displayName = displayName
  }
}

/// Une place que la fiche du créateur relie déjà à un profil (un ami lié, ou le créateur
/// lui-même).
public struct LinkedSeat: Codable, Hashable, Sendable {
  public let seat: SeatRef
  public let profileID: UUID

  public init(seat: SeatRef, profileID: UUID) {
    self.seat = seat
    self.profileID = profileID
  }
}

/// Un événement d'identité. Structure plate plutôt qu'une énumération à valeurs associées : même
/// JSON, sans ambiguïté, côté Swift et côté Kotlin.
public struct SessionIdentityEvent: Codable, Hashable, Sendable {
  public enum Kind: String, Codable, Sendable {
    /// « C'est moi » : `seat` + `profile`.
    case claim
    /// Annule la revendication `revokedClaimID` — par le créateur, ou par son auteur.
    case revoke
    /// Publié par le créateur : son profil (`profile`, absent s'il n'en a pas) et les places que
    /// ses fiches relient déjà à un profil (`linkedSeats`). Seul le plus récent compte.
    case roster
  }

  public let id: UUID
  public let deviceID: String
  public let occurredAt: Date
  public let kind: Kind
  public let seat: SeatRef?
  public let profile: ProfileCard?
  public let revokedClaimID: UUID?
  public let linkedSeats: [LinkedSeat]?

  public init(
    id: UUID = UUID(), deviceID: String, occurredAt: Date = Date(), kind: Kind,
    seat: SeatRef? = nil, profile: ProfileCard? = nil, revokedClaimID: UUID? = nil,
    linkedSeats: [LinkedSeat]? = nil
  ) {
    self.id = id
    self.deviceID = deviceID
    self.occurredAt = occurredAt
    self.kind = kind
    self.seat = seat
    self.profile = profile
    self.revokedClaimID = revokedClaimID
    self.linkedSeats = linkedSeats
  }

  public static func claim(_ seat: SeatRef, profile: ProfileCard, deviceID: String) -> Self {
    Self(deviceID: deviceID, kind: .claim, seat: seat, profile: profile)
  }

  public static func revoke(_ claimID: UUID, deviceID: String) -> Self {
    Self(deviceID: deviceID, kind: .revoke, revokedClaimID: claimID)
  }

  public static func roster(owner: ProfileCard?, linkedSeats: [LinkedSeat], deviceID: String)
    -> Self
  {
    Self(deviceID: deviceID, kind: .roster, profile: owner, linkedSeats: linkedSeats)
  }
}

/// L'enveloppe stockée : la clé `identity` la distingue d'un `StampedEvent`.
struct SessionIdentityEnvelope: Codable {
  let identity: SessionIdentityEvent
}

/// Un événement d'identité lisible du journal de la session.
public struct SessionIdentityRecord: Sendable, Equatable, Identifiable {
  public let seq: Int64
  public let event: SessionIdentityEvent

  public var id: Int64 { seq }

  public init(seq: Int64, event: SessionIdentityEvent) {
    self.seq = seq
    self.event = event
  }
}

/// Une revendication retenue.
public struct ActiveClaim: Sendable, Equatable {
  public let claimID: UUID
  public let seat: SeatRef
  public let profile: ProfileCard
  public let deviceID: String
  public let seq: Int64
}

/// Qui occupe quelle place, déduit du journal — le même calcul sur chaque appareil.
///
/// Règles, dans l'ordre du journal :
/// - seul le dernier `roster` du créateur compte ; une place qu'il relie à un profil est à ce
///   profil, sans revendication ;
/// - une revendication annulée ne compte plus (annulation par le créateur ou par son auteur) ;
/// - premier arrivé, premier servi : une place déjà revendiquée par un autre profil, ou reliée à
///   un autre profil par le créateur, ne peut pas l'être (doc 16, « sans accord ») ;
/// - un profil n'occupe qu'une place : une nouvelle revendication remplace la précédente, **y
///   compris sa place reliée par le créateur**, qui devient alors libre (reconnu d'office sur la
///   mauvaise fiche, on peut toujours changer de place).
public struct SessionIdentities: Sendable, Equatable {
  public private(set) var owner: ProfileCard?
  public private(set) var linkedSeats: [SeatRef: UUID] = [:]
  public private(set) var activeClaims: [ActiveClaim] = []

  public init(records: [SessionIdentityRecord], ownerDeviceID: String) {
    var revoked: Set<UUID> = []
    for record in records where record.event.kind == .revoke {
      guard let claimID = record.event.revokedClaimID else { continue }
      let claim = records.first { $0.event.id == claimID && $0.event.kind == .claim }
      if record.event.deviceID == ownerDeviceID || record.event.deviceID == claim?.event.deviceID {
        revoked.insert(claimID)
      }
    }
    if let roster = records.last(where: {
      $0.event.kind == .roster && $0.event.deviceID == ownerDeviceID
    }) {
      owner = roster.event.profile
      for linked in roster.event.linkedSeats ?? [] {
        linkedSeats[linked.seat] = linked.profileID
      }
    }
    for record in records where record.event.kind == .claim && !revoked.contains(record.event.id) {
      guard let seat = record.event.seat, let profile = record.event.profile else { continue }
      if let linked = linkedSeats[seat], linked != profile.id,
        !activeClaims.contains(where: { $0.profile.id == linked && $0.seat != seat })
      {
        continue
      }
      if activeClaims.contains(where: { $0.seat == seat && $0.profile.id != profile.id }) {
        continue
      }
      activeClaims.removeAll { $0.profile.id == profile.id }
      activeClaims.append(
        ActiveClaim(
          claimID: record.event.id, seat: seat, profile: profile,
          deviceID: record.event.deviceID, seq: record.seq))
    }
  }

  /// Le profil qui occupe cette place, s'il y en a un.
  public func occupant(of seat: SeatRef) -> UUID? {
    if let claimed = activeClaims.first(where: { $0.seat == seat }) { return claimed.profile.id }
    guard let linked = linkedSeats[seat],
      !activeClaims.contains(where: { $0.profile.id == linked })
    else { return nil }
    return linked
  }

  /// La place de ce profil : revendiquée, sinon reliée par le créateur.
  public func seat(of profileID: UUID) -> SeatRef? {
    activeClaims.first { $0.profile.id == profileID }?.seat
      ?? linkedSeats.first { $0.value == profileID }?.key
  }

  public func activeClaim(of profileID: UUID) -> ActiveClaim? {
    activeClaims.first { $0.profile.id == profileID }
  }
}
