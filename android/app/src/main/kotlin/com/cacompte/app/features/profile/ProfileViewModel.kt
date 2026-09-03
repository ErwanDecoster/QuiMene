package com.cacompte.app.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cacompte.domain.rules.GameCatalog
import com.cacompte.store.LeaderboardRepository
import com.cacompte.store.PlayerEntity
import com.cacompte.store.PlayerRepository
import com.cacompte.store.ProfileRepository
import com.cacompte.store.ProfileStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Miroir de `ProfileModel.swift` (doc 06) — aucun agrégat mis en cache, tout est recalculé à
 * chaque chargement (« un agrégat stocké est un agrégat qui finira désynchronisé »). */
class ProfileViewModel(
    private val playerId: UUID,
    private val playerRepository: PlayerRepository,
    private val profileRepository: ProfileRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val catalog: GameCatalog,
) : ViewModel() {
    var player by mutableStateOf<PlayerEntity?>(null)
        private set
    var stats by mutableStateOf<ProfileStats?>(null)
        private set

    /** Jeux où ce joueur est actuellement 1ᵉʳ au classement, au moins un autre joueur l'ayant
     * aussi joué — miroir de `topGameIDs` (`ProfileView.swift`), affiché comme une couronne à
     * côté du jeu concerné dans « Par jeu ». */
    var topGameIDs by mutableStateOf<Set<String>>(emptySet())
        private set

    var isShowingFullYear by mutableStateOf(false)
        private set

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val current = playerRepository.observeAll().first().firstOrNull { it.id == playerId } ?: return@launch
            player = current
            val computed = runCatching { profileRepository.stats(current, catalog) }.getOrDefault(ProfileStats.empty)
            stats = computed
            topGameIDs =
                computed.byGame
                    .mapNotNull { breakdown ->
                        val entries = leaderboardRepository.leaderboard(breakdown.gameID)
                        if (entries.size >= 2 && entries.first().playerID == playerId) breakdown.gameID else null
                    }.toSet()
        }
    }

    fun toggleActivityView() {
        isShowingFullYear = !isShowingFullYear
    }
}
