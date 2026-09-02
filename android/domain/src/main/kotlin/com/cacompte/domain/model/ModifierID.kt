package com.cacompte.domain.model

import kotlinx.serialization.Serializable

/** Miroir de `ModifierID.swift` — chaîne typée (value class Kotlin, `RawRepresentable` côté
 * Swift). */
@Serializable
@JvmInline
value class ModifierID(
    val rawValue: String,
) {
    companion object {
        val closedRound = ModifierID("closedRound")
    }
}
