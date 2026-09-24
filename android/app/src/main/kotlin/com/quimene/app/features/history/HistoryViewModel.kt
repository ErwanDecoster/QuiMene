package com.quimene.app.features.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.domain.model.MatchStatus
import com.quimene.domain.rules.GameCatalog
import com.quimene.store.MatchEntity
import com.quimene.store.MatchRepository
import com.quimene.store.ParticipantEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Miroir de `HistoryListModel.swift` (doc 06) — parties terminées ou abandonnées, non archivées,
 * les plus récentes en premier. Filtre par jeu et par joueur (comme Apple).
 *
 * Observe [MatchRepository.observeAll]/[MatchRepository.observeAllParticipants] plutôt qu'un
 * chargement ponctuel — remontée utilisateur : l'onglet Historique restait vide après une partie
 * tout juste conclue. Cause : la `ViewModel` d'un onglet vit aussi longtemps que son entrée de
 * pile de retour est conservée par le sélecteur d'onglets (`saveState`/`restoreState`) —
 * `init{}` ne se relance pas à chaque retour sur l'onglet, donc un chargement ponctuel y restait
 * figé sur l'instantané du premier passage. */
class HistoryViewModel(
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
    initialGameFilter: String? = null,
) : ViewModel() {
    /** Miroir de `HistoryListModel.PlayerFilterID` — un joueur dont la fiche a été supprimée est
     * regroupé par pseudo (`nicknameSnapshot`), pas par participation, sinon la même personne
     * supprimée apparaît une fois par partie jouée dans le filtre. */
    sealed interface PlayerFilterID {
        data class Player(
            val id: UUID,
        ) : PlayerFilterID

        data class DeletedPlayer(
            val nickname: String,
        ) : PlayerFilterID
    }

    data class Row(
        val match: MatchEntity,
        val gameName: String,
        val participants: List<ParticipantEntity>,
    )

    data class UiState(
        val rows: List<Row>? = null,
        val archivedCount: Int = 0,
    )

    val uiState: StateFlow<UiState> =
        combine(repository.observeAll(), repository.observeAllParticipants()) { matches, participants ->
            val participantsByMatch = participants.groupBy { it.matchId }
            val finished =
                matches
                    .filter { !it.isArchived && (it.status == MatchStatus.Ended || it.status == MatchStatus.Abandoned) }
                    .sortedByDescending { it.startedAt }
                    .map { match ->
                        val name =
                            catalog.allGames
                                .firstOrNull { it.id == match.gameID }
                                ?.name
                                ?.localized ?: match.gameID
                        Row(match, name, participantsByMatch[match.id].orEmpty())
                    }
            UiState(rows = finished, archivedCount = matches.count { it.isArchived })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    var selectedGameID by mutableStateOf(initialGameFilter)
        private set
    var selectedPlayerID by mutableStateOf<PlayerFilterID?>(null)
        private set

    fun selectGame(gameID: String?) {
        selectedGameID = gameID
    }

    fun selectPlayer(playerID: PlayerFilterID?) {
        selectedPlayerID = playerID
    }

    fun archive(match: MatchEntity) {
        viewModelScope.launch { repository.archive(match) }
    }

    companion object {
        /** Doc 01 « filtrable par jeu et par joueur » — la suppression d'une fiche joueur annule
         * `ParticipantEntity.playerId` (règle `.nullify`) mais garde `nicknameSnapshot` : le
         * filtre retombe sur le pseudo quand la fiche a été supprimée. */
        fun filterID(participant: ParticipantEntity): PlayerFilterID =
            participant.playerId?.let { PlayerFilterID.Player(it) }
                ?: PlayerFilterID.DeletedPlayer(participant.nicknameSnapshot)
    }
}
