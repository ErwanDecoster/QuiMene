package com.cacompte.domain.stats

import java.util.UUID

/** Miroir de `Badge.swift` — pas de sérialisation (résultat de calcul en mémoire, jamais
 * transmis ni persisté, comme `ValidationResult`). */
data class Badge(
    val kind: Kind,
    val participantID: UUID,
) {
    enum class Kind {
        Winner,
        Metronome,
        Rollercoaster,
        Comeback,
        Kamikaze,
        Unshakeable,
        PhotoFinish,
        Sniper,
        Boulet,
        Landslide,
    }
}
