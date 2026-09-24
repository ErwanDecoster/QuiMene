package com.quimene.app.features.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.features.results.MatchSummaryContent
import com.quimene.app.features.results.MatchSummaryViewModel
import com.quimene.app.navigation.LocalFloatingNavBarHeight
import com.quimene.designsystem.tokens.Space
import java.util.UUID

/** Réouverture du classement final d'une partie déjà terminée — même rendu que
 * [com.quimene.app.features.results.ResultsScreen], la seule différence étant le contexte de
 * navigation (ouvert depuis l'historique, pas juste après la fin de la partie). */
@Composable
fun HistoryDetailScreen(
    matchId: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val id = UUID.fromString(matchId)
    val viewModel = rememberViewModel { MatchSummaryViewModel(id, container.catalog, container.matchRepository) }
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.definition?.name?.localized ?: "Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        MatchSummaryContent(
            state,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            contentPadding =
                PaddingValues(
                    top = Space.lg,
                    bottom = Space.lg + innerPadding.calculateBottomPadding() + LocalFloatingNavBarHeight.current,
                ),
        )
    }
}
