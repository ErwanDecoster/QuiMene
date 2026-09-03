package com.cacompte.app.features.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.store.LeaderboardEntry
import com.cacompte.store.LeaderboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
