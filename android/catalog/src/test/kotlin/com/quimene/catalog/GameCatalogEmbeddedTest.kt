package com.quimene.catalog

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Critère "Fini quand" de l'étape B (docs/11-portage-android.md) : les 20 définitions chargent
 * sans exception, et la table moteur→ID est exhaustive — ce test échouerait si un JSON de
 * `spec/games/` référençait un moteur absent de la table (`GameCatalogError.UnknownEngine`,
 * levée par le constructeur de `GameCatalog`), miroir du test Swift équivalent.
 */
class GameCatalogEmbeddedTest {
    @Test
    fun `loads all 20 game definitions from spec games without throwing`() {
        val catalog = GameCatalogEmbedded.embedded

        catalog.allGames shouldHaveSize 20
    }

    @Test
    fun `every declared engine id is reachable through the catalog`() {
        val catalog = GameCatalogEmbedded.embedded
        val engineIds = catalog.allGames.map { it.engine }.toSet()

        engineIds shouldContainExactlyInAnyOrder
            setOf(
                "generic.sum.v1",
                "skyjo.v1",
                "yams.v1",
                "belote.v1",
                "molkky.v1",
                "tarot.v1",
                "wizard.v1",
            )

        for (definition in catalog.allGames) {
            // Ne doit jamais lever GameCatalogError.UnknownEngine — l'exhaustivité de la table
            // est déjà vérifiée à la construction de `embedded`, mais on la revérifie ici
            // explicitement par jeu, comme le fait le test Swift homologue.
            catalog.rules(definition.id, definition.rulesVersion)
        }
    }

    @Test
    fun `fourteen games use the generic sum engine`() {
        val catalog = GameCatalogEmbedded.embedded

        catalog.allGames.count { it.engine == "generic.sum.v1" } shouldBe 14
    }
}
