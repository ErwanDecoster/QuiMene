import CryptoKit
import Foundation

/// Doc 09 « Format d'un événement » — clé de session dérivée du code d'appairage, événements
/// scellés en AES-GCM. `CryptoKit` est un framework système : aucune dépendance tierce ajoutée
/// (ADR-0012).
enum SessionCrypto {
  enum CryptoError: Error, Sendable, Equatable {
    case sealFailed
  }

  /// HKDF-SHA256 : le `sessionID` sert de sel, ce qui garantit une clé différente par session
  /// même si deux créateurs obtiennent le même code par coïncidence. Salé par la session — stable
  /// tant qu'elle dure — et non par la partie courante : une session peut enchaîner plusieurs
  /// parties (doc 09 « Session ») sans que la clé ne change, donc sans qu'un participant déjà
  /// connecté ait besoin de se réappairer entre deux parties.
  static func deriveKey(pairingCode: String, sessionID: UUID) -> SymmetricKey {
    let inputKeyMaterial = SymmetricKey(data: Data(pairingCode.utf8))
    let salt = Data(sessionID.uuidString.utf8)
    let info = Data("quimene.livesession.v1".utf8)
    return HKDF<SHA256>.deriveKey(
      inputKeyMaterial: inputKeyMaterial, salt: salt, info: info, outputByteCount: 32)
  }

  static func encrypt(_ data: Data, key: SymmetricKey) throws -> Data {
    let sealed = try AES.GCM.seal(data, using: key)
    guard let combined = sealed.combined else { throw CryptoError.sealFailed }
    return combined
  }

  static func decrypt(_ data: Data, key: SymmetricKey) throws -> Data {
    let box = try AES.GCM.SealedBox(combined: data)
    return try AES.GCM.open(box, using: key)
  }
}
