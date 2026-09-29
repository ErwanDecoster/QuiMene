/// Doc 09 — trois rôles possibles dans une partie partagée. `.host` (le créateur) n'est jamais
/// demandé par un appareil qui rejoint : seuls `.contributor` et `.observer` le sont.
public enum Role: String, Codable, Sendable, Equatable {
  case host
  case contributor
  case observer
}
