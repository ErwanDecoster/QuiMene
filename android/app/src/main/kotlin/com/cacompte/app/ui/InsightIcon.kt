package com.cacompte.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/** `Insight.symbol` (`:domain`) porte le nom SF Symbol utilisé côté Apple — pas de registre de
 * symboles équivalent sur Android, correspondance posée à la main pour les 9 valeurs réellement
 * produites par `StatsEngine.insights` (voir `StatsEngine.kt`, pas les 11 `InsightID` déclarées :
 * `remontada`/`collapse` n'en produisent aucune, fidèle à la source Swift). */
fun insightIcon(symbol: String): ImageVector =
    when (symbol) {
        "flame.fill" -> Icons.Filled.LocalFireDepartment
        "star.fill" -> Icons.Filled.Star
        "metronome" -> Icons.Filled.Timer
        "chart.line.uptrend.xyaxis" -> Icons.AutoMirrored.Filled.TrendingUp
        "arrow.left.and.right" -> Icons.Filled.SwapHoriz
        "arrow.left.arrow.right" -> Icons.AutoMirrored.Filled.CompareArrows
        "crown.fill" -> Icons.Filled.EmojiEvents
        "lock.fill" -> Icons.Filled.Lock
        "multiply.circle.fill" -> Icons.Filled.HighlightOff
        else -> Icons.Filled.Info
    }
