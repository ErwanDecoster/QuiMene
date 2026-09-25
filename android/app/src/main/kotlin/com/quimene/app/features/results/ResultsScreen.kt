package com.quimene.app.features.results

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.features.livematch.MeBadge
import com.quimene.app.features.livematch.NextMatchBar
import com.quimene.app.features.livematch.NextMatchPicker
import com.quimene.app.navigation.LocalFloatingNavBarHeight
import com.quimene.app.ui.insightIcon
import com.quimene.app.ui.label
import com.quimene.app.ui.toAvatar
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.IconSize
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.LocalIsDarkTheme
import com.quimene.designsystem.tokens.Radius
import com.quimene.designsystem.tokens.ScoreTypography
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.model.Round
import com.quimene.domain.rules.Direction
import com.quimene.domain.rules.Standing
import com.quimene.domain.stats.Badge
import com.quimene.domain.stats.Insight
import com.quimene.domain.stats.ParticipantSeries
import com.quimene.store.ParticipantEntity
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** Miroir de `ResultsView.swift` (doc 06) : podium (rang, avatar, badge, score), faits marquants
 * (`StatsEngine.insights`), évolution des scores manche par manche, tableau détaillé. Partagé
 * avec [com.quimene.app.features.history.HistoryDetailScreen] via [MatchSummaryContent]. Pas de
 * partage du résumé en image (`ShareLink` côté Apple) — délibérément hors périmètre, écran
 * distinct à construire séparément si besoin. */
@Composable
fun ResultsScreen(
    matchId: String,
    onDone: () -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val id = UUID.fromString(matchId)
    val viewModel = rememberViewModel { MatchSummaryViewModel(id, container.catalog, container.matchRepository) }
    val state = viewModel.uiState
    val shareCoordinator = container.liveShareCoordinator
    val scope = rememberCoroutineScope()
    var isPickingNextMatch by remember { mutableStateOf(false) }
    var isStartingNextMatch by remember { mutableStateOf(false) }

    // Doc 16, phase E — une partie qui vient de se terminer part tout de suite chez les amis liés
    // qui y ont joué, sans attendre un retour au premier plan.
    LaunchedEffect(Unit) { runCatching { container.sharedProfileSyncCoordinator.sync() } }

    // Doc 16, phase C — un autre appareil de la session a lancé la partie suivante : on la suit.
    LaunchedEffect(id) {
        shareCoordinator.remoteStartedMatches.collect { started ->
            if (started.previousMatchID == id) onOpenMatch(started.newMatchID.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.definition?.name?.localized ?: stringResource(R.string.resultats)) },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            MatchSummaryContent(state, modifier = Modifier.weight(1f))
            if (shareCoordinator.attachedMatchID == id) {
                NextMatchBar(
                    isBusy = isStartingNextMatch,
                    modifier = Modifier.padding(horizontal = Space.lg).padding(top = Space.lg),
                ) { isPickingNextMatch = true }
            }
            PrimaryButton(
                text = stringResource(R.string.termine),
                onClick = onDone,
                modifier = Modifier.padding(Space.lg).padding(bottom = LocalFloatingNavBarHeight.current),
            )
        }
    }

    if (isPickingNextMatch) {
        NextMatchPicker(
            playerCount = state.participants.size,
            currentGameID = state.definition?.id.orEmpty(),
            onDismiss = { isPickingNextMatch = false },
        ) { next ->
            scope.launch {
                isStartingNextMatch = true
                try {
                    val previous = container.matchRepository.match(id) ?: return@launch
                    shareCoordinator.startNextMatch(next, previous)?.let { onOpenMatch(it.id.toString()) }
                } finally {
                    isStartingNextMatch = false
                }
            }
        }
    }
}

@Composable
internal fun MatchSummaryContent(
    state: MatchSummaryViewModel.UiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Space.lg),
) {
    val sortedStandings = state.standings.sortedBy { it.rank }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Space.lg),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.xxl),
    ) {
        item {
            PodiumSection(sortedStandings, state.participants, state.badgeByParticipant, state.myParticipantID)
        }
        if (state.insights.isNotEmpty()) {
            item { InsightsSection(state.insights) }
        }
        if (state.series.isNotEmpty() && state.rounds.isNotEmpty()) {
            item {
                EvolutionSection(state.series, state.participants, state.direction, state.scoreThreshold)
            }
        }
        if (state.rounds.isNotEmpty()) {
            item { RoundByRoundSection(state.rounds, sortedStandings, state.participants, state.myParticipantID) }
        }
    }
}

@Composable
private fun PodiumSection(
    sortedStandings: List<Standing>,
    participants: Map<UUID, ParticipantEntity>,
    badgeByParticipant: Map<UUID, Badge>,
    myParticipantID: UUID?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        for (standing in sortedStandings) {
            val participant = participants[standing.participantID] ?: continue
            PodiumRow(
                standing,
                participant,
                badgeByParticipant[standing.participantID],
                isMe = standing.participantID == myParticipantID,
            )
        }
    }
}

@Composable
private fun PodiumRow(
    standing: Standing,
    participant: ParticipantEntity,
    badge: Badge?,
    isMe: Boolean,
) {
    val colors = LocalAppColors.current
    val isDark = LocalIsDarkTheme.current
    val isFirst = standing.rank == 1
    val accentColor = if (isFirst) colors.brandBrass else colors.textSecondary
    val background = if (isFirst) colors.brandBrass.copy(alpha = 0.08f) else colors.neutralSurface

    // Miroir de `Card` (:designsystem) plutôt qu'un simple `Modifier.background()` — même
    // remontée « les listes ne se détachent pas du fond » : tonal + ombre garantit une séparation
    // visuelle même quand le fond est proche de `background` en couleur dynamique.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = background,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = if (isDark) BorderStroke(1.dp, colors.neutralBorder) else null,
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = "${standing.rank}",
                style = MaterialTheme.typography.headlineSmall,
                color = accentColor,
                modifier = Modifier.width(32.dp),
            )
            AvatarView(participant.toAvatar(), size = AvatarSize.Medium)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        participant.nicknameSnapshot,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    if (isMe) MeBadge()
                }
                badge?.let {
                    Text(it.kind.label, style = MaterialTheme.typography.labelMedium, color = colors.brandBrass)
                }
            }
            Text(text = standing.score.toString(), style = ScoreTypography.scoreXL, color = accentColor)
        }
    }
}

@Composable
private fun InsightsSection(insights: List<Insight>) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            stringResource(R.string.faits_marquants),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(CardGutterResults)) {
            for (insight in insights) {
                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Icon(
                            imageVector = insightIcon(insight.symbol),
                            contentDescription = null,
                            tint = colors.brandInk,
                            modifier = Modifier.size(IconSize.lg),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                insight.headline,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                insight.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvolutionSection(
    series: List<ParticipantSeries>,
    participants: Map<UUID, ParticipantEntity>,
    direction: Direction,
    threshold: Int?,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            stringResource(R.string.evolution),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                EvolutionChart(series, participants, direction, threshold)
                EvolutionLegend(series, participants)
            }
        }
    }
}

@Composable
private fun EvolutionChart(
    series: List<ParticipantSeries>,
    participants: Map<UUID, ParticipantEntity>,
    direction: Direction,
    threshold: Int?,
) {
    val colors = LocalAppColors.current
    val gridColor = colors.neutralBorder
    val thresholdColor = colors.semanticWarning
    val labelColorArgb = colors.textTertiary.toArgb()

    fun displayValue(total: Int): Float = if (direction == Direction.LowestWins) -total.toFloat() else total.toFloat()

    val lines =
        series.mapNotNull { entry ->
            val points = entry.points.map { it.round to displayValue(it.total) }
            if (points.size < 2) null else entry to points
        }
    val thresholdValue = threshold?.let { displayValue(it) }
    val allValues = lines.flatMap { (_, points) -> points.map { it.second } } + listOfNotNull(thresholdValue)
    if (allValues.isEmpty()) return

    val minY = allValues.min()
    val maxY = allValues.max()
    val yRange = (maxY - minY).takeIf { it > 0f } ?: 1f
    val maxRound = series.maxOf { entry -> entry.points.maxOfOrNull { it.round } ?: 0 }.coerceAtLeast(1)

    Canvas(modifier = Modifier.fillMaxWidth().height(EvolutionChartHeight)) {
        val leftAxisWidth = 36.dp.toPx()
        val chartWidth = (size.width - leftAxisWidth).coerceAtLeast(1f)
        val chartHeight = size.height

        fun xFor(round: Int): Float = leftAxisWidth + chartWidth * (round / maxRound.toFloat())

        fun yFor(value: Float): Float = chartHeight - ((value - minY) / yRange) * chartHeight

        val tickCount = 4
        val textPaint =
            Paint().apply {
                color = labelColorArgb
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }
        for (tick in 0..tickCount) {
            val value = minY + (yRange * tick / tickCount)
            val y = yFor(value)
            drawLine(gridColor, Offset(leftAxisWidth, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(value.roundToInt().toString(), 0f, y + 4.dp.toPx(), textPaint)
        }

        thresholdValue?.let { value ->
            val y = yFor(value)
            drawLine(
                color = thresholdColor,
                start = Offset(leftAxisWidth, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        for ((entry, points) in lines) {
            val paletteID = participants[entry.id]?.paletteIDSnapshot?.toIntOrNull() ?: 1
            val lineColor = colors.player(paletteID)
            val path = Path()
            points.forEachIndexed { index, (round, value) ->
                val x = xFor(round)
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun EvolutionLegend(
    series: List<ParticipantSeries>,
    participants: Map<UUID, ParticipantEntity>,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        for (entry in series) {
            val paletteID = participants[entry.id]?.paletteIDSnapshot?.toIntOrNull() ?: 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.player(paletteID)),
                )
                Text(entry.name, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun RoundByRoundSection(
    rounds: List<Round>,
    sortedStandings: List<Standing>,
    participants: Map<UUID, ParticipantEntity>,
    myParticipantID: UUID?,
) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            stringResource(R.string.manche_par_manche),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        Card {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        Box(modifier = Modifier.width(RoundColumnWidth))
                        for (standing in sortedStandings) {
                            Column(modifier = Modifier.width(ParticipantColumnWidth).padding(bottom = Space.xs)) {
                                Text(
                                    text = participants[standing.participantID]?.nicknameSnapshot ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (standing.participantID == myParticipantID) MeBadge()
                            }
                        }
                    }
                    for (round in rounds) {
                        Row {
                            Text(
                                text = "${round.index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                modifier = Modifier.width(RoundColumnWidth).padding(vertical = Space.xxs),
                            )
                            for (standing in sortedStandings) {
                                val entry = round.entries.firstOrNull { it.participantID == standing.participantID }
                                Text(
                                    text =
                                        entry?.let {
                                            if (it.rawValue == it.computedValue) {
                                                "${it.computedValue}"
                                            } else {
                                                "${it.rawValue} → ${it.computedValue}"
                                            }
                                        } ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (entry != null) colors.textPrimary else colors.textTertiary,
                                    modifier = Modifier.width(ParticipantColumnWidth).padding(vertical = Space.xxs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CardGutterResults = Space.sm
private val EvolutionChartHeight = 200.dp
private val RoundColumnWidth = 28.dp
private val ParticipantColumnWidth = 88.dp
