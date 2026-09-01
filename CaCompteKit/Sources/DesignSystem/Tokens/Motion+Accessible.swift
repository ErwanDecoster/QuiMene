import SwiftUI

extension Duration {
  /// Conversion vers `TimeInterval` (secondes), nécessaire pour `Animation.linear(duration:)`
  /// qui ne prend pas de `Duration`. `Motion` est exprimé en `Duration` (charte §9.1) ; cette
  /// extension est le seul pont entre les deux, plutôt que de dupliquer les valeurs en `Double`.
  public var seconds: TimeInterval {
    let components = components
    return Double(components.seconds) + Double(components.attoseconds) / 1e18
  }
}

extension View {
  /// Charte §9.3 « Reduce Motion » — sous Reduce Motion, toute durée retombe à `motion/fast` ;
  /// remplace `.animation(_:value:)` partout où l'intention est un changement de contenu (texte,
  /// opacité, mise à l'échelle d'un bouton), pas un mouvement de position à part entière — ces
  /// derniers restent à traiter au cas par cas (podium, écran de résultats).
  @ViewBuilder
  public func accessibleAnimation<V: Equatable>(_ animation: Animation?, value: V) -> some View {
    modifier(AccessibleAnimationModifier(animation: animation, value: value))
  }
}

private struct AccessibleAnimationModifier<V: Equatable>: ViewModifier {
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  let animation: Animation?
  let value: V

  func body(content: Content) -> some View {
    content.animation(
      reduceMotion ? .linear(duration: Motion.fast.seconds) : animation, value: value)
  }
}
