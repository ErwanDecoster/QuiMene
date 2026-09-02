package com.cacompte.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Routes de navigation typées (Navigation Compose 2.10+, objets `@Serializable` plutôt que des
 * chaînes construites à la main). **Pas de destination « Rejoindre »/QR** (doc 11
 * `JoinTabView`/`QRScannerView`) : dépend de `:sync` (étape F). **Pas de destination « Profil »
 * séparée** au sens Swift (doc 14, identité partagée de l'appareil) : la gestion des joueurs et
 * leurs statistiques (doc 06) sont fusionnées sous [Players] faute de notion d'identité de
 * l'appareil sans profil partagé.
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
    data object Leaderboard : Destination

    @Serializable
    data class GameLeaderboard(
        val gameId: String,
    ) : Destination

    @Serializable
    data object Settings : Destination
}

/** Les 4 destinations racines de la barre de navigation (doc 11 §E). */
enum class RootDestination(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
) {
    Play(Destination.GamesCatalog, "Jouer", Icons.Filled.Casino),
    History(Destination.History, "Historique", Icons.Filled.History),
    Leaderboard(Destination.Leaderboard, "Classements", Icons.Filled.EmojiEvents),
    Players(Destination.PlayersList, "Joueurs", Icons.Filled.Group),
}
