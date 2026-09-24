package com.quimene.app.features.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.domain.rules.GameCatalog
import com.quimene.store.MatchEntity
import com.quimene.store.MatchRepository
import kotlinx.coroutines.launch

/** Miroir de `ArchivedMatchesView.swift` (doc 06). */
class ArchivedMatchesViewModel(
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
) : ViewModel() {
    var matches by mutableStateOf<List<MatchEntity>>(emptyList())
        private set

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { matches = repository.archivedMatches() }
    }

    fun unarchive(match: MatchEntity) {
        viewModelScope.launch {
            repository.unarchive(match)
            reload()
        }
    }

    fun delete(match: MatchEntity) {
        viewModelScope.launch {
            repository.delete(match)
            reload()
        }
    }

    fun gameName(match: MatchEntity): String =
        catalog.allGames
            .firstOrNull { it.id == match.gameID }
            ?.name
            ?.localized ?: match.gameID
}
