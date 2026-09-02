package com.cacompte.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de `MatchStatus.swift`. Noms d'entrée PascalCase (convention Kotlin/ktlint) avec
 * [SerialName] explicite pour préserver la valeur JSON exacte de la source Swift
 * (`String` raw value = nom du cas, ex. `"inProgress"`). */
@Serializable
enum class MatchStatus {
    @SerialName("inProgress")
    InProgress,

    @SerialName("finalRound")
    FinalRound,

    @SerialName("ended")
    Ended,

    @SerialName("abandoned")
    Abandoned,
}
