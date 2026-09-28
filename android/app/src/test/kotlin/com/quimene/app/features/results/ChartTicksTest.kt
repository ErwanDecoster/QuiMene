package com.quimene.app.features.results

import io.kotest.matchers.shouldBe
import org.junit.Test

class ChartTicksTest {
    @Test
    fun `ticks are round values that enclose the data`() {
        niceTicks(10f, 465f) shouldBe listOf(0f, 100f, 200f, 300f, 400f, 500f)
    }

    @Test
    fun `lowest wins totals are negated, ticks still round`() {
        // Skyjo : −174 … −5 en valeurs affichées → 200, 150, 100, 50, 0 une fois les signes rétablis.
        niceTicks(-174f, -5f) shouldBe listOf(-200f, -150f, -100f, -50f, 0f)
    }

    @Test
    fun `small ranges keep a step of at least one point`() {
        niceTicks(0f, 3f) shouldBe listOf(0f, 1f, 2f, 3f)
    }

    @Test
    fun `a flat series still gets two ticks`() {
        niceTicks(0f, 0f) shouldBe listOf(0f, 1f)
        niceTicks(40f, 40f) shouldBe listOf(40f, 41f)
    }
}
