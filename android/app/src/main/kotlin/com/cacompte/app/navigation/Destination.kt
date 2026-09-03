package com.cacompte.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Routes de navigation typées (Navigation Compose 2.10+, objets `@Serializable` plutôt que des
 * chaînes construites à la main). **Pas de destination « Profil » séparée** au sens Swift (doc 14,
 * identité partagée de l'appareil) : la gestion des joueurs et leurs statistiques (doc 06) sont
 * fusionnées sous [PlayersList] faute de notion d'identité de l'appareil sans profil partagé.
 * **Pas de destination « Classements » de premier niveau** : comme côté Swift
 * (`CaCompteApp.swift`), un classement se rejoint toujours pour un jeu donné, jamais comme liste
 * de tous les jeux — voir [GameLeaderboard], atteint depuis une ligne de [GamesCatalog].
 */
sealed interface Destination {
    @Serializable
    data object GamesCatalog : Destination

    @Serializable
    data class MatchSetup(
        val gameId: String,
    ) : Destination

    @Serializable
    data class LiveMatch(
        val matchId: String,
    ) : Destination

    @Serializable
    data class Results(
        val matchId: String,
    ) : Destination

    @Serializable
    data object PlayersList : Destination

    @Serializable
    data class PlayerEditor(
        val playerId: String?,
    ) : Destination

    @Serializable
    data class PlayerProfile(
        val playerId: String,
    ) : Destination

    @Serializable
    data object History : Destination

    @Serializable
    data class HistoryDetail(
        val matchId: String,
    ) : Destination

    @Serializable
    data class GameLeaderboard(
        val gameId: String,
    ) : Destination

    /** Miroir de `JoinTabView.swift` — écran de repli tant que `:sync` (étape F, transport
     * Supabase Realtime, ADR-0016) n'existe pas : l'onglet est présent dans la barre (même
     * position qu'Apple) mais annonce honnêtement l'indisponibilité plutôt que d'imiter un
     * flux de scan QR qui ne connecterait rien. */
    @Serializable
    data object Join : Destination

    @Serializable
    data object Settings : Destination
}

/** Les 4 destinations racines de la barre de navigation — mêmes 4 onglets, même ordre, mêmes
 * libellés que `CaCompteApp.swift` (Joueurs, Jeux, Rejoindre, Historique). */
enum class RootDestination(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
) {
    Players(Destination.PlayersList, "Joueurs", Icons.Filled.Group),
    Games(Destination.GamesCatalog, "Jeux", Icons.Filled.Games),
    Join(Destination.Join, "Rejoindre", Icons.Filled.QrCodeScanner),
    History(Destination.History, "Historique", Icons.Filled.History),
}
