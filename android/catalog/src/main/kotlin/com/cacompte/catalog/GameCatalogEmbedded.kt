package com.cacompte.catalog

import com.cacompte.catalog.games.BeloteRulesV1
import com.cacompte.catalog.games.MolkkyRulesV1
import com.cacompte.catalog.games.SkyjoRulesV1
import com.cacompte.catalog.games.TarotRulesV1
import com.cacompte.catalog.games.WizardRulesV1
import com.cacompte.catalog.games.YamsRulesV1
import com.cacompte.catalog.generic.GenericSumRules
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Miroir de `GameCatalog+Embedded.swift` : charge toutes les définitions embarquées et construit
 * un [GameCatalog] avec la table complète des 7 moteurs (le générique + les 6 dédiés — Skyjo,
 * Yams, Belote, Mölkky, Tarot, Wizard — tous réellement implémentés depuis l'étape C, vérifiés
 * par les 24 golden files de `spec/golden/`).
 */
object GameCatalogEmbedded {
    private val json = Json { ignoreUnknownKeys = false }

    val embedded: GameCatalog by lazy {
        GameCatalog(
            definitions = loadDefinitions(),
            engineTable =
                mapOf(
                    GenericSumRules.ENGINE_ID to { GenericSumRules() },
                    SkyjoRulesV1.ENGINE_ID to { SkyjoRulesV1() },
                    YamsRulesV1.ENGINE_ID to { YamsRulesV1() },
                    BeloteRulesV1.ENGINE_ID to { BeloteRulesV1() },
                    MolkkyRulesV1.ENGINE_ID to { MolkkyRulesV1() },
                    TarotRulesV1.ENGINE_ID to { TarotRulesV1() },
                    WizardRulesV1.ENGINE_ID to { WizardRulesV1() },
                ),
        )
    }

    /**
     * Ressource régénérée à chaque build par la tâche Gradle `copySpecResources`
     * (`android/catalog/build.gradle.kts`) à partir de `spec/games/` — jamais committée, ne peut
     * donc pas diverger de la source de vérité. Listage par répertoire de fichiers plutôt que
     * balayage générique de JAR : suffisant pour l'exécution des tests JVM de cette étape ; à
     * revérifier quand `:app` embarque réellement `:catalog` dans un APK packagé (étape D/E) —
     * l'accès en lecture à une ressource *individuelle* via `getResourceAsStream` fonctionne déjà
     * de façon standard sur Android (JAR classpath ordinaire), mais le listage de répertoire par
     * `File(url.toURI())` suppose des ressources non compressées sur le disque.
     */
    private fun loadDefinitions(): List<GameDefinition> {
        val resourceUrl =
            requireNotNull(javaClass.classLoader.getResource("GameDefinitions")) {
                "Ressource GameDefinitions introuvable — copySpecResources a-t-il tourné ?"
            }
        val directory = File(resourceUrl.toURI())
        val files =
            directory.listFiles { file -> file.extension == "json" }
                ?: error("GameDefinitions n'est pas un dossier lisible : $directory")

        return files
            .sortedBy { it.name }
            .map { file -> json.decodeFromString<GameDefinition>(file.readText()) }
    }
}
