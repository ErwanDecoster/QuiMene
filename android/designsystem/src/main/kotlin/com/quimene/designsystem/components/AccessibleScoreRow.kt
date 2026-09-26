package com.quimene.designsystem.components

import android.content.res.Resources
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import com.quimene.designsystem.R

/**
 * Miroir de `accessibleScoreRow` (`AccessibleScoreRow.swift`) : un seul arrêt TalkBack par ligne
 * de tableau de scores, libellé = nom + rang (« Alice, deuxième »), valeur = score + écart de la
 * manche (« 44 points, +12 cette manche ») — même découpage libellé/valeur que côté Swift
 * (`accessibilityLabel` + `accessibilityValue`), pas un texte unique concaténé.
 *
 * Les rangs sont écrits en toutes lettres par langue (`ds_ordinals`) : `NumberFormatter(.ordinal)`
 * n'a pas d'équivalent public sur Android (`android.icu.text.RuleBasedNumberFormat` est absent du
 * SDK). Un `Modifier.Node` plutôt qu'une fabrique `@Composable` : il lit les ressources traduites
 * via le `Context` courant (`currentValueOf(LocalContext)`).
 */
fun Modifier.accessibleScoreRow(
    name: String,
    score: Int,
    rank: Int? = null,
    delta: Int? = null,
): Modifier = this then AccessibleScoreRowElement(name, score, rank, delta)

private data class AccessibleScoreRowElement(
    val name: String,
    val score: Int,
    val rank: Int?,
    val delta: Int?,
) : ModifierNodeElement<AccessibleScoreRowNode>() {
    override fun create() = AccessibleScoreRowNode(name, score, rank, delta)

    override fun InspectorInfo.inspectableProperties() {
        name = "accessibleScoreRow"
        properties["name"] = this@AccessibleScoreRowElement.name
        properties["score"] = score
        properties["rank"] = rank
        properties["delta"] = delta
    }

    override fun update(node: AccessibleScoreRowNode) {
        node.name = name
        node.score = score
        node.rank = rank
        node.delta = delta
        node.invalidateSemantics()
    }
}

private class AccessibleScoreRowNode(
    var name: String,
    var score: Int,
    var rank: Int?,
    var delta: Int?,
) : Modifier.Node(),
    SemanticsModifierNode,
    CompositionLocalConsumerModifierNode {
    override val shouldMergeDescendantSemantics: Boolean get() = true

    override fun SemanticsPropertyReceiver.applySemantics() {
        val resources = currentValueOf(LocalContext).resources
        contentDescription = scoreRowLabel(resources, name, rank)
        stateDescription = scoreRowValue(resources, score, delta)
    }
}

private fun scoreRowLabel(
    resources: Resources,
    name: String,
    rank: Int?,
): String {
    if (rank == null) return name
    val ordinal =
        resources.getStringArray(R.array.ds_ordinals).getOrNull(rank - 1)
            ?: resources.getString(R.string.ds_ordinal_fallback, rank)
    return "$name, $ordinal"
}

private fun scoreRowValue(
    resources: Resources,
    score: Int,
    delta: Int?,
): String {
    val points = resources.getQuantityString(R.plurals.ds_score_points, score, score)
    if (delta == null) return points
    val signed = if (delta >= 0) "+$delta" else "$delta"
    return "$points, ${resources.getString(R.string.ds_score_delta, signed)}"
}
