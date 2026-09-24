package com.quimene.catalog.testing

/**
 * Miroir de `SeededGenerator.swift` (`CatalogTests`) — SplitMix64, générateur déterministe : un
 * test qui échoue redevient reproductible à partir de son seed, contrairement à un générateur
 * système. Pour les petits seeds réellement utilisés par les tests (0..49), produit la même
 * séquence bit-à-bit que la version Swift (même élargissement signe-préservant vers 64 bits).
 */
class SeededGenerator(
    seed: Int,
) {
    private var state: ULong = seed.toLong().toULong() + 0x9E3779B97F4A7C15uL

    fun nextULong(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    fun nextInt(range: IntRange): Int {
        val span = (range.last - range.first + 1).toULong()
        return (nextULong() % span).toInt() + range.first
    }

    fun nextBoolean(): Boolean = nextULong() % 2uL == 0uL

    fun <T> randomElement(items: List<T>): T = items[nextInt(items.indices)]
}
