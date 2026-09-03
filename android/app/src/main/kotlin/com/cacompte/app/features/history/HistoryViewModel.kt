package com.cacompte.app.features.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.launch

/** Miroir de `HistoryListModel.swift` (doc 06) — parties terminées ou abandonnées, non archivées,
 * les plus récentes en premier. Filtre par jeu (comme Apple) ; le filtre par joueur d'Apple n'est
 * pas encore porté (nécessite de croiser les participants, laissé pour une prochaine passe). */
class HistoryViewModel(
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
    initialGameFilter: String? = null,
) : ViewModel() {
    data class Row(
        val match: MatchEntity,
        val gameName: String,
    )

    var rows by mutableStateOf<List<Row>?>(null)
        private set
    var archivedCount by mutableStateOf(0)
        private set
    var selectedGameID by mutableStateOf(initialGameFilter)
        private set

    val availableGames: List<GameDefinition>
        get() =
            rows
                .orEmpty()
                .map { it.match.gameID }
                .distinct()
                .mapNotNull { id -> catalog.allGames.firstOrNull { it.id == id } }
                .sortedBy { it.name.localized }

    val filteredRows: List<Row>
        get() {
            val gameID = selectedGameID ?: return rows.orEmpty()
            return rows.orEmpty().filter { it.match.gameID == gameID }
        }

    init {
        reload()
    }

    fun selectGame(gameID: String?) {
        selectedGameID = gameID
    }

    fun archive(match: MatchEntity) {
        viewModelScope.launch {
            repository.archive(match)
            reload()
        }
    }

    fun reload() {
        viewModelScope.launch {
            val matches = repository.finishedMatches()
            rows =
                matches.map { match ->
                    val name =
                        catalog.allGames
                            .firstOrNull { it.id == match.gameID }
                            ?.name
                            ?.localized ?: match.gameID
                    Row(match, name)
                }
            archivedCount = repository.archivedMatches().size
        }
    }
}
