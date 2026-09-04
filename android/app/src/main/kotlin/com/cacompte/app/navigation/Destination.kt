package com.cacompte.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector
import com.cacompte.app.R
import kotlinx.serialization.Serializable

/**
 * Routes de navigation typées (Navigation Compose 2.10+, objets `@Serializable` plutôt que des
 * chaînes construites à la main). [PlayerProfile] (statistiques) est distincte de [PlayerEditor]
 * (édition) — miroir de `ProfileView.swift`/`PlayerEditorView.swift` : taper une ligne dans
 * [PlayersList] ouvre le profil, jamais directement l'éditeur (atteint depuis le profil via son
 * bouton « Modifier »). **Pas de destination « Classements » de premier niveau** : comme côté
 * Swift (`CaCompteApp.swift`), un classement se rejoint toujours pour un jeu donné, jamais comme
 * liste de tous les jeux — voir [GameLeaderboard], atteint depuis une ligne de [GamesCatalog].
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

    /** Miroir de `ArchivedPlayersView.swift` — écran séparé plutôt qu'une section toujours
     * visible dans [PlayersList], même logique que [ArchivedMatches]. */
    @Serializable
    data object ArchivedPlayers : Destination

    /** [gameId] non nul quand on arrive depuis le bouton « Historique » d'un classement
     * ([GameLeaderboard]) — miroir de `deepLinkRouter.pendingHistoryGameID` côté Apple : la liste
     * s'ouvre déjà filtrée sur ce jeu plutôt que de forcer l'utilisateur à refiltrer. */
    @Serializable
    data class History(
        val gameId: String? = null,
    ) : Destination

    @Serializable
    data class HistoryDetail(
        val matchId: String,
    ) : Destination

    /** Miroir de `ArchivedMatchesView.swift` — écran séparé plutôt qu'une section toujours
     * visible dans [History] : les parties archivées sont un cas d'usage occasionnel. */
    @Serializable
    data object ArchivedMatches : Destination

    @Serializable
    data class GameLeaderboard(
        val gameId: String,
    ) : Destination

    /** Miroir de `JoinTabView.swift` — code de pairage à 6 chiffres (pas de scan caméra dans
     * cette version, voir [com.cacompte.app.features.join.JoinScreen]). */
    @Serializable
    data object Join : Destination

    @Serializable
    data object Settings : Destination
}

/** Les 4 destinations racines de la barre de navigation — mêmes 4 onglets, même ordre, mêmes
 * libellés que `CaCompteApp.swift` (Joueurs, Jeux, Rejoindre, Historique). */
enum class RootDestination(
    val destination: Destination,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Players(Destination.PlayersList, R.string.joueurs, Icons.Filled.Group),
    Games(Destination.GamesCatalog, R.string.jeux, Icons.Filled.Games),
    Join(Destination.Join, R.string.rejoindre, Icons.Filled.QrCodeScanner),
    History(Destination.History(), R.string.historique, Icons.Filled.History),
}
