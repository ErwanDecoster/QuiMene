import Foundation

/// Doc 16, phase E — une partie terminée, **complète** (journal d'événements et fiches de ses
/// joueurs), telle que déposée dans la boîte aux lettres d'un ami lié qui y a joué
/// (`MailboxCrypto`, Sync). Remplace les résumés du doc 14 (classement final seulement).
public struct SharedMatchPackage: Codable, Sendable, Equatable {
  public struct Participant: Codable, Sendable, Equatable {
    public let participantID: UUID
    /// `nil` pour un joueur sans profil lié chez l'expéditeur.
    public let sharedProfileID: UUID?
    public let nickname: String
    public let avatarKind: String
    public let avatarValue: String
    public let paletteID: String

    public init(
      participantID: UUID, sharedProfileID: UUID?, nickname: String, avatarKind: String,
      avatarValue: String, paletteID: String
    ) {
      self.participantID = participantID
      self.sharedProfileID = sharedProfileID
      self.nickname = nickname
      self.avatarKind = avatarKind
      self.avatarValue = avatarValue
      self.paletteID = paletteID
    }
  }

  public let matchID: UUID
  public let participants: [Participant]
  /// Le journal complet, prêt pour `MatchEngine.replay` : la partie se relit à l'identique.
  public let events: [StampedEvent]

  public init(matchID: UUID, participants: [Participant], events: [StampedEvent]) {
    self.matchID = matchID
    self.participants = participants
    self.events = events
  }
}
