package com.quimene.app.features.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.store.PlayerEntity
import com.quimene.store.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Miroir de `ArchivedPlayersView.swift` (doc 06). */
class ArchivedPlayersViewModel(
    private val repository: PlayerRepository,
) : ViewModel() {
    val archived =
        repository
            .observeAll()
            .map { players -> players.filter { it.isArchived }.sortedBy { it.nickname.lowercase() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun unarchive(player: PlayerEntity) {
        viewModelScope.launch { repository.unarchive(player) }
    }

    fun delete(player: PlayerEntity) {
        viewModelScope.launch { repository.delete(player) }
    }
}
