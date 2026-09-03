package com.cacompte.app.features.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Miroir de `HistoryListModel.swift` (doc 06) — parties terminées ou abandonnées, non archivées,
 * les plus récentes en premier. Filtre par jeu (comme Apple) ; le filtre par joueur d'Apple n'est
 * pas encore porté (nécessite de croiser les participants, laissé pour une prochaine passe).
 *
 * Observe [MatchRepository.observeAll] plutôt qu'un chargement ponctuel — remontée utilisateur :
 * l'onglet Historique restait vide après une partie tout juste conclue. Cause : la `ViewModel`
 * d'un onglet vit aussi longtemps que son entrée de pile de retour est conservée par le
 * sélecteur d'onglets (`saveState`/`restoreState`) — `init{}` ne se relance pas à chaque retour
 * sur l'onglet, donc un chargement ponctuel y restait figé sur l'instantané du premier passage. */
class HistoryViewModel(
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
    initialGameFilter: String? = null,
) : ViewModel() {
    data class Row(
        val match: MatchEntity,
        val gameName: String,
    )

    data class UiState(
        val rows: List<Row>? = null,
        val archivedCount: Int = 0,
    )

    val uiState: StateFlow<UiState> =
        repository
            .observeAll()
            .map { matches ->
                val finished =
                    matches
                        .filter {
                            !it.isArchived &&
                                (it.status == MatchStatus.Ended || it.status == MatchStatus.Abandoned)
                        }.sortedByDescending { it.startedAt }
                        .map { match ->
                            val name =
                                catalog.allGames
                                    .firstOrNull { it.id == match.gameID }
                                    ?.name
                                    ?.localized ?: match.gameID
                            Row(match, name)
                        }
                UiState(rows = finished, archivedCount = matches.count { it.isArchived })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    var selectedGameID by mutableStateOf(initialGameFilter)
        private set

    fun selectGame(gameID: String?) {
        selectedGameID = gameID
    }

    fun archive(match: MatchEntity) {
        viewModelScope.launch { repository.archive(match) }
    }
}
