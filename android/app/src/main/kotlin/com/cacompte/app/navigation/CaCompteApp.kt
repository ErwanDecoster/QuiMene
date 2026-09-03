package com.cacompte.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.cacompte.app.features.history.ArchivedMatchesScreen
import com.cacompte.app.features.history.HistoryDetailScreen
import com.cacompte.app.features.history.HistoryListScreen
import com.cacompte.app.features.join.JoinScreen
import com.cacompte.app.features.leaderboard.GameLeaderboardScreen
import com.cacompte.app.features.livematch.LiveMatchScreen
import com.cacompte.app.features.matchsetup.MatchSetupScreen
import com.cacompte.app.features.play.GamesCatalogScreen
import com.cacompte.app.features.players.PlayerEditorScreen
import com.cacompte.app.features.players.PlayersListScreen
import com.cacompte.app.features.results.ResultsScreen
import com.cacompte.app.features.settings.SettingsScreen

/** Racine de l'UI — `Scaffold` avec barre de navigation basse à 4 onglets (Joueurs, Jeux,
 * Rejoindre, Historique — mêmes 4, même ordre que `CaCompteApp.swift`) + `NavHost` typé sur
 * [Destination]. */
@Composable
fun CaCompteApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { RootNavigationBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.PlayersList,
            modifier = Modifier.padding(innerPadding),
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
                    onEditPlayer = { playerId -> navController.navigate(Destination.PlayerEditor(playerId)) },
                )
            }
            composable<Destination.PlayerEditor> { backStackEntry ->
                val route: Destination.PlayerEditor = backStackEntry.toRoute()
                PlayerEditorScreen(
                    playerId = route.playerId,
                    onDone = { navController.popBackStack() },
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
}

@Composable
private fun RootNavigationBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
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
                    navController.navigate(root.destination) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(root.icon, contentDescription = null) },
                label = { Text(root.label) },
            )
        }
    }
}
