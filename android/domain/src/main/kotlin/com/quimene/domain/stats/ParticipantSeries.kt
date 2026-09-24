package com.quimene.domain.stats

import java.util.UUID

/** Miroir de `ParticipantSeries.swift` — pas de sérialisation (résultat de calcul en mémoire). */
data class ParticipantSeries(
    val id: UUID,
    val name: String,
    val points: List<Point>,
) {
    data class Point(
        val round: Int,
        val total: Int,
    )
}
