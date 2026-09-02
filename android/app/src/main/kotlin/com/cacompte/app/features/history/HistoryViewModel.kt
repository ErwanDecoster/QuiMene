package com.cacompte.app.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Miroir de `HistoryView.swift` (doc 06) — parties terminées ou abandonnées, non archivées,
 * les plus récentes en premier. */
class HistoryViewModel(
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
) : ViewModel() {
    data class Row(
        val match: MatchEntity,
        val gameName: String,
    )

    private val matchesState = MutableStateFlow<List<Row>?>(null)
    val rows = matchesState.asStateFlow()

    init {
        viewModelScope.launch {
            val matches = repository.finishedMatches()
            matchesState.value =
                matches.map { match ->
                    val name =
                        catalog.allGames
                            .firstOrNull { it.id == match.gameID }
                            ?.name
                            ?.localized ?: match.gameID
                    Row(match, name)
                }
        }
    }
}
