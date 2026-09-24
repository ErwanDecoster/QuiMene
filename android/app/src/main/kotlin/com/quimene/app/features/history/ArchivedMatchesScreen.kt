package com.quimene.app.features.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quimene.app.R
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.di.rememberViewModel
import com.quimene.app.navigation.floatingNavBarContentPadding
import com.quimene.app.ui.gameIcon
import com.quimene.designsystem.components.Card
import com.quimene.designsystem.components.CardGutter
import com.quimene.designsystem.components.EmptyState
import com.quimene.designsystem.components.TertiaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.quimene.store.MatchEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Miroir de `ArchivedMatchesView.swift` (doc 06) — parties archivées, chacune réactivable
 * (« Réactiver ») ou supprimable définitivement (icône, avec confirmation — pas de balayage,
 * aucune convention de ce type ailleurs dans cette app Android). */
@Composable
fun ArchivedMatchesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { ArchivedMatchesViewModel(container.catalog, container.matchRepository) }
    var matchPendingDeletion by remember { mutableStateOf<MatchEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parties_archivees)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (viewModel.matches.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Archive,
                message = stringResource(R.string.aucune_partie_archivee),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = floatingNavBarContentPadding(systemBottomInset = innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(viewModel.matches, key = { it.id }) { match ->
                ArchivedMatchRow(
                    match = match,
                    gameName = viewModel.gameName(match),
                    onUnarchive = { viewModel.unarchive(match) },
                    onDelete = { matchPendingDeletion = match },
                )
            }
        }
    }

    matchPendingDeletion?.let { match ->
        AlertDialog(
            onDismissRequest = { matchPendingDeletion = null },
            title = { Text(stringResource(R.string.supprimer_definitivement_cette_partie)) },
            text = {
                Text(stringResource(R.string.la_partie_et_ses_manches_seront_definitivement_supprimees_y))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        matchPendingDeletion = null
                        viewModel.delete(match)
                    },
                ) { Text(stringResource(R.string.supprimer)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { matchPendingDeletion = null },
                ) { Text(stringResource(R.string.annuler)) }
            },
        )
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

@Composable
private fun ArchivedMatchRow(
    match: MatchEntity,
    gameName: String,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Icon(imageVector = gameIcon(match.gameID), contentDescription = null, tint = colors.brandInk)
            Column(modifier = Modifier.weight(1f)) {
                Text(gameName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    text = dateFormatter.format(match.startedAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            TertiaryButton(text = stringResource(R.string.reactiver), onClick = onUnarchive)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer définitivement")
            }
        }
    }
}
