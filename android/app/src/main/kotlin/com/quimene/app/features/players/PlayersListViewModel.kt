package com.quimene.app.features.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.store.AppSettings
import com.quimene.store.PlayerEntity
import com.quimene.store.PlayerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Miroir de la logique de liste de `PlayersListView.swift` (doc 06) — actifs triés (manuel ou
 * alphabétique selon [AppSettings.PlayerSortMode]) ; les archivés vivent sur un écran séparé
 * ([ArchivedPlayersScreen]), seul leur nombre est porté ici (lien en bas de liste). Mode
 * sélection : miroir de l'`EditButton`/`selectedPlayerIDs` d'Apple, réalisé côté Android comme un
 * mode contextuel de barre de titre plutôt qu'un `EditButton` (aucun équivalent Material) —
 * doc utilisateur, « respecte le style Android ». */
class PlayersListViewModel(
    private val repository: PlayerRepository,
    private val settings: AppSettings,
) : ViewModel() {
    data class UiState(
        /** Doc 16, phase A — mon profil, affiché à part au-dessus de la liste. */
        val me: PlayerEntity? = null,
        val active: List<PlayerEntity> = emptyList(),
        val archivedCount: Int = 0,
        val sortMode: AppSettings.PlayerSortMode = AppSettings.PlayerSortMode.Automatic,
    )

    val uiState: StateFlow<UiState> =
        combine(repository.observeAll(), settings.playerSortMode) { players, sortMode ->
            val me = players.firstOrNull { it.sharedProfileIsMine }
            val (archived, active) = players.filterNot { it.sharedProfileIsMine }.partition { it.isArchived }
            val orderedActive =
                when (sortMode) {
                    AppSettings.PlayerSortMode.Automatic -> active.sortedBy { it.nickname.lowercase() }
                    AppSettings.PlayerSortMode.Manual -> active.sortedBy { it.sortIndex }
                }
            UiState(
                me = me,
                active = orderedActive,
                archivedCount = archived.size,
                sortMode = sortMode,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    var isSelecting by mutableStateOf(false)
        private set
    var selectedPlayerIDs by mutableStateOf<Set<UUID>>(emptySet())
        private set

    fun toggleSelectionMode() {
        isSelecting = !isSelecting
        if (!isSelecting) selectedPlayerIDs = emptySet()
    }

    fun toggleSelected(playerID: UUID) {
        selectedPlayerIDs =
            if (playerID in selectedPlayerIDs) selectedPlayerIDs - playerID else selectedPlayerIDs + playerID
    }

    /** Miroir de `archiveSelected()` — vide la sélection et sort du mode sélection après coup,
     * pas laissé à l'utilisateur de fermer lui-même. */
    fun archiveSelected() {
        val toArchive = uiState.value.active.filter { it.id in selectedPlayerIDs }
        viewModelScope.launch {
            for (player in toArchive) repository.archive(player)
            selectedPlayerIDs = emptySet()
            isSelecting = false
        }
    }
}
