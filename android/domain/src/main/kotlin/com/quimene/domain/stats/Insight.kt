package com.quimene.domain.stats

import java.util.UUID

/** Miroir de `InsightID.swift`. */
@JvmInline
value class InsightID(
    val rawValue: String,
) {
    companion object {
        val highestRoundScore = InsightID("highestRoundScore")
        val bestRoundScore = InsightID("bestRoundScore")
        val mostRegular = InsightID("mostRegular")
        val mostIrregular = InsightID("mostIrregular")
        val finalGap = InsightID("finalGap")
        val leadChanges = InsightID("leadChanges")
        val longestLeadStreak = InsightID("longestLeadStreak")

        /** Déclarées mais jamais produites par [StatsEngine] — fidèle à la source Swift, qui ne
         * les construit nulle part non plus (voir `.comeback`, le badge correspondant, qui
         * existe bien lui). */
        val remontada = InsightID("remontada")
        val collapse = InsightID("collapse")

        val roundsClosed = InsightID("roundsClosed")
        val doublingsSuffered = InsightID("doublingsSuffered")
    }
}

/** Miroir de `Insight.swift` — pas de sérialisation (résultat de calcul en mémoire). */
data class Insight(
    val id: InsightID,
    val headline: String,
    val detail: String,
    val symbol: String,
    val prominence: Prominence = Prominence.Standard,
    val value: Value,
    /** `internal` (pas `private`), comme côté Swift : lu uniquement par la sélection gloutonne
     * de [StatsEngine], dans le même module. */
    internal val interestScore: Double = 0.0,
) {
    enum class Prominence { Hero, Standard, Minor }

    sealed interface Value {
        data class Single(
            val participantID: UUID?,
            val value: Double,
            val round: Int?,
        ) : Value

        data class PerParticipant(
            val values: Map<UUID, Double>,
        ) : Value
    }

    /** Miroir de l'extension `fileprivate var mentionedParticipants` (bas de `StatsEngine.swift`). */
    internal val mentionedParticipants: List<UUID>
        get() =
            when (val v = value) {
                is Value.Single -> listOfNotNull(v.participantID)
                is Value.PerParticipant -> v.values.keys.toList()
            }
}
