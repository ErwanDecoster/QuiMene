package com.cacompte.app.features.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.store.AppSettings
import com.cacompte.store.PlayerEntity
import com.cacompte.store.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Miroir de la logique de liste de `PlayersListView.swift` (doc 06) — actifs triés (manuel ou
 * alphabétique selon [AppSettings.PlayerSortMode]), archivés à part. */
class PlayersListViewModel(
    private val repository: PlayerRepository,
    private val settings: AppSettings,
) : ViewModel() {
    data class UiState(
        val active: List<PlayerEntity> = emptyList(),
        val archived: List<PlayerEntity> = emptyList(),
        val sortMode: AppSettings.PlayerSortMode = AppSettings.PlayerSortMode.Automatic,
    )

    val uiState: StateFlow<UiState> =
        combine(repository.observeAll(), settings.playerSortMode) { players, sortMode ->
            val (archived, active) = players.partition { it.isArchived }
            val orderedActive =
                when (sortMode) {
                    AppSettings.PlayerSortMode.Automatic -> active.sortedBy { it.nickname.lowercase() }
                    AppSettings.PlayerSortMode.Manual -> active.sortedBy { it.sortIndex }
                }
            UiState(
                active = orderedActive,
                archived = archived.sortedBy { it.nickname.lowercase() },
                sortMode = sortMode,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun archive(player: PlayerEntity) {
        viewModelScope.launch { repository.archive(player) }
    }

    fun unarchive(player: PlayerEntity) {
        viewModelScope.launch { repository.unarchive(player) }
    }

    fun delete(player: PlayerEntity) {
        viewModelScope.launch { repository.delete(player) }
    }
}
