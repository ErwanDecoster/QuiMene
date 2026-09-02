package com.cacompte.app.features.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.di.rememberViewModel
import com.cacompte.designsystem.components.Card
import com.cacompte.designsystem.components.CardGutter
import com.cacompte.designsystem.components.EmptyState
import com.cacompte.designsystem.tokens.LocalAppColors
import com.cacompte.designsystem.tokens.Space
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Miroir de `HistoryView.swift` (doc 06) — liste des parties terminées, tap pour rouvrir le
 * classement final. */
@Composable
fun HistoryListScreen(onOpenMatch: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { HistoryViewModel(container.catalog, container.matchRepository) }
    val rows by viewModel.rows.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Historique") }) }) { innerPadding ->
        val currentRows = rows
        if (currentRows == null) return@Scaffold
        if (currentRows.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                message = "Aucune partie terminée pour l'instant.",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(CardGutter),
        ) {
            items(currentRows, key = { it.match.id }) { row ->
                HistoryRow(row, onClick = { onOpenMatch(row.match.id.toString()) })
            }
        }
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

@Composable
private fun HistoryRow(
    row: HistoryViewModel.Row,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Text(row.gameName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = dateFormatter.format(row.match.startedAt.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
