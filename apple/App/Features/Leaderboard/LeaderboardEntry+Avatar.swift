import DesignSystem
import Foundation
import Store

/// Même pont que `PlayerRecord+Avatar.swift` — `Store` ignore `DesignSystem`, seule la cible
/// `App` connaît les deux (doc 02).
extension LeaderboardEntry {
  var palette: PlayerPalette {
    PlayerPalette(index: Int(paletteID) ?? 1)
  }

  var avatar: Avatar {
    let kind: Avatar.Kind
    switch avatarKind {
    case "emoji":
      kind = .emoji(avatarValue)
    case "photo":
      kind = .photo(avatarPhoto ?? Data())
    default:
      kind = .symbol(avatarValue.isEmpty ? "person.fill" : avatarValue)
    }
    return Avatar(kind: kind, palette: palette)
  }
}
