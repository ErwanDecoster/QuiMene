package com.quimene.catalog.golden

import com.quimene.domain.model.VariantSelection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Miroir de `GoldenFile.swift` — décodage des fichiers `.json` de `spec/golden` (format documenté dans
 * `spec/README.md`). Les identifiants de participants ("p1", "p2"…) sont des jetons de test
 * lisibles, pas des `UUID` : [com.quimene.catalog.golden.GoldenFileTest] les convertit à la
 * volée. `ignoreUnknownKeys` : les golden files portent un champ `description` documentaire non
 * modélisé ici, comme le décodage Swift par défaut l'ignore silencieusement.
 */
@Serializable
data class GoldenFile(
    val goldenId: String,
    val gameId: String,
    val rulesVersion: Int,
    val variants: VariantSelection,
    val participants: List<Participant>,
    val rounds: List<RoundInput>,
    val expected: Expected,
) {
    @Serializable
    data class Participant(
        val id: String,
        val name: String,
        val seatIndex: Int,
        val teamID: String? = null,
    )

    @Serializable
    data class Input(
        val participant: String,
        val rawValue: Int,
        val modifiers: List<String> = emptyList(),
        val detail: Map<String, String>? = null,
    )

    @Serializable
    data class RoundInput(
        val index: Int,
        val inputs: List<Input>,
    )

    @Serializable
    data class RoundResult(
        val index: Int,
        val computed: Map<String, Int>,
        val cumulative: Map<String, Int>,
        val doubled: List<String> = emptyList(),
        val status: String,
    )

    @Serializable
    data class StandingResult(
        val participant: String,
        val rank: Int,
        val score: Int,
        val sharedWith: List<String>? = null,
    )

    @Serializable
    data class FinalResult(
        val status: String,
        val reason: String? = null,
        val standings: List<StandingResult>,
    )

    @Serializable
    data class InsightExpectation(
        val id: String,
        val participant: String? = null,
        val value: Double? = null,
        val round: Int? = null,
        val values: Map<String, Double>? = null,
    )

    @Serializable
    data class Expected(
        val roundResults: List<RoundResult>,
        val final: FinalResult,
        val insights: List<InsightExpectation>,
    )

    override fun toString(): String = goldenId

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Miroir de `GoldenFile.all` — balaie la ressource `GoldenResources` régénérée par la
         * tâche Gradle `copyGoldenResources`, triée par nom de fichier. */
        val all: List<GoldenFile> by lazy {
            val resourceUrl =
                requireNotNull(GoldenFile::class.java.classLoader.getResource("GoldenResources")) {
                    "Ressource GoldenResources introuvable — copyGoldenResources a-t-il tourné ?"
                }
            val directory = File(resourceUrl.toURI())
            val files =
                directory.listFiles { file -> file.extension == "json" }
                    ?: error("GoldenResources n'est pas un dossier lisible : $directory")

            files.sortedBy { it.name }.map { file -> json.decodeFromString<GoldenFile>(file.readText()) }
        }
    }
}
