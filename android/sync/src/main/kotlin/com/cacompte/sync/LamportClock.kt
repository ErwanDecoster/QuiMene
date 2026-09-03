package com.cacompte.sync

/** Miroir de `LamportClock.swift` — un compteur par pair : [tick] à chaque événement émis,
 * [observe] à chaque événement reçu. Le tri final se fait sur `(lamport, deviceID)`, jamais sur
 * l'horloge murale. */
internal class LamportClock(
    startingAt: ULong = 0uL,
) {
    var value: ULong = startingAt
        private set

    fun tick(): ULong {
        value += 1uL
        return value
    }

    fun observe(received: ULong) {
        value = maxOf(value, received) + 1uL
    }
}
