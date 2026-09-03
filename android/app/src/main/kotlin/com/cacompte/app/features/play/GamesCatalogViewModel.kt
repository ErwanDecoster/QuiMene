package com.cacompte.app.features.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.model.MatchStatus
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.domain.rules.GameDefinition
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Miroir de la logique de reprise de `GamesTabView.swift` — parties en cours, toutes plateformes
 * confondues (rien n'empêche d'en avoir plusieurs sans avoir terminé la précédente).
 *
 * Observe [MatchRepository.observeAll] plutôt qu'un chargement ponctuel rafraîchi à la main —
 * même bug de fond que celui corrigé sur [com.cacompte.app.features.history.HistoryViewModel] :
 * la `ViewModel` de cet onglet vit aussi longtemps que son entrée de pile de retour, donc un
 * chargement figé à `init{}` ne verrait jamais une partie conclue depuis un autre onglet. */
class GamesCatalogViewModel(
    private val matchRepository: MatchRepository,
    private val catalog: GameCatalog,
) : ViewModel() {
    val inProgressMatches: StateFlow<List<MatchEntity>> =
        matchRepository
            .observeAll()
            .map { matches ->
                matches
                    .filter { it.status == MatchStatus.InProgress || it.status == MatchStatus.FinalRound }
                    .sortedByDescending { it.startedAt }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Miroir de `GamesTabView.searchText` — filtre en direct à chaque frappe, sur le nom et la
     * description courte, dans les 5 langues déclarées (voir [GameDefinition.LocalizedText
     * .matches]), pas seulement la langue affichée. */
    var searchText by mutableStateOf("")
        private set

    val games: List<GameDefinition>
        get() {
            val all = catalog.allGames.sortedBy { it.name.localized }
            if (searchText.isBlank()) return all
            return all.filter { game ->
                game.name.matches(searchText) || (game.shortDescription?.matches(searchText) ?: false)
            }
        }

    fun updateSearchText(value: String) {
        searchText = value
    }

    fun abandon(
        match: MatchEntity,
        deviceID: String,
    ) {
        viewModelScope.launch { matchRepository.abandonMatch(match, catalog, deviceID) }
    }

    fun gameName(match: MatchEntity): String =
        runCatching { catalog.definition(match.gameID, match.rulesVersion) }
            .getOrNull()
            ?.name
            ?.localized
            ?: match.gameID
}
