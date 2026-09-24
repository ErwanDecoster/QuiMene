package com.quimene.app.features.matchsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.navigation.LocalFloatingNavBarHeight
import com.quimene.app.ui.toAvatar
import com.quimene.designsystem.components.AvatarSize
import com.quimene.designsystem.components.AvatarView
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.CardGutter
import com.quimene.designsystem.components.Chip
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.domain.model.VariantValue
import com.quimene.domain.rules.Variant
import com.quimene.domain.rules.VariantKind
import com.quimene.store.PlayerEntity

/** Miroir de `MatchSetupView.swift` (doc 05) — sélection des joueurs, variantes du jeu,
 * assignation d'équipe si le jeu en a une, puis lancement. */
@Composable
fun MatchSetupScreen(
    gameId: String,
    onMatchStarted: (String) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val definition = container.catalog.allGames.first { it.id == gameId }
    val availablePlayers by container.playerRepository.observeAll().collectAsState(initial = null)
    val players = availablePlayers?.filterNot { it.isArchived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(definition.name.localized) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { innerPadding ->
        if (players == null) return@Scaffold
        val viewModel =
            rememberViewModel { MatchSetupViewModel(definition, players, container.matchRepository) }
        MatchSetupContent(
            viewModel = viewModel,
            onMatchStarted = onMatchStarted,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun MatchSetupContent(
    viewModel: MatchSetupViewModel,
    onMatchStarted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            item {
                Text(
                    stringResource(R.string.joueurs),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Space.lg),
                )
            }
            items(viewModel.orderedAvailablePlayers, key = { it.id }) { player ->
                PlayerSelectionRow(
                    player = player,
                    isSelected = viewModel.isSelected(player),
                    team = viewModel.teamAssignment[player.id],
                    onToggle = { viewModel.toggle(player) },
                    onCycleTeam = {
                        val next = if (viewModel.teamAssignment[player.id] == "A") "B" else "A"
                        viewModel.assign(player, next)
                    },
                    showTeam = viewModel.definition.players.teams != null && viewModel.isSelected(player),
                )
            }
            if (viewModel.definition.variants.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.variantes),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                }
                items(viewModel.definition.variants, key = { it.id }) { variant ->
                    VariantControl(variant, viewModel)
                }
            }
        }

        PrimaryButton(
            text = stringResource(R.string.commencer),
            enabled = viewModel.canStart,
            onClick = { viewModel.start { match -> onMatchStarted(match.id.toString()) } },
            modifier = Modifier.padding(Space.lg).padding(bottom = LocalFloatingNavBarHeight.current),
        )
    }
}

@Composable
private fun PlayerSelectionRow(
    player: PlayerEntity,
    isSelected: Boolean,
    team: String?,
    showTeam: Boolean,
    onToggle: () -> Unit,
    onCycleTeam: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            AvatarView(player.toAvatar(), size = AvatarSize.Small)
            Text(
                text = player.nickname,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (showTeam && team != null) {
                Chip(title = stringResource(R.string.equipe_value1, team), isSelected = true, onClick = onCycleTeam)
            }
            Switch(checked = isSelected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun VariantControl(
    variant: Variant,
    viewModel: MatchSetupViewModel,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(variant.label.localized, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (variant.kind) {
                VariantKind.Bool -> {
                    Switch(
                        checked = viewModel.boolVariant(variant.id, defaultBool(variant)),
                        onCheckedChange = { viewModel.setBoolVariant(variant.id, it) },
                    )
                }

                VariantKind.IntegerChoice, VariantKind.Option -> {
                    for (value in variant.values) {
                        VariantChoiceChip(variant, value, viewModel)
                    }
                }

                VariantKind.IntegerRange -> {
                    val current = viewModel.intVariant(variant.id, defaultInt(variant))
                    val min = variant.min ?: Int.MIN_VALUE
                    val max = variant.max ?: Int.MAX_VALUE
                    Chip(title = "−", isSelected = false, onClick = {
                        viewModel.setIntVariant(variant.id, (current - 1).coerceIn(min, max))
                    })
                    Text(current.toString(), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Chip(title = "+", isSelected = false, onClick = {
                        viewModel.setIntVariant(variant.id, (current + 1).coerceIn(min, max))
                    })
                }
            }
        }
    }
}

@Composable
private fun VariantChoiceChip(
    variant: Variant,
    value: VariantValue,
    viewModel: MatchSetupViewModel,
) {
    when (value) {
        is VariantValue.IntValue -> {
            val isSelected = viewModel.intVariant(variant.id, defaultInt(variant)) == value.value
            Chip(title = value.value.toString(), isSelected = isSelected, onClick = {
                viewModel.setIntVariant(variant.id, value.value)
            })
        }

        is VariantValue.StringValue -> {
            val isSelected = viewModel.stringVariantOrNull(variant.id) == value.value
            Chip(
                title = value.value,
                isSelected = isSelected,
                onClick = { viewModel.setStringVariant(variant.id, value.value) },
            )
        }

        is VariantValue.BoolValue -> Unit // pas de variante bool à choix multiple dans le catalogue.
    }
}

private fun defaultBool(variant: Variant): Boolean = (variant.defaultValue as? VariantValue.BoolValue)?.value ?: false

private fun defaultInt(variant: Variant): Int = (variant.defaultValue as? VariantValue.IntValue)?.value ?: 0
