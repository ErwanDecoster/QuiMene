package com.cacompte.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de `Role.swift` — trois rôles possibles dans une partie partagée. `Host` n'est jamais
 * demandé par un pair qui rejoint, seuls `Contributor` et `Observer` transitent sur le fil. */
@Serializable
enum class Role {
    @SerialName("host")
    Host,

    @SerialName("contributor")
    Contributor,

    @SerialName("observer")
    Observer,
}
