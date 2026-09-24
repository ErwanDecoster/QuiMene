package com.quimene.sync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Miroir de `LamportClockTests.swift`. */
class LamportClockTest {
    @Test
    fun `tick increments and returns the new value`() {
        val clock = LamportClock()
        clock.tick() shouldBe 1uL
        clock.tick() shouldBe 2uL
        clock.value shouldBe 2uL
    }

    @Test
    fun `observe adopts max(local, received) + 1`() {
        val clock = LamportClock(startingAt = 3uL)
        clock.observe(10uL)
        clock.value shouldBe 11uL
    }

    @Test
    fun `observe has no effect when the local counter is already ahead`() {
        val clock = LamportClock(startingAt = 10uL)
        clock.observe(2uL)
        clock.value shouldBe 11uL // toujours max(local, reçu) + 1, même quand reçu < local.
    }
}
