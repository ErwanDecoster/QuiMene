package com.quimene.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class VariantSelectionTest {
    @Test
    fun `accessors never coerce between value types`() {
        val selection =
            VariantSelection.of(
                "flag" to VariantValue.BoolValue(true),
                "count" to VariantValue.IntValue(3),
                "label" to VariantValue.StringValue("hello"),
            )

        // Le type stocké est un Bool : les accesseurs int/string retombent sur leur défaut,
        // aucune coercion (charte de discipline du domaine, voir GameRules.kt).
        selection.int("flag", default = -1) shouldBe -1
        selection.string("flag", default = "d") shouldBe "d"
        selection.bool("flag", default = false) shouldBe true

        selection.bool("count", default = false) shouldBe false
        selection.int("count", default = -1) shouldBe 3

        selection.int("missing", default = 42) shouldBe 42
    }

    @Test
    fun `unquoted JSON literals resolve to bool then int, quoted values always stay string`() {
        val decoded =
            Json.decodeFromString<VariantSelection>(
                """{"a": true, "b": 1, "c": "true", "d": "1", "e": "hello"}""",
            )

        decoded.bool("a", default = false) shouldBe true
        decoded.int("b", default = -1) shouldBe 1
        // Guillemetée : jamais un Bool, même si le contenu textuel est "true".
        decoded.string("c", default = "") shouldBe "true"
        decoded.bool("c", default = false) shouldBe false
        // Guillemetée : jamais un Int, même si le contenu textuel est "1".
        decoded.string("d", default = "") shouldBe "1"
        decoded.int("d", default = -1) shouldBe -1
        decoded.string("e", default = "") shouldBe "hello"
    }

    @Test
    fun `round-trips through JSON as a plain object, not wrapped`() {
        val selection = VariantSelection.of("threshold" to VariantValue.IntValue(100))
        val encoded = Json.encodeToString(selection)

        encoded shouldBe """{"threshold":100}"""

        val decoded = Json.decodeFromString<VariantSelection>(encoded)
        decoded.int("threshold", default = 0) shouldBe 100
    }
}
