package com.cacompte.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.app.navigation.LocalFloatingNavBarHeight
import com.cacompte.app.ui.toAvatar
import com.cacompte.designsystem.components.AvatarSize
import com.cacompte.designsystem.components.AvatarView
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import com.cacompte.store.ProfileStats
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Miroir de `ProfileView.swift` (doc 06) — statistiques d'un joueur, recalculées à chaque
 * ouverture (aucun agrégat mis en cache). Atteint depuis une ligne de [com.cacompte.app.features.players.PlayersListScreen]
 * ou [com.cacompte.app.features.players.ArchivedPlayersScreen] — jamais directement l'éditeur,
 * qui vit derrière le bouton « Modifier » de cet écran. */
@Composable
fun ProfileScreen(
    playerId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel =
        rememberViewModel {
            ProfileViewModel(
                UUID.fromString(playerId),
                container.playerRepository,
                container.profileRepository,
                container.leaderboardRepository,
                container.catalog,
            )
        }
    val player = viewModel.player
    val stats = viewModel.stats
    val colors = LocalAppColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(player?.nickname ?: "Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(onClick = { onEdit(playerId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (player == null || stats == null) return@Scaffold
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = Space.lg)
                    .padding(bottom = innerPadding.calculateBottomPadding() + LocalFloatingNavBarHeight.current)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarView(player.toAvatar(), size = AvatarSize.Large)
                Text(
                    player.nickname,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }

            if (stats.played == 0) {
                EmptyState(
                    icon = Icons.Filled.BarChart,
                    message = "Aucune partie terminée pour l'instant. La fiche se remplit après la première partie.",
                )
                return@Column
            }

            SummarySection(stats)
            if (stats.byGame.isNotEmpty()) ByGameSection(stats, viewModel.topGameIDs)
            stats.nemesis?.let { NemesisSection(it) }
            StreaksSection(stats)
            if (stats.activity.any { it.count > 0 }) {
                ActivitySection(
                    stats = stats,
                    isShowingFullYear = viewModel.isShowingFullYear,
                    onToggle = viewModel::toggleActivityView,
                )
            }
        }
    }
}

@Composable
private fun SummarySection(stats: ProfileStats) {
    val colors = LocalAppColors.current
    // Locale lue via LocalConfiguration (observable en composition), pas Locale.getDefault()
    // (lint androidx.compose.ui : NonObservableLocale — sans ça, un changement de langue système
    // pendant que l'app tourne ne redéclencherait jamais le formatage de ce nombre).
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("En bref", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Card {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("Parties", "${stats.played}")
                StatBlock("Victoires", "${stats.wins}")
                StatBlock("Taux de victoire", "${(stats.winRate * 100).roundToInt()} %")
                StatBlock("Rang moyen", String.format(locale, "%.1f", stats.averageRank))
            }
        }
    }
}

@Composable
private fun ByGameSection(
    stats: ProfileStats,
    topGameIDs: Set<String>,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("Par jeu", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        for (game in stats.byGame) {
            Card {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Text(game.gameName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        if (game.gameID in topGameIDs) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = "Meilleur joueur",
                                tint = colors.brandBrass,
                                modifier = Modifier.padding(start = Space.xxs),
                            )
                        }
                    }
                    Text(
                        "${game.played} partie(s) · ${game.wins} victoire(s) · ${(game.winRate * 100).roundToInt()} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    game.bestScore?.let {
                        Text(
                            "Meilleur score : ${it.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                    game.worstScore?.let {
                        Text(
                            "Pire score : ${it.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NemesisSection(nemesis: ProfileStats.Nemesis) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("Némésis", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Card {
            Column {
                Text(nemesis.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    "${nemesis.matchesTogether} partie(s) ensemble · " +
                        "${(nemesis.winRateWithThemPresent * 100).roundToInt()} % de victoires",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StreaksSection(stats: ProfileStats) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("Séries", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Card {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("Série en cours", "${stats.currentWinStreak}")
                StatBlock("Record", "${stats.bestWinStreak}")
            }
        }
    }
}

@Composable
private fun ActivitySection(
    stats: ProfileStats,
    isShowingFullYear: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activité", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            TextButton(onClick = onToggle) {
                Text(if (isShowingFullYear) "Voir le mois" else "Voir l'année")
            }
        }
        Card {
            if (isShowingFullYear) {
                ActivityYearChart(stats.activity)
            } else {
                StatBlock("Parties ce mois-ci", "${stats.activity.lastOrNull()?.count ?: 0}")
            }
        }
    }
}

private val ActivityChartBarAreaHeight = 110.dp

@Composable
private fun ActivityYearChart(activity: List<ProfileStats.MonthActivity>) {
    val colors = LocalAppColors.current
    val maxCount = (activity.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (month in activity) {
            val label =
                runCatching { YearMonth.parse(month.monthKey).month.getDisplayName(TextStyle.SHORT, Locale.FRENCH) }
                    .getOrDefault("")
            val barHeight = (ActivityChartBarAreaHeight * (month.count.toFloat() / maxCount)).coerceAtLeast(2.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.height(ActivityChartBarAreaHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier =
                            Modifier
                                .width(16.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(colors.brandInk),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.xxs),
                )
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}
