package com.quimene.domain.rules

/** Miroir de `GameCatalogError.swift`. */
sealed class GameCatalogError(
    message: String,
) : Exception(message) {
    data class UnknownGame(
        val gameID: String,
        val version: Int,
    ) : GameCatalogError("Jeu inconnu : $gameID (version $version)")

    data class UnknownEngine(
        val engineID: String,
    ) : GameCatalogError("Moteur inconnu : $engineID")
}

/**
 * Miroir de `GameCatalog.swift`. `engineTable` associe un `engineID` (`"skyjo.v1"`,
 * `"generic.sum.v1"`, …) à une fabrique de [GameRules] — mêmes clés que le champ `engine` des
 * fichiers `.json` de `spec/games`.
 */
class GameCatalog(
    definitions: List<GameDefinition>,
    private val engineTable: Map<String, () -> GameRules>,
) {
    private val definitionsByID: Map<String, GameDefinition> = definitions.associateBy { it.id }

    init {
        for (definition in definitions) {
            if (definition.engine !in engineTable) {
                throw GameCatalogError.UnknownEngine(definition.engine)
            }
        }
    }

    fun definition(
        gameID: String,
        version: Int,
    ): GameDefinition {
        val definition = definitionsByID[gameID]
        if (definition == null || definition.rulesVersion != version) {
            throw GameCatalogError.UnknownGame(gameID, version)
        }
        return definition
    }

    fun rules(
        gameID: String,
        version: Int,
    ): GameRules {
        val definition = definition(gameID, version)
        val factory = engineTable[definition.engine] ?: throw GameCatalogError.UnknownEngine(definition.engine)
        return factory()
    }

    val allGames: List<GameDefinition> get() = definitionsByID.values.toList()
}
