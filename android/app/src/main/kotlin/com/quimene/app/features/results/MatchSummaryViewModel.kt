package com.quimene.app.features.results

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.domain.model.MatchState
import com.quimene.domain.model.Round
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.EndConditionType
import com.quimene.domain.rules.GameCatalog
import com.quimene.domain.rules.GameDefinition
import com.quimene.domain.rules.GameRules
import com.quimene.domain.rules.Standing
import com.quimene.domain.stats.Badge
import com.quimene.domain.stats.Insight
import com.quimene.domain.stats.ParticipantSeries
import com.quimene.domain.stats.StatsEngine
import com.quimene.store.MatchRepository
import com.quimene.store.ParticipantEntity
import kotlinx.coroutines.launch
import java.util.UUID

/** Classement final d'une partie conclue — partagé par [com.quimene.app.features.results.ResultsScreen]
 * (juste après la fin d'une partie) et [com.quimene.app.features.history.HistoryDetailScreen]
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
            val participants = repository.participants(matchID).associateBy { it.id }
            uiState = matchSummaryState(state, definition, rules, participants)
        }
    }
}

/** L'écran de résultats d'une partie, à partir de son état rejoué — partagé par la partie locale
 * (ci-dessus) et par la partie suivie d'un participant d'une session en ligne (doc 16, phase C),
 * qui n'a pas de copie en base : ses [participants] sont alors des fiches en mémoire. */
fun matchSummaryState(
    state: MatchState,
    definition: GameDefinition,
    rules: GameRules,
    participants: Map<UUID, ParticipantEntity>,
): MatchSummaryViewModel.UiState {
    val statsEngine = StatsEngine()
    val threshold =
        definition.end.conditions
            .firstOrNull { it.type == EndConditionType.ScoreThreshold }
            ?.resolvedValue(state.variants)
    return MatchSummaryViewModel.UiState(
        definition = definition,
        standings = rules.standings(state, definition),
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
