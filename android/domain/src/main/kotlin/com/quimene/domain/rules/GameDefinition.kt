package com.quimene.domain.rules

import com.quimene.domain.model.VariantSelection
import com.quimene.domain.model.VariantValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Miroir de `GameDefinition.swift` et de ses types imbriqués — un type Kotlin pour un type
 * Swift, mêmes noms, mêmes valeurs par défaut (posées ici comme valeurs de paramètre Kotlin,
 * plus simple que le `init(from:)` manuel de Swift). Champs et valeurs par défaut vérifiés
 * contre `spec/schema/game-definition.schema.json`, pas seulement contre le rapport
 * d'exploration du source Swift.
 */
@Serializable
data class GameDefinition(
    val id: String,
    val specVersion: Int,
    val rulesVersion: Int,
    val name: LocalizedText,
    val shortDescription: LocalizedText? = null,
    val symbol: String,
    val paletteID: PaletteToken = PaletteToken.Ink,
    val players: Players,
    val scoring: Scoring,
    val engine: String,
    val end: End,
    val tieBreak: List<TieBreakRule> = listOf(TieBreakRule.Shared),
    val variants: List<Variant> = emptyList(),
    val statsProfiles: List<String> = listOf("standard"),
) {
    val requiresCloserSelection: Boolean
        get() = scoring.modifiers.any { it.kind == ModifierKind.ExclusiveFlag && it.required }
}

/** Miroir de `GameDefinition.LocalizedText`. */
@Serializable
data class LocalizedText(
    val fr: String,
    val en: String? = null,
    val es: String? = null,
    val de: String? = null,
    val it: String? = null,
) {
    /** Langue système, repli sur [fr] — miroir de `localized` (`Bundle.main.preferredLocalizations`
     * côté Swift ; `Locale.getDefault()` en JVM pur, `:domain` n'a pas de contexte Android). */
    val localized: String
        get() {
            val translated =
                when (Locale.getDefault().language) {
                    "en" -> en
                    "es" -> es
                    "de" -> de
                    "it" -> it
                    else -> null
                }
            return translated ?: fr
        }

    /** Insensible aux espaces (pas seulement en début/fin — partout dans la chaîne, des deux
     * côtés) et à la langue affichée : compare contre les 5 traductions déclarées, pas seulement
     * celle actuellement montrée — miroir de `LocalizedText.matches(_:)` (Swift). */
    fun matches(term: String): Boolean {
        val needle = term.filter { !it.isWhitespace() }
        if (needle.isEmpty()) return false
        return listOfNotNull(fr, en, es, de, it).any { candidate ->
            candidate.filter { !it.isWhitespace() }.contains(needle, ignoreCase = true)
        }
    }
}

/** Miroir de `GameDefinition.PaletteToken`. */
@Serializable
enum class PaletteToken {
    @SerialName("ink")
    Ink,

    @SerialName("brass")
    Brass,

    @SerialName("teal")
    Teal,

    @SerialName("azur")
    Azur,

    @SerialName("ambre")
    Ambre,

    @SerialName("emeraude")
    Emeraude,

    @SerialName("magenta")
    Magenta,

    @SerialName("ardoise")
    Ardoise,

    @SerialName("cyan")
    Cyan,

    @SerialName("vermillon")
    Vermillon,

    @SerialName("violet")
    Violet,

    @SerialName("olive")
    Olive,
}

/** Miroir de `GameDefinition.Players`. */
@Serializable
data class Players(
    val min: Int,
    val max: Int,
    val recommended: List<Int> = emptyList(),
    val teams: Teams? = null,
) {
    /** Miroir de `GameDefinition.Players.Teams`. */
    @Serializable
    data class Teams(
        val size: Int,
        val fixed: Boolean = true,
    )
}

/** Miroir de `GameDefinition.EntryKind`. */
@Serializable
enum class EntryKind {
    @SerialName("integer")
    Integer,

    @SerialName("categorySheet")
    CategorySheet,

    @SerialName("structured")
    Structured,

    @SerialName("rank")
    Rank,

    @SerialName("predictionAndResult")
    PredictionAndResult,
}

/** Miroir de `GameDefinition.Category`. */
@Serializable
data class Category(
    val id: String,
    val label: LocalizedText,
    val section: Section,
    val scoring: Scoring,
) {
    @Serializable
    enum class Section {
        @SerialName("upper")
        Upper,

        @SerialName("lower")
        Lower,
    }

    /** Nom identique à `Scoring` top-niveau côté Swift (deux types distincts qui portent le même
     * nom court, désambiguïsés par l'imbrication — Kotlin le permet nativement, comme Swift). */
    @Serializable
    data class Scoring(
        val kind: Kind,
        val value: Int? = null,
        val max: Int? = null,
    ) {
        @Serializable
        enum class Kind {
            @SerialName("fixed")
            Fixed,

            @SerialName("sumOfDice")
            SumOfDice,

            @SerialName("multipleOf")
            MultipleOf,
        }
    }
}

/** Miroir de `GameDefinition.RankOption`. */
@Serializable
data class RankOption(
    val id: String,
    val label: LocalizedText,
    val points: Int,
)

/** Miroir de `GameDefinition.ScoreEntrySpec`. */
@Serializable
data class ScoreEntrySpec(
    val kind: EntryKind,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int = 1,
    val allowsNegative: Boolean = false,
    val warnBelow: Int? = null,
    val warnAbove: Int? = null,
    val categories: List<Category>? = null,
    val ranks: List<RankOption>? = null,
)

/** Miroir de `GameDefinition.ModifierKind`. */
@Serializable
enum class ModifierKind {
    @SerialName("exclusiveFlag")
    ExclusiveFlag,

    @SerialName("flag")
    Flag,

    @SerialName("counter")
    Counter,
}

/**
 * Miroir de `GameDefinition.Modifier` — renommé `ScoreModifier` (pas `Modifier`) : `Modifier`
 * collisionnerait systématiquement avec `androidx.compose.ui.Modifier`, omniprésent dans le
 * code Compose des étapes suivantes. Mêmes champs, même sémantique, seul le nom diffère —
 * `Modifier` n'existe pas comme type au top-niveau côté Swift (pas de collision là-bas).
 */
@Serializable
data class ScoreModifier(
    val id: String,
    val label: LocalizedText,
    val kind: ModifierKind,
    val required: Boolean = false,
    val max: Int? = null,
)

/** Miroir de `GameDefinition.Direction`. */
@Serializable
enum class Direction {
    @SerialName("lowestWins")
    LowestWins,

    @SerialName("highestWins")
    HighestWins,

    @SerialName("targetExact")
    TargetExact,
}

/** Miroir de `GameDefinition.Aggregation`. */
@Serializable
enum class Aggregation {
    @SerialName("cumulative")
    Cumulative,

    @SerialName("lastRoundOnly")
    LastRoundOnly,

    @SerialName("bestRound")
    BestRound,
}

/** Miroir du `Scoring` top-niveau (`GameDefinition.swift`) — champ JSON `"modifiers"`, décodé en
 * `List<ScoreModifier>` (voir la note de renommage ci-dessus). */
@Serializable
data class Scoring(
    val direction: Direction,
    val aggregation: Aggregation = Aggregation.Cumulative,
    val roundLabel: LocalizedText? = null,
    val entry: ScoreEntrySpec,
    val modifiers: List<ScoreModifier> = emptyList(),
)

/** Miroir de `GameDefinition.EndConditionType`. */
@Serializable
enum class EndConditionType {
    @SerialName("scoreThreshold")
    ScoreThreshold,

    @SerialName("roundLimit")
    RoundLimit,

    @SerialName("targetReached")
    TargetReached,

    @SerialName("allSheetsComplete")
    AllSheetsComplete,

    @SerialName("elimination")
    Elimination,

    @SerialName("manualStop")
    ManualStop,
}

/** Miroir de `GameDefinition.Comparison` — valeurs JSON symboliques (`">="`, pas
 * `"greaterThanOrEqual"`), comme le raw value Swift. */
@Serializable
enum class Comparison {
    @SerialName(">=")
    GreaterThanOrEqual,

    @SerialName(">")
    GreaterThan,

    @SerialName("==")
    EqualTo,

    @SerialName("<=")
    LessThanOrEqual,

    @SerialName("<")
    LessThan,
    ;

    fun evaluate(
        lhs: Int,
        rhs: Int,
    ): Boolean =
        when (this) {
            GreaterThanOrEqual -> lhs >= rhs
            GreaterThan -> lhs > rhs
            EqualTo -> lhs == rhs
            LessThanOrEqual -> lhs <= rhs
            LessThan -> lhs < rhs
        }
}

/** Miroir de `GameDefinition.Scope`. */
@Serializable
enum class Scope {
    @SerialName("anyPlayer")
    AnyPlayer,

    @SerialName("allPlayers")
    AllPlayers,

    @SerialName("anyTeam")
    AnyTeam,
}

/** Miroir de `GameDefinition.EndCondition`. */
@Serializable
data class EndCondition(
    val type: EndConditionType,
    val value: Int? = null,
    val valueExpression: String? = null,
    val comparison: Comparison = Comparison.GreaterThanOrEqual,
    val scope: Scope = Scope.AnyPlayer,
    val completeRound: Boolean = true,
    val variantKey: String? = null,
) {
    fun resolvedValue(variants: VariantSelection): Int =
        if (variantKey != null) variants.int(variantKey, value ?: 0) else (value ?: 0)
}

/** Miroir de `GameDefinition.End`. */
@Serializable
data class End(
    val conditions: List<EndCondition>,
)

/** Miroir de `GameDefinition.TieBreakRule` / `TieBreakRule` top-niveau. */
@Serializable
enum class TieBreakRule {
    @SerialName("bestSingleRound")
    BestSingleRound,

    @SerialName("worstSingleRound")
    WorstSingleRound,

    @SerialName("fewestRoundsClosed")
    FewestRoundsClosed,

    @SerialName("mostRoundsWon")
    MostRoundsWon,

    @SerialName("lowerSecondaryScore")
    LowerSecondaryScore,

    @SerialName("higherSecondaryScore")
    HigherSecondaryScore,

    @SerialName("headToHead")
    HeadToHead,

    @SerialName("shared")
    Shared,
}

/** Miroir de `GameDefinition.VariantKind`. */
@Serializable
enum class VariantKind {
    @SerialName("bool")
    Bool,

    @SerialName("integerChoice")
    IntegerChoice,

    @SerialName("integerRange")
    IntegerRange,

    @SerialName("option")
    Option,
}

/** Miroir de `GameDefinition.Variant` — clé JSON `"default"` (mot réservé mou en Kotlin,
 * utilisable comme nom de propriété mais renommé `defaultValue` pour rester lisible en usage). */
@Serializable
data class Variant(
    val id: String,
    val label: LocalizedText,
    val help: LocalizedText? = null,
    val kind: VariantKind,
    val values: List<VariantValue> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    @SerialName("default") val defaultValue: VariantValue,
)
