import SwiftUI

/// Charte graphique §2.2 — chaque token référence un style système, jamais une taille en
/// points : le tracking et l'*optical sizing* de SF Pro s'appliquent seuls, Dynamic Type opère
/// sans intervention.
extension Font {
  public static var h1: Font { .largeTitle.weight(.bold) }
  public static var h2: Font { .title.weight(.bold) }
  public static var h3: Font { .title2.weight(.bold) }
  public static var h4: Font { .title3.weight(.semibold) }
  public static var h5: Font { .headline }
  public static var h6: Font { .subheadline.weight(.semibold) }
  public static var bodyText: Font { .body }
  public static var bodySmall: Font { .callout }
  public static var label: Font { .footnote.weight(.medium) }
  public static var captionText: Font { .caption }
  public static var caption2Text: Font { .caption2 }
  public static var button: Font { .headline }

  /// Charte graphique §2.3 — chiffres de score : chasse fixe (`monospacedDigit`) et dessin
  /// `.rounded`, seul écart typographique assumé de la charte.
  public static var scoreXL: Font {
    .system(.largeTitle, design: .rounded, weight: .semibold).monospacedDigit()
  }
  public static var scoreL: Font {
    .system(.title2, design: .rounded, weight: .semibold).monospacedDigit()
  }
  public static var scoreM: Font {
    .system(.body, design: .rounded, weight: .medium).monospacedDigit()
  }
}
