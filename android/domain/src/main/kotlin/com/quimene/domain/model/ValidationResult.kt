package com.quimene.domain.model

import java.util.UUID

/** Miroir de `ValidationResult.swift` — pas de sérialisation, résultat de calcul en mémoire
 * uniquement (jamais transmis ni persisté). */
sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Warning(
        val messages: List<String>,
    ) : ValidationResult

    data class Invalid(
        val errors: List<ValidationError>,
    ) : ValidationResult
}

/** Miroir de `ValidationError.swift`. */
data class ValidationError(
    val field: Field,
    val message: String,
) {
    sealed interface Field {
        data class ParticipantField(
            val participantID: UUID,
        ) : Field

        data class ModifierField(
            val modifierID: ModifierID,
        ) : Field

        data object General : Field
    }
}
