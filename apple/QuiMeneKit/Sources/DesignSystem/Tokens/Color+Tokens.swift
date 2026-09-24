import SwiftUI

/// Charte graphique §14 — traduction mécanique du tableau de synthèse des couleurs. Chaque
/// entrée référence le catalogue d'assets (variantes Any/Dark), jamais une valeur littérale.
extension ShapeStyle where Self == Color {
  public static var brandInk: Color { Color("brand/ink", bundle: .designSystem) }
  public static var brandInkPressed: Color { Color("brand/inkPressed", bundle: .designSystem) }
  public static var brandBrass: Color { Color("brand/brass", bundle: .designSystem) }
  public static var brandTeal: Color { Color("brand/teal", bundle: .designSystem) }

  public static var neutralBg: Color { Color("neutral/bg", bundle: .designSystem) }
  public static var neutralSurface: Color { Color("neutral/surface", bundle: .designSystem) }
  public static var neutralSunken: Color { Color("neutral/sunken", bundle: .designSystem) }
  public static var neutralFill: Color { Color("neutral/fill", bundle: .designSystem) }
  public static var neutralBorder: Color { Color("neutral/border", bundle: .designSystem) }
  public static var neutralBorderStrong: Color {
    Color("neutral/borderStrong", bundle: .designSystem)
  }

  public static var textPrimary: Color { Color("text/primary", bundle: .designSystem) }
  public static var textSecondary: Color { Color("text/secondary", bundle: .designSystem) }
  public static var textTertiary: Color { Color("text/tertiary", bundle: .designSystem) }
  public static var textDisabled: Color { Color("text/disabled", bundle: .designSystem) }

  public static var semanticSuccess: Color { Color("semantic/success", bundle: .designSystem) }
  public static var semanticError: Color { Color("semantic/error", bundle: .designSystem) }
  public static var semanticWarning: Color { Color("semantic/warning", bundle: .designSystem) }
  public static var semanticInfo: Color { Color("semantic/info", bundle: .designSystem) }
}

extension Color {
  /// Charte graphique §1.5 — palette des joueurs, `index` de 1 à 10. `PlayerPalette` est le
  /// point d'entrée à préférer : la couleur seule ne porte jamais l'information (§1.5, §13.1).
  public static func player(_ index: Int) -> Color {
    precondition((1...10).contains(index), "player index must be in 1...10")
    return Color("player/\(index)", bundle: .designSystem)
  }
}
