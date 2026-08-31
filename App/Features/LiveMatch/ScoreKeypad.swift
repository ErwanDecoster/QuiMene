import DesignSystem
import SwiftUI

/// Doc utilisateur — remontée : le bouton +/- de la barre d'accessoires du clavier système
/// obligeait un aller-retour du regard entre les chiffres (sur le clavier) et le signe (au-dessus)
/// — et manquait entièrement à `SharedMatchView`, qui n'avait pas cette barre. La charte §6 avait
/// déjà prévu ce pavé (`Keypad.key`/`Keypad.gap`, jamais utilisés jusqu'ici) : le signe y devient
/// une touche parmi les chiffres, au même niveau, partagée par `LiveMatchView` et
/// `SharedMatchView`. Remplace entièrement le clavier système (`.keyboardType(.numberPad)`) pour
/// la saisie des scores — ce qui élimine au passage le bug iPad que corrigeait `KeyboardObserver`
/// (plus de clavier système à masquer/synchroniser).
struct ScoreKeypad: View {
    let allowsNegative: Bool
    let onDigit: (Int) -> Void
    let onToggleSign: () -> Void
    let onDelete: () -> Void

    private enum Key: Hashable {
        case digit(Int)
        case sign
        case delete
        case blank
    }

    private var rows: [[Key]] {
        [
            [.digit(7), .digit(8), .digit(9)],
            [.digit(4), .digit(5), .digit(6)],
            [.digit(1), .digit(2), .digit(3)],
            [allowsNegative ? .sign : .blank, .digit(0), .delete],
        ]
    }

    var body: some View {
        VStack(spacing: Keypad.gap) {
            ForEach(rows.indices, id: \.self) { rowIndex in
                HStack(spacing: Keypad.gap) {
                    ForEach(rows[rowIndex], id: \.self) { key in
                        keyButton(key)
                    }
                }
            }
        }
        .padding(.horizontal, Space.lg)
        .padding(.top, Space.md)
        .padding(.bottom, Space.sm)
        .background(.neutralSunken)
    }

    @ViewBuilder
    private func keyButton(_ key: Key) -> some View {
        switch key {
        case .digit(let value):
            keyBase(action: { onDigit(value) }) {
                Text("\(value)").font(.h4).foregroundStyle(.textPrimary)
            }
        case .sign:
            keyBase(action: onToggleSign) {
                Image(systemName: "plusminus")
                    .font(.system(size: IconSize.md))
                    .foregroundStyle(.textPrimary)
            }
            .accessibilityLabel("Changer le signe")
        case .delete:
            keyBase(action: onDelete) {
                Image(systemName: "delete.left")
                    .font(.system(size: IconSize.md))
                    .foregroundStyle(.textPrimary)
            }
            .accessibilityLabel("Effacer le dernier chiffre")
        case .blank:
            Color.clear.frame(width: Keypad.key, height: Keypad.key)
        }
    }

    private func keyBase<Label: View>(action: @escaping () -> Void, @ViewBuilder label: () -> Label) -> some View {
        Button(action: action) {
            label()
                .frame(width: Keypad.key, height: Keypad.key)
                .background(.neutralSurface, in: .rect(cornerRadius: Radius.md))
        }
        .buttonStyle(.plain)
    }
}
