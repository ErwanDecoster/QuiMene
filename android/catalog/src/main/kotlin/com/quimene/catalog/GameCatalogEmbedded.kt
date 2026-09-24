package com.quimene.catalog

import com.quimene.catalog.games.BeloteRulesV1
import com.quimene.catalog.games.MolkkyRulesV1
import com.quimene.catalog.games.SkyjoRulesV1
import com.quimene.catalog.games.TarotRulesV1
import com.quimene.catalog.games.WizardRulesV1
import com.quimene.catalog.games.YamsRulesV1
import com.quimene.catalog.generic.GenericSumRules
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
     * donc pas diverger de la source de vérité. Lue via `getResourceAsStream` (index texte, puis
     * chaque fichier), jamais via `File(url.toURI())` : ce dernier lève "URI is not hierarchical"
     * dès que la ressource est vue à travers un JAR (rencontré en test Robolectric de `:app`,
     * qui consomme `:catalog` packagé) et n'aurait de toute façon pas fonctionné une fois les
     * ressources compressées dans un APK réel — `getResourceAsStream` fonctionne uniformément
     * dans les trois cas (répertoire de classes, JAR, APK).
     */
    private fun loadDefinitions(): List<GameDefinition> {
        val index =
            requireNotNull(javaClass.classLoader.getResourceAsStream("GameDefinitions/index.txt")) {
                "Index GameDefinitions introuvable — copySpecResources a-t-il tourné ?"
            }.bufferedReader().readText()

        val names =
            index
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .sorted()

        return names
            .map { name ->
                val text =
                    requireNotNull(javaClass.classLoader.getResourceAsStream("GameDefinitions/$name")) {
                        "Ressource GameDefinitions/$name introuvable alors que listée dans l'index."
                    }.bufferedReader().readText()
                json.decodeFromString<GameDefinition>(text)
            }.toList()
    }
}
