package com.quimene.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dessine [symbol] centré sur [center] — miroir des `BasicChartSymbolShape` de Swift Charts, pour
 * les courbes tracées à la main (`Canvas`) : la couleur du joueur y est doublée de son symbole
 * (charte §1.5), lisible sans la couleur.
 */
fun DrawScope.drawChartSymbol(
    symbol: ChartSymbol,
    center: Offset,
    radius: Float,
    color: Color,
) {
    val stroke = radius * 0.6f
    when (symbol) {
        ChartSymbol.Circle -> drawCircle(color, radius, center)
        ChartSymbol.Square ->
            drawRect(color, topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2))
        ChartSymbol.Triangle -> drawPath(polygon(center, radius, sides = 3), color)
        ChartSymbol.Diamond -> drawPath(polygon(center, radius, sides = 4), color)
        ChartSymbol.Pentagon -> drawPath(polygon(center, radius, sides = 5), color)
        ChartSymbol.Plus -> spokes(center, radius, color, stroke, count = 2, startAngle = 0.0)
        ChartSymbol.Cross -> spokes(center, radius, color, stroke, count = 2, startAngle = PI / 4)
        ChartSymbol.Asterisk -> spokes(center, radius, color, stroke, count = 3, startAngle = PI / 2)
    }
}

/** Le symbole seul, à la taille de [modifier] — pastille de légende. */
@Composable
fun ChartSymbolMarker(
    symbol: ChartSymbol,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawChartSymbol(symbol, center, size.minDimension / 2, color)
    }
}

/** Polygone régulier pointe en haut (triangle, losange, pentagone). */
private fun polygon(
    center: Offset,
    radius: Float,
    sides: Int,
): Path =
    Path().apply {
        for (index in 0 until sides) {
            val angle = -PI / 2 + 2 * PI * index / sides
            val point = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }

/** [count] traits passant par le centre, régulièrement espacés (plus, croix, astérisque). */
private fun DrawScope.spokes(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Float,
    count: Int,
    startAngle: Double,
) {
    for (index in 0 until count) {
        val angle = startAngle + PI * index / count
        val delta = Offset(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
        drawLine(color, center - delta, center + delta, strokeWidth = stroke)
    }
}
