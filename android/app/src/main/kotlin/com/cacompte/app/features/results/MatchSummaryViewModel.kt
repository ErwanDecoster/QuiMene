package com.cacompte.app.features.results

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.model.Round
import com.cacompte.domain.rules.Direction
import com.cacompte.domain.rules.EndConditionType
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.domain.rules.Standing
import com.cacompte.domain.stats.Badge
import com.cacompte.domain.stats.Insight
import com.cacompte.domain.stats.ParticipantSeries
import com.cacompte.domain.stats.StatsEngine
import com.cacompte.store.MatchRepository
import com.cacompte.store.ParticipantEntity
import kotlinx.coroutines.launch
import java.util.UUID

/** Classement final d'une partie conclue — partagé par [com.cacompte.app.features.results.ResultsScreen]
 * (juste après la fin d'une partie) et [com.cacompte.app.features.history.HistoryDetailScreen]
 * (rouvert depuis l'historique) : même calcul, seul le contexte de navigation diffère. Miroir de
 * `ResultsView.swift` : podium + faits marquants (`StatsEngine.insights`) + évolution
 * (`StatsEngine.series`) + manche par manche, pas seulement le classement brut. */
class MatchSummaryViewModel(
    private val matchID: UUID,
    private val catalog: GameCatalog,
    private val repository: MatchRepository,
) : ViewModel() {
    data class UiState(
        val definition: GameDefinition? = null,
        val standings: List<Standing> = emptyList(),
        val participants: Map<UUID, ParticipantEntity> = emptyMap(),
        val badgeByParticipant: Map<UUID, Badge> = emptyMap(),
        val insights: List<Insight> = emptyList(),
        val series: List<ParticipantSeries> = emptyList(),
        val rounds: List<Round> = emptyList(),
        val direction: Direction = Direction.HighestWins,
        val scoreThreshold: Int? = null,
        val isLoading: Boolean = true,
    )

    var uiState by mutableStateOf(UiState())
        private set

    init {
        viewModelScope.launch {
            val match = requireNotNull(repository.match(matchID)) { "Partie introuvable : $matchID" }
            val definition = catalog.definition(match.gameID, match.rulesVersion)
            val rules = catalog.rules(match.gameID, match.rulesVersion)
            val state = repository.loadState(match, catalog)
            val standings = rules.standings(state, definition)
            val participants = repository.participants(matchID).associateBy { it.id }

            val statsEngine = StatsEngine()
            val threshold =
                definition.end.conditions
                    .firstOrNull { it.type == EndConditionType.ScoreThreshold }
                    ?.resolvedValue(state.variants)

            uiState =
                UiState(
                    definition = definition,
                    standings = standings,
                    participants = participants,
                    badgeByParticipant = statsEngine.badges(state, definition).associateBy { it.participantID },
                    insights = statsEngine.insights(state, definition),
                    series = statsEngine.series(state),
                    rounds = state.rounds.sortedBy { it.index },
                    direction = definition.scoring.direction,
                    scoreThreshold = threshold,
                    isLoading = false,
                )
        }
    }
}
