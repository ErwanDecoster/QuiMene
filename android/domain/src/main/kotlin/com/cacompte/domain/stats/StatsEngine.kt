package com.cacompte.domain.stats

import com.cacompte.domain.model.MatchState
import com.cacompte.domain.model.ModifierID
import com.cacompte.domain.model.Participant
import com.cacompte.domain.model.Round
import com.cacompte.domain.rules.Direction
import com.cacompte.domain.rules.GameDefinition
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

private enum class Extreme { Max, Min }

/**
 * Miroir de `StatsEngine.swift` — sans état, tout est recalculé à la demande (aucune propriété
 * stockée), comme côté Swift. Aucune arithmétique de date (règle du domaine, doc 11) : tout
 * l'ordre temporel passe par `Round.index`, jamais `Round.committedAt`, exactement comme le
 * source Swift vérifié en exploration.
 *
 * Deux corrections délibérées par rapport au comportement Swift observé (documentées ici, pas
 * silencieuses) : les choix « déviation la plus faible/forte » et « leader le plus fréquent »
 * dans [badges] dépendaient de l'ordre d'itération d'un `Dictionary` Swift, non spécifié — donc
 * non déterministe d'une exécution à l'autre. Ici, départage explicite par `seatIndex`
 * (le premier participant à égalité, en ordre de sièges, l'emporte) pour un résultat
 * reproductible et testable. Ce n'est PAS le même genre de choix que le quirk
 * `.worstSingleRound` de `GameRules` (comportement stable, déjà figé par des golden files) —
 * ici il n'existe aucun golden file pour figer un résultat qui varie déjà d'un run à l'autre côté
 * Swift, donc rien à reproduire fidèlement.
 */
class StatsEngine {
    fun series(state: MatchState): List<ParticipantSeries> {
        val sortedRounds = state.rounds.sortedBy { it.index }
        val sortedParticipants = state.participants.sortedBy { it.seatIndex }

        return sortedParticipants.map { participant ->
            var running = 0
            val points = mutableListOf<ParticipantSeries.Point>()
            for (round in sortedRounds) {
                val entry = round.entries.firstOrNull { it.participantID == participant.id }
                if (entry != null) running += entry.computedValue
                points += ParticipantSeries.Point(round = round.index, total = running)
            }
            ParticipantSeries(id = participant.id, name = participant.displayName, points = points)
        }
    }

    fun badges(
        state: MatchState,
        definition: GameDefinition,
    ): List<Badge> {
        if (state.rounds.size < 3) return emptyList()

        val direction = definition.scoring.direction
        val participants = state.participants.sortedBy { it.seatIndex }
        val rounds = state.rounds.sortedBy { it.index }
        val totals = state.totals()
        if (totals.isEmpty()) return emptyList()

        val candidates = LinkedHashMap<UUID, MutableList<Badge.Kind>>()

        fun addCandidate(
            id: UUID,
            kind: Badge.Kind,
        ) {
            candidates.getOrPut(id) { mutableListOf() } += kind
        }

        val extremeTotal = if (direction == Direction.HighestWins) totals.values.max() else totals.values.min()
        val winners = participants.filter { (totals[it.id] ?: 0) == extremeTotal }

        if (winners.size == 1) {
            val winner = winners.first()
            addCandidate(winner.id, Badge.Kind.Winner)

            val sortedTotals =
                participants.map { totals[it.id] ?: 0 }.let {
                    if (direction == Direction.HighestWins) it.sortedDescending() else it.sorted()
                }
            if (sortedTotals.size >= 2) {
                val gap = abs(sortedTotals[0] - sortedTotals[1]).toDouble()
                if (gap < 3) addCandidate(winner.id, Badge.Kind.PhotoFinish)

                val allValues = rounds.flatMap { round -> round.entries.map { it.computedValue.toDouble() } }
                val mean = if (allValues.isEmpty()) 0.0 else allValues.sum() / allValues.size
                val spread = standardDeviation(allValues, mean)
                if (spread > 0 && gap / spread > 3) addCandidate(winner.id, Badge.Kind.Landslide)
            }
        }

        if (direction != Direction.TargetExact) {
            extremeSingleRound(rounds, if (direction == Direction.HighestWins) Extreme.Max else Extreme.Min)
                ?.let { (id, _, _) -> addCandidate(id, Badge.Kind.Sniper) }
        }

        if (direction == Direction.HighestWins) {
            extremeSingleRound(rounds, Extreme.Min)?.let { (id, _, _) -> addCandidate(id, Badge.Kind.Boulet) }
        }

        val deviations = standardDeviations(rounds, participants)
        if (deviations.size >= 2) {
            val average = deviations.values.average()
            val lowest = pickExtreme(participants, deviations, selectMax = false)
            val highest = pickExtreme(participants, deviations, selectMax = true)
            if (lowest != null && average > 0 && lowest.second < average * 0.6) {
                addCandidate(lowest.first, Badge.Kind.Metronome)
            }
            if (highest != null && average > 0 && highest.second > average * 1.6) {
                addCandidate(highest.first, Badge.Kind.Rollercoaster)
            }
        }

        if (direction == Direction.LowestWins) {
            extremeSingleRound(rounds, Extreme.Max)?.let { (id, _, _) -> addCandidate(id, Badge.Kind.Kamikaze) }
        }

        val leaders = leaderSequence(rounds, participants, direction)
        if (leaders.isNotEmpty()) {
            val counts = leaders.groupingBy { it }.eachCount()
            val mostFrequent = pickExtreme(participants, counts.mapValues { it.value.toDouble() }, selectMax = true)
            if (mostFrequent != null && mostFrequent.second / leaders.size.toDouble() >= 0.8) {
                addCandidate(mostFrequent.first, Badge.Kind.Unshakeable)
            }
        }

        val rankSeries = rankSequence(rounds, participants, direction)
        for (participant in participants) {
            val ranks = rankSeries[participant.id] ?: continue
            if (ranks.isEmpty()) continue
            val worst = ranks.max()
            val final = ranks.last()
            if (worst - final >= 3) addCandidate(participant.id, Badge.Kind.Comeback)
        }

        val rarityOrder =
            listOf(
                Badge.Kind.Landslide,
                Badge.Kind.PhotoFinish,
                Badge.Kind.Comeback,
                Badge.Kind.Rollercoaster,
                Badge.Kind.Sniper,
                Badge.Kind.Boulet,
                Badge.Kind.Kamikaze,
                Badge.Kind.Metronome,
                Badge.Kind.Unshakeable,
                Badge.Kind.Winner,
            )
        return participants.mapNotNull { participant ->
            val kinds = candidates[participant.id] ?: return@mapNotNull null
            val chosen = rarityOrder.firstOrNull { it in kinds } ?: return@mapNotNull null
            Badge(kind = chosen, participantID = participant.id)
        }
    }

    fun insights(
        state: MatchState,
        definition: GameDefinition,
    ): List<Insight> = select(candidates(state, definition))

    /**
     * Miroir de `candidates(state:definition:)` — `internal` côté Swift (pas `public`), rendu
     * accessible aux tests via `@testable import`. Kotlin n'a pas d'équivalent à
     * `@testable import` : `internal` y est une visibilité de module, opaque à `:catalog` qui
     * dépend de `:domain` sans y être compilé. D'où `public` ici — [insights] reste le point
     * d'entrée destiné à l'app ; cette fonction est un point d'accroche pour les golden files
     * (`:catalog`), pas une API applicative.
     */
    fun candidates(
        state: MatchState,
        definition: GameDefinition,
    ): List<Insight> {
        if (state.rounds.isEmpty()) return emptyList()

        val direction = definition.scoring.direction
        val participants = state.participants.sortedBy { it.seatIndex }
        val rounds = state.rounds.sortedBy { it.index }
        val nameByID = participants.associate { it.id to it.displayName }

        val allValues = rounds.flatMap { round -> round.entries.map { it.computedValue.toDouble() } }
        val overallMean = if (allValues.isEmpty()) 0.0 else allValues.sum() / allValues.size
        val overallSpread = maxOf(standardDeviation(allValues, overallMean), 1.0)

        val result = mutableListOf<Insight>()

        extremeSingleRound(rounds, Extreme.Max)?.let { (id, value, round) ->
            val z = abs(value - overallMean) / overallSpread
            result +=
                Insight(
                    id = InsightID.highestRoundScore,
                    headline = "Plus gros tour",
                    detail = "${nameByID[id]} — ${value.toInt()} points, manche ${round + 1}",
                    symbol = "flame.fill",
                    value = Insight.Value.Single(id, value, round),
                    interestScore = z,
                )
        }

        extremeSingleRound(rounds, if (direction == Direction.HighestWins) Extreme.Max else Extreme.Min)
            ?.let { (id, value, round) ->
                val z = abs(value - overallMean) / overallSpread
                result +=
                    Insight(
                        id = InsightID.bestRoundScore,
                        headline = "Meilleur tour",
                        detail = "${nameByID[id]} — ${value.toInt()} points, manche ${round + 1}",
                        symbol = "star.fill",
                        value = Insight.Value.Single(id, value, round),
                        interestScore = z * 0.9,
                    )
            }

        val deviations = standardDeviations(rounds, participants)
        if (deviations.size >= 2) {
            val average = deviations.values.average()
            pickExtreme(participants, deviations, selectMax = false)?.let { (id, lowest) ->
                result +=
                    Insight(
                        id = InsightID.mostRegular,
                        headline = "Le Métronome",
                        detail = "${nameByID[id]} — écart-type ${round2(lowest)}",
                        symbol = "metronome",
                        value = Insight.Value.Single(id, round2(lowest), null),
                        interestScore = if (average > 0) (average - lowest) / average else 0.0,
                    )
            }
            pickExtreme(participants, deviations, selectMax = true)?.let { (id, highest) ->
                result +=
                    Insight(
                        id = InsightID.mostIrregular,
                        headline = "Les montagnes russes",
                        detail = "${nameByID[id]} — écart-type ${round2(highest)}",
                        symbol = "chart.line.uptrend.xyaxis",
                        value = Insight.Value.Single(id, round2(highest), null),
                        interestScore = if (average > 0) (highest - average) / average else 0.0,
                    )
            }
        }

        val sortedTotals =
            run {
                val totals = state.totals()
                participants.map { totals[it.id] ?: 0 }.let {
                    if (direction == Direction.HighestWins) it.sortedDescending() else it.sorted()
                }
            }
        if (sortedTotals.size >= 2) {
            val gap = abs(sortedTotals[0] - sortedTotals[1]).toDouble()
            val relativeGap = if (overallSpread > 0) gap / overallSpread else gap
            result +=
                Insight(
                    id = InsightID.finalGap,
                    headline = "Écart final",
                    detail = "${gap.toInt()} points entre le premier et le deuxième",
                    symbol = "arrow.left.and.right",
                    value = Insight.Value.Single(null, gap, null),
                    interestScore = if (relativeGap < 0.5) (0.5 - relativeGap) * 2 else 0.0,
                )
        }

        val leaders = leaderSequence(rounds, participants, direction)
        if (leaders.isNotEmpty()) {
            val changes = leaders.zipWithNext().count { (a, b) -> a != b }
            result +=
                Insight(
                    id = InsightID.leadChanges,
                    headline = "Changements de tête",
                    detail = if (changes == 0) "Domination du début à la fin" else "$changes changement(s) de leader",
                    symbol = "arrow.left.arrow.right",
                    value = Insight.Value.Single(null, changes.toDouble(), null),
                    interestScore =
                        when {
                            changes == 0 -> 1.2
                            changes >= 4 -> 1.0
                            else -> 0.2
                        },
                )

            longestStreak(leaders)?.let { (id, streak) ->
                result +=
                    Insight(
                        id = InsightID.longestLeadStreak,
                        headline = "Plus longue série en tête",
                        detail = "${nameByID[id]} — $streak manche(s) d'affilée",
                        symbol = "crown.fill",
                        value = Insight.Value.Single(id, streak.toDouble(), null),
                        interestScore = streak.toDouble() / rounds.size,
                    )
            }
        }

        if ("skyjo" in definition.statsProfiles) {
            val closedCounts = LinkedHashMap<UUID, Int>()
            val doubledCounts = LinkedHashMap<UUID, Int>()
            for (participant in participants) {
                closedCounts[participant.id] = 0
                doubledCounts[participant.id] = 0
            }
            for (round in rounds) {
                for (entry in round.entries) {
                    if (ModifierID.closedRound in entry.modifiers) {
                        closedCounts[entry.participantID] = (closedCounts[entry.participantID] ?: 0) + 1
                    }
                    if (entry.computedValue != entry.rawValue) {
                        doubledCounts[entry.participantID] = (doubledCounts[entry.participantID] ?: 0) + 1
                    }
                }
            }
            result +=
                Insight(
                    id = InsightID.roundsClosed,
                    headline = "Manches fermées",
                    detail = "Répartition des fermetures de manche",
                    symbol = "lock.fill",
                    value = Insight.Value.PerParticipant(closedCounts.mapValues { it.value.toDouble() }),
                    interestScore = 0.3,
                )
            result +=
                Insight(
                    id = InsightID.doublingsSuffered,
                    headline = "Doublements subis",
                    detail = "Répartition des scores doublés",
                    symbol = "multiply.circle.fill",
                    value = Insight.Value.PerParticipant(doubledCounts.mapValues { it.value.toDouble() }),
                    interestScore = if (doubledCounts.values.any { it > 0 }) 0.5 else 0.1,
                )
        }

        return result
    }

    /**
     * Sélection gloutonne — au plus 6 faits, pénalité de 0,3 par mention déjà utilisée,
     * le premier fait est toujours accepté (garantit au moins un résultat s'il existe un
     * candidat), les suivants doivent dépasser un score ajusté de 0,2.
     */
    private fun select(candidates: List<Insight>): List<Insight> {
        var remaining = candidates
        val selected = mutableListOf<Insight>()
        val mentionCounts = HashMap<UUID, Int>()

        while (selected.size < 6 && remaining.isNotEmpty()) {
            val scored =
                remaining.map { insight ->
                    val penalty = insight.mentionedParticipants.sumOf { (mentionCounts[it] ?: 0) } * 0.3
                    insight to (insight.interestScore - penalty)
                }
            val (best, score) = scored.maxByOrNull { it.second } ?: break
            if (score <= 0.2 && selected.isNotEmpty()) break

            selected += best
            for (participant in best.mentionedParticipants) {
                mentionCounts[participant] = (mentionCounts[participant] ?: 0) + 1
            }
            remaining = remaining.filterNot { it.id == best.id && it.value == best.value }
        }
        return selected
    }

    private fun round2(value: Double): Double = round(value * 100) / 100

    /** Départage déterministe par ordre de sièges (voir note de tête de fichier) — [participants]
     * doit déjà être trié par `seatIndex`. */
    private fun <T : Number> pickExtreme(
        participants: List<Participant>,
        values: Map<UUID, T>,
        selectMax: Boolean,
    ): Pair<UUID, Double>? {
        var best: Pair<UUID, Double>? = null
        for (participant in participants) {
            val value = values[participant.id]?.toDouble() ?: continue
            val current = best
            if (current == null || (if (selectMax) value > current.second else value < current.second)) {
                best = participant.id to value
            }
        }
        return best
    }

    private fun extremeSingleRound(
        rounds: List<Round>,
        pick: Extreme,
    ): Triple<UUID, Double, Int>? {
        var extremeValue: Double? = null
        for (round in rounds) {
            for (entry in round.entries) {
                val value = entry.computedValue.toDouble()
                extremeValue =
                    when {
                        extremeValue == null -> value
                        pick == Extreme.Max && value > extremeValue -> value
                        pick == Extreme.Min && value < extremeValue -> value
                        else -> extremeValue
                    }
            }
        }
        val target = extremeValue ?: return null

        var holder: Triple<UUID, Double, Int>? = null
        for (round in rounds) {
            for (entry in round.entries) {
                if (entry.computedValue.toDouble() == target) {
                    val current = holder
                    if (current == null) {
                        holder = Triple(entry.participantID, target, round.index)
                    } else if (current.first != entry.participantID) {
                        return null // valeur ambiguë entre deux participants distincts
                    }
                }
            }
        }
        return holder
    }

    private fun standardDeviation(
        values: List<Double>,
        mean: Double,
    ): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun standardDeviations(
        rounds: List<Round>,
        participants: List<Participant>,
    ): Map<UUID, Double> {
        val result = LinkedHashMap<UUID, Double>()
        for (participant in participants) {
            val values =
                rounds.mapNotNull { round ->
                    round.entries
                        .firstOrNull { it.participantID == participant.id }
                        ?.computedValue
                        ?.toDouble()
                }
            result[participant.id] =
                if (values.size <= 1) {
                    0.0
                } else {
                    standardDeviation(values, values.average())
                }
        }
        return result
    }

    private fun currentLeader(
        totals: Map<UUID, Int>,
        participants: List<Participant>,
        direction: Direction,
    ): UUID {
        var bestId = participants.first().id
        var bestValue = totals[bestId] ?: 0
        for (participant in participants.drop(1)) {
            val value = totals[participant.id] ?: 0
            val better = if (direction == Direction.HighestWins) value > bestValue else value < bestValue
            if (better) {
                bestId = participant.id
                bestValue = value
            }
        }
        return bestId
    }

    private fun leaderSequence(
        rounds: List<Round>,
        participants: List<Participant>,
        direction: Direction,
    ): List<UUID> {
        val running = HashMap<UUID, Int>()
        for (participant in participants) running[participant.id] = 0
        val sequence = mutableListOf<UUID>()
        for (round in rounds) {
            for (entry in round.entries) {
                running[entry.participantID] = (running[entry.participantID] ?: 0) + entry.computedValue
            }
            sequence += currentLeader(running, participants, direction)
        }
        return sequence
    }

    private fun longestStreak(sequence: List<UUID>): Pair<UUID, Int>? {
        if (sequence.isEmpty()) return null
        var bestId = sequence[0]
        var bestLength = 1
        var currentId = sequence[0]
        var currentLength = 1
        for (index in 1 until sequence.size) {
            val id = sequence[index]
            if (id == currentId) {
                currentLength++
            } else {
                currentId = id
                currentLength = 1
            }
            if (currentLength > bestLength) {
                bestLength = currentLength
                bestId = currentId
            }
        }
        return bestId to bestLength
    }

    private fun rankSequence(
        rounds: List<Round>,
        participants: List<Participant>,
        direction: Direction,
    ): Map<UUID, List<Int>> {
        val running = HashMap<UUID, Int>()
        val series = LinkedHashMap<UUID, MutableList<Int>>()
        for (participant in participants) {
            running[participant.id] = 0
            series[participant.id] = mutableListOf()
        }
        for (round in rounds) {
            for (entry in round.entries) {
                running[entry.participantID] = (running[entry.participantID] ?: 0) + entry.computedValue
            }
            val ordered =
                participants.map { it.id }.sortedWith(
                    if (direction == Direction.HighestWins) {
                        compareByDescending { running[it] ?: 0 }
                    } else {
                        compareBy { running[it] ?: 0 }
                    },
                )
            for ((rank, id) in ordered.withIndex()) {
                series.getValue(id).add(rank + 1)
            }
        }
        return series
    }
}
