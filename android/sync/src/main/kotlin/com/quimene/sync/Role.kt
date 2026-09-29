package com.quimene.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de `Role.swift` — trois rôles possibles dans une partie partagée. `Host` (le créateur)
 * n'est jamais demandé par un appareil qui rejoint : seuls `Contributor` et `Observer` le sont. */
@Serializable
enum class Role {
    @SerialName("host")
    Host,

    @SerialName("contributor")
    Contributor,

    @SerialName("observer")
    Observer,
}
