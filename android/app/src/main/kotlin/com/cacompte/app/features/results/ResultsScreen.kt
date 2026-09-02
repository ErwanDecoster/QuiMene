package com.cacompte.app.features.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.ui.toAvatar
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.PrimaryButton
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.domain.rules.Standing
import com.cacompte.store.ParticipantEntity
import java.util.UUID

/** Miroir de `ResultsView.swift` (doc 05) — classement final juste après la conclusion d'une
 * partie. */
@Composable
fun ResultsScreen(
    matchId: String,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val id = UUID.fromString(matchId)
    val viewModel = rememberViewModel { MatchSummaryViewModel(id, container.catalog, container.matchRepository) }
    val state = viewModel.uiState

    Scaffold(
        topBar = { TopAppBar(title = { Text(state.definition?.name?.localized ?: "Résultats") }) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            StandingsList(state.standings, state.participants, modifier = Modifier.weight(1f))
            PrimaryButton(text = "Terminé", onClick = onDone, modifier = Modifier.padding(Space.lg))
        }
    }
}

@Composable
internal fun StandingsList(
    standings: List<Standing>,
    participants: Map<UUID, ParticipantEntity>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(CardGutter),
        contentPadding = PaddingValues(vertical = Space.lg),
    ) {
        items(standings, key = { it.participantID }) { standing ->
            StandingRow(standing, participants[standing.participantID])
        }
    }
}

@Composable
private fun StandingRow(
    standing: Standing,
    participant: ParticipantEntity?,
) {
    val colors = LocalAppColors.current
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = "#${standing.rank}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
                modifier = Modifier.width(40.dp),
            )
            if (participant != null) {
                AvatarView(participant.toAvatar(), size = AvatarSize.Small)
                Text(
                    text = participant.nicknameSnapshot,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(text = "—", modifier = Modifier.weight(1f))
            }
            Text(
                text = standing.score.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
    }
}
