package com.cacompte.app.features.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.MatchEntity
import com.cacompte.store.MatchRepository
import kotlinx.coroutines.launch

/** Miroir de la logique de reprise de `GamesTabView.swift` — parties en cours, toutes plateformes
 * confondues (rien n'empêche d'en avoir plusieurs sans avoir terminé la précédente). Rafraîchi
 * manuellement (à l'ouverture de l'écran, après un abandon) plutôt qu'observé en continu — même
 * choix qu'Apple (`refreshInProgressMatches()`), pas besoin d'un flux réactif pour une liste qui
 * ne change qu'à la marge d'une session. */
class GamesCatalogViewModel(
    private val matchRepository: MatchRepository,
    private val catalog: GameCatalog,
) : ViewModel() {
    var inProgressMatches by mutableStateOf<List<MatchEntity>>(emptyList())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { inProgressMatches = matchRepository.inProgressMatches() }
    }

    fun abandon(
        match: MatchEntity,
        deviceID: String,
    ) {
        viewModelScope.launch {
            matchRepository.abandonMatch(match, catalog, deviceID)
            refresh()
        }
    }

    fun gameName(match: MatchEntity): String =
        runCatching { catalog.definition(match.gameID, match.rulesVersion) }
            .getOrNull()
            ?.name
            ?.localized
            ?: match.gameID
}
