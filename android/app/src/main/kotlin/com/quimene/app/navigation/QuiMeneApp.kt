package com.quimene.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.quimene.app.features.history.ArchivedMatchesScreen
import com.quimene.app.features.history.HistoryDetailScreen
import com.quimene.app.features.history.HistoryListScreen
import com.quimene.app.features.join.JoinScreen
import com.quimene.app.features.leaderboard.GameLeaderboardScreen
import com.quimene.app.features.livematch.LiveMatchScreen
import com.quimene.app.features.matchsetup.MatchSetupScreen
import com.quimene.app.features.play.GamesCatalogScreen
import com.quimene.app.features.players.ArchivedPlayersScreen
import com.quimene.app.features.players.PlayerEditorScreen
import com.quimene.app.features.players.PlayersListScreen
import com.quimene.app.features.profile.ProfileScreen
import com.quimene.app.features.results.ResultsScreen
import com.quimene.app.features.settings.SettingsScreen
import com.quimene.designsystem.tokens.Space

/** Hauteur réellement occupée par l'îlot flottant de [RootNavigationBar] (mesurée à l'exécution,
 * marge + barre de geste système incluses) — à ajouter au padding bas du contenu défilant de
 * chaque écran pour qu'il puisse défiler *derrière* l'îlot (doc utilisateur) sans que son dernier
 * élément reste durablement caché dessous. `0.dp` tant que la barre n'a pas encore été mesurée
 * (première composition). */
val LocalFloatingNavBarHeight = compositionLocalOf { 0.dp }

/** Racine de l'UI — barre de navigation flottante à 4 onglets (Joueurs, Jeux, Rejoindre,
 * Historique — mêmes 4, même ordre que `QuiMeneApp.swift`) superposée au `NavHost`, qui occupe
 * tout l'écran (le contenu défile derrière l'îlot plutôt que de s'arrêter au-dessus, doc
 * utilisateur) + `NavHost` typé sur [Destination]. */
@Composable
fun QuiMeneApp() {
    val navController = rememberNavController()
    var navBarHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFloatingNavBarHeight provides navBarHeight) {
            NavHost(
                navController = navController,
                startDestination = Destination.PlayersList,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<Destination.GamesCatalog> {
                    GamesCatalogScreen(
                        onGameSelected = { gameId -> navController.navigate(Destination.MatchSetup(gameId)) },
                        onOpenLeaderboard = { gameId -> navController.navigate(Destination.GameLeaderboard(gameId)) },
                        onResumeMatch = { matchId -> navController.navigate(Destination.LiveMatch(matchId)) },
                    )
                }
                composable<Destination.MatchSetup> { backStackEntry ->
                    val route: Destination.MatchSetup = backStackEntry.toRoute()
                    MatchSetupScreen(
                        gameId = route.gameId,
                        onMatchStarted = { matchId ->
                            navController.navigate(Destination.LiveMatch(matchId)) {
                                popUpTo(Destination.GamesCatalog)
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Destination.LiveMatch> { backStackEntry ->
                    val route: Destination.LiveMatch = backStackEntry.toRoute()
                    LiveMatchScreen(
                        matchId = route.matchId,
                        onConcluded = { matchId ->
                            navController.navigate(Destination.Results(matchId)) {
                                popUpTo(Destination.GamesCatalog)
                            }
                        },
                        onAbandoned = { navController.popBackStack(Destination.GamesCatalog, inclusive = false) },
                    )
                }
                composable<Destination.Results> { backStackEntry ->
                    val route: Destination.Results = backStackEntry.toRoute()
                    ResultsScreen(
                        matchId = route.matchId,
                        onDone = { navController.popBackStack(Destination.GamesCatalog, inclusive = false) },
                    )
                }
                composable<Destination.PlayersList> {
                    PlayersListScreen(
                        onAddPlayer = { navController.navigate(Destination.PlayerEditor(null)) },
                        onOpenProfile = { playerId -> navController.navigate(Destination.PlayerProfile(playerId)) },
                        onOpenArchivedPlayers = { navController.navigate(Destination.ArchivedPlayers) },
                    )
                }
                composable<Destination.PlayerEditor> { backStackEntry ->
                    val route: Destination.PlayerEditor = backStackEntry.toRoute()
                    PlayerEditorScreen(
                        playerId = route.playerId,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<Destination.PlayerProfile> { backStackEntry ->
                    val route: Destination.PlayerProfile = backStackEntry.toRoute()
                    ProfileScreen(
                        playerId = route.playerId,
                        onBack = { navController.popBackStack() },
                        onEdit = { playerId -> navController.navigate(Destination.PlayerEditor(playerId)) },
                    )
                }
                composable<Destination.ArchivedPlayers> {
                    ArchivedPlayersScreen(
                        onOpenProfile = { playerId -> navController.navigate(Destination.PlayerProfile(playerId)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Destination.History> { backStackEntry ->
                    val route: Destination.History = backStackEntry.toRoute()
                    HistoryListScreen(
                        initialGameFilter = route.gameId,
                        onOpenMatch = { matchId -> navController.navigate(Destination.HistoryDetail(matchId)) },
                        onOpenArchivedMatches = { navController.navigate(Destination.ArchivedMatches) },
                        onOpenSettings = { navController.navigate(Destination.Settings) },
                    )
                }
                composable<Destination.HistoryDetail> { backStackEntry ->
                    val route: Destination.HistoryDetail = backStackEntry.toRoute()
                    HistoryDetailScreen(matchId = route.matchId, onBack = { navController.popBackStack() })
                }
                composable<Destination.ArchivedMatches> {
                    ArchivedMatchesScreen(onBack = { navController.popBackStack() })
                }
                composable<Destination.GameLeaderboard> { backStackEntry ->
                    val route: Destination.GameLeaderboard = backStackEntry.toRoute()
                    GameLeaderboardScreen(
                        gameId = route.gameId,
                        onBack = { navController.popBackStack() },
                        onOpenHistory = { gameId ->
                            navController.navigate(Destination.History(gameId)) {
                                popUpTo(Destination.GamesCatalog)
                            }
                        },
                    )
                }
                composable<Destination.Join> {
                    JoinScreen()
                }
                composable<Destination.Settings> {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        RootNavigationBar(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
            onIslandHeightMeasured = { height -> navBarHeight = height },
        )
    }
}

/** Barre de navigation flottante — îlot arrondi, séparé des bords de l'écran et surélevé (ombre),
 * plutôt qu'une barre pleine largeur collée en bas (doc utilisateur). Dessinée par-dessus le
 * `NavHost` (même `Box`, ajoutée en second) plutôt que dans un `Scaffold.bottomBar` : le contenu
 * défilant de chaque écran doit pouvoir passer *derrière* elle, pas s'arrêter au-dessus (voir
 * [LocalFloatingNavBarHeight]).
 *
 * [onIslandHeightMeasured] rapporte la hauteur de l'îlot **seul** (marge + contenu), mesurée
 * *après* avoir consommé l'inset système (`windowInsetsPadding`, plus bas dans la chaîne de
 * modificateurs) — sans quoi chaque écran qui l'ajoute à son propre `innerPadding` (qui compte
 * déjà cet inset une fois, via son propre `Scaffold`) le compterait deux fois : la « Saisir un
 * code » de [com.quimene.app.features.join.JoinScreen] apparaissait ainsi trop haute. */
@Composable
private fun RootNavigationBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onIslandHeightMeasured: (Dp) -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val density = LocalDensity.current

    Surface(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .onGloballyPositioned { coordinates ->
                    onIslandHeightMeasured(with(density) { coordinates.size.height.toDp() })
                }.padding(horizontal = Space.lg, vertical = Space.sm),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        NavigationBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
        ) {
            for (root in RootDestination.entries) {
                val selected =
                    currentDestination?.hierarchy?.any {
                        when (root) {
                            RootDestination.Players -> it.hasRoute<Destination.PlayersList>()
                            RootDestination.Games -> it.hasRoute<Destination.GamesCatalog>()
                            RootDestination.Join -> it.hasRoute<Destination.Join>()
                            RootDestination.History -> it.hasRoute<Destination.History>()
                        }
                    } == true

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        // Doc utilisateur — retaper l'onglet courant (même en profondeur dans un
                        // écran poussé depuis sa racine, ex. le profil d'un joueur) doit revenir à
                        // la première page de cet onglet, sur les 4 onglets. `popBackStack` ne
                        // pop que si la racine de l'onglet est déjà quelque part sur la pile
                        // actuelle (donc seulement quand on est *dans* cet onglet) ; sinon (on
                        // change réellement d'onglet), repli sur le patron standard qui préserve
                        // l'état de chaque onglet entre deux sélections.
                        val poppedToTabRoot = navController.popBackStack(root.destination, inclusive = false)
                        if (!poppedToTabRoot) {
                            navController.navigate(root.destination) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { Icon(root.icon, contentDescription = null) },
                    label = { Text(stringResource(root.labelRes)) },
                )
            }
        }
    }
}
