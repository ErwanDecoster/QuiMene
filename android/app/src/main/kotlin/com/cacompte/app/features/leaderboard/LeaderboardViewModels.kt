package com.cacompte.app.features.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.store.LeaderboardEntry
import com.cacompte.store.LeaderboardRepository
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Miroir de `LeaderboardView.swift` (doc 06) — seuls les jeux réellement joués (au moins une
 * partie terminée) apparaissent : un classement vide pour un jeu jamais lancé n'apporte rien. */
class LeaderboardListViewModel(
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
) : ViewModel() {
    private val gamesState = MutableStateFlow<List<GameDefinition>?>(null)
    val games = gamesState.asStateFlow()

    init {
        viewModelScope.launch {
            val playedGameIDs = repository.finishedMatches().map { it.gameID }.toSet()
            gamesState.value = catalog.allGames.filter { it.id in playedGameIDs }.sortedBy { it.name.localized }
        }
    }
}

/** Classement d'un jeu donné, doc 06. */
class GameLeaderboardViewModel(
    private val gameID: String,
    private val repository: LeaderboardRepository,
) : ViewModel() {
    private val entriesState = MutableStateFlow<List<LeaderboardEntry>?>(null)
    val entries = entriesState.asStateFlow()

    init {
        viewModelScope.launch { entriesState.value = repository.leaderboard(gameID) }
    }
}
