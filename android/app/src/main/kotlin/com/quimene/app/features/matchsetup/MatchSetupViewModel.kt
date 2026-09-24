package com.quimene.app.features.matchsetup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quimene.domain.model.VariantSelection
import com.quimene.domain.model.VariantValue
import com.quimene.domain.rules.GameDefinition
import com.quimene.store.MatchEntity
import com.quimene.store.MatchRepository
import com.quimene.store.PlayerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Miroir de `MatchSetupModel.swift`. **Le partage/la partie partagée en direct (doc 09,
 * `JoinTabView`/`QRScannerView`) ne sont pas portés** : dépendent de `:sync` (étape F). Seule la
 * mise en place d'une partie locale est couverte ici.
 */
class MatchSetupViewModel(
    val definition: GameDefinition,
    availablePlayers: List<PlayerEntity>,
    private val repository: MatchRepository,
) : ViewModel() {
    /** Doc utilisateur — les habitués en tête de la liste : sans ça, un groupe de 8+ joueurs
     * doit chercher les mêmes 4-5 noms dans une liste triée arbitrairement à chaque partie. */
    var orderedAvailablePlayers: List<PlayerEntity> = availablePlayers
        private set

    var selectedPlayers by mutableStateOf<List<PlayerEntity>>(emptyList())
        private set

    /** Doc 05 : chaque jeu déclare ses propres variantes — un dictionnaire générique plutôt que
     * des propriétés nommées, pour que ce modèle serve tous les jeux du catalogue. */
    var variantValues by mutableStateOf<Map<String, VariantValue>>(emptyMap())
        private set

    /** Doc 05 « Belote » — assignation d'équipe par joueur, vide si le jeu n'en a pas besoin. */
    var teamAssignment by mutableStateOf<Map<UUID, String>>(emptyMap())
        private set

    private val loadingState = MutableStateFlow(true)
    val isLoading = loadingState.asStateFlow()

    val canStart: Boolean
        get() {
            val countOK = selectedPlayers.size in definition.players.min..definition.players.max
            if (!countOK) return false
            return definition.players.teams == null || teamsAreValid
        }

    /** Chaque équipe doit être complète (`teams.size` joueurs), aucune équipe partielle. */
    val teamsAreValid: Boolean
        get() {
            val teams = definition.players.teams ?: return true
            if (!selectedPlayers.all { teamAssignment.containsKey(it.id) }) return false
            val counts = selectedPlayers.groupBy { teamAssignment.getValue(it.id) }.mapValues { it.value.size }
            return counts.isNotEmpty() && counts.values.all { it == teams.size }
        }

    init {
        for (variant in definition.variants) {
            variantValues = variantValues + (variant.id to variant.defaultValue)
        }
        viewModelScope.launch {
            val counts = repository.participationCounts()
            orderedAvailablePlayers =
                availablePlayers.sortedWith(
                    compareByDescending<PlayerEntity> { counts[it.id] ?: 0 }.thenBy { it.sortIndex },
                )

            val recentForThisGame =
                repository
                    .mostRecentParticipants(definition.id)
                    .filter { last -> availablePlayers.any { it.id == last.id } }
            selectedPlayers =
                if (recentForThisGame.isNotEmpty()) {
                    recentForThisGame
                } else {
                    availablePlayers.sortedByDescending { it.createdAt }.take(definition.players.max)
                }
            if (definition.players.teams != null) reassignTeamsAlternating()
            loadingState.value = false
        }
    }

    /** Texte d'aide de la variante, tel que déclaré dans le JSON du jeu. */
    fun help(variantID: String): String? =
        definition.variants
            .firstOrNull { it.id == variantID }
            ?.help
            ?.localized

    fun toggle(player: PlayerEntity) {
        if (isSelected(player)) {
            selectedPlayers = selectedPlayers.filterNot { it.id == player.id }
            teamAssignment = teamAssignment - player.id
        } else {
            if (selectedPlayers.size >= definition.players.max) return
            selectedPlayers = selectedPlayers + player
        }
        if (definition.players.teams != null) reassignTeamsAlternating()
    }

    fun isSelected(player: PlayerEntity): Boolean = selectedPlayers.any { it.id == player.id }

    fun assign(
        player: PlayerEntity,
        teamID: String,
    ) {
        teamAssignment = teamAssignment + (player.id to teamID)
    }

    /** Répartition par défaut à la sélection : joueurs pairs/impairs alternés entre les
     * équipes, modifiable ensuite au cas par cas. */
    private fun reassignTeamsAlternating() {
        val teams = definition.players.teams ?: return
        val updated = teamAssignment.toMutableMap()
        selectedPlayers.forEachIndexed { index, player ->
            val teamIndex = (index / teams.size) % DEFAULT_TEAM_IDS.size
            updated[player.id] = DEFAULT_TEAM_IDS[teamIndex]
        }
        teamAssignment = updated
    }

    fun setIntVariant(
        variantID: String,
        value: Int,
    ) {
        variantValues = variantValues + (variantID to VariantValue.IntValue(value))
    }

    fun setBoolVariant(
        variantID: String,
        value: Boolean,
    ) {
        variantValues = variantValues + (variantID to VariantValue.BoolValue(value))
    }

    fun setStringVariant(
        variantID: String,
        value: String,
    ) {
        variantValues = variantValues + (variantID to VariantValue.StringValue(value))
    }

    fun intVariant(
        variantID: String,
        default: Int = 0,
    ): Int = (variantValues[variantID] as? VariantValue.IntValue)?.value ?: default

    fun boolVariant(
        variantID: String,
        default: Boolean = false,
    ): Boolean = (variantValues[variantID] as? VariantValue.BoolValue)?.value ?: default

    fun stringVariantOrNull(variantID: String): String? = (variantValues[variantID] as? VariantValue.StringValue)?.value

    fun start(onStarted: (MatchEntity) -> Unit) {
        viewModelScope.launch {
            val seeds =
                selectedPlayers.map { player ->
                    MatchRepository.ParticipantSeed(
                        player = player,
                        nickname = player.nickname,
                        avatarKind = player.avatarKind,
                        avatarValue = player.avatarValue,
                        paletteID = player.paletteID,
                        teamID = teamAssignment[player.id],
                    )
                }
            val match =
                repository.createMatch(
                    gameID = definition.id,
                    rulesVersion = definition.rulesVersion,
                    variants = VariantSelection(variantValues),
                    seeds = seeds,
                )
            onStarted(match)
        }
    }

    companion object {
        val DEFAULT_TEAM_IDS = listOf("A", "B")
    }
}
