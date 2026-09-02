package com.cacompte.domain.rules

import com.cacompte.domain.testing.TestGameRules
import com.cacompte.domain.testing.testDefinition
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GameCatalogTest {
    @Test
    fun `constructing the catalog fails loudly when a definition references an unregistered engine`() {
        val definition = testDefinition(engine = "unregistered.v1")

        val error =
            shouldThrow<GameCatalogError.UnknownEngine> {
                GameCatalog(definitions = listOf(definition), engineTable = emptyMap())
            }
        error.engineID shouldBe "unregistered.v1"
    }

    @Test
    fun `definition lookup fails when the id is unknown`() {
        val definition = testDefinition(id = "skyjo")
        val catalog =
            GameCatalog(
                definitions = listOf(definition),
                engineTable = mapOf(definition.engine to { TestGameRules(definition.engine) }),
            )

        shouldThrow<GameCatalogError.UnknownGame> { catalog.definition("yams", 1) }
    }

    @Test
    fun `definition lookup fails when the id exists but the rules version does not match`() {
        val definition = testDefinition(id = "skyjo")
        val catalog =
            GameCatalog(
                definitions = listOf(definition),
                engineTable = mapOf(definition.engine to { TestGameRules(definition.engine) }),
            )

        shouldThrow<GameCatalogError.UnknownGame> { catalog.definition("skyjo", 2) }
    }

    @Test
    fun `rules factory is invoked fresh for each lookup`() {
        val definition = testDefinition(id = "skyjo")
        var callCount = 0
        val catalog =
            GameCatalog(
                definitions = listOf(definition),
                engineTable =
                    mapOf(
                        definition.engine to {
                            callCount++
                            TestGameRules(definition.engine)
                        },
                    ),
            )

        catalog.rules("skyjo", 1)
        catalog.rules("skyjo", 1)

        callCount shouldBe 2
    }
}
