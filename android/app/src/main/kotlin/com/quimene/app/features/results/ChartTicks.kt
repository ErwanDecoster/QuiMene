package com.quimene.app.features.results

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Graduations « rondes » de l'axe des totaux : un pas de 1, 2 ou 5 × 10ⁿ donnant environ
 * [targetCount] intervalles, bornes arrondies vers l'extérieur — comme l'axe automatique de Swift
 * Charts côté Apple, plutôt que des valeurs découpées à l'identique entre les extrêmes (5, 47, 89…).
 * Jamais un pas sous l'unité (les scores sont entiers), toujours au moins deux graduations.
 */
internal fun niceTicks(
    min: Float,
    max: Float,
    targetCount: Int = 4,
): List<Float> {
    val low = minOf(min, max)
    val high = maxOf(min, max)
    val rough = (high - low).takeIf { it > 0f }?.div(targetCount) ?: 1f
    val magnitude = 10f.pow(floor(log10(rough)))
    val nice =
        when (rough / magnitude) {
            in 0f..1.5f -> 1f
            in 1.5f..3f -> 2f
            in 3f..7f -> 5f
            else -> 10f
        }
    // Arrondi : 10 × 0,1 donne 1,0000001 en Float.
    val step = (nice * magnitude).roundToInt().coerceAtLeast(1).toFloat()
    val first = floor(low / step) * step
    val count = ((ceil(high / step) * step - first) / step).roundToInt().coerceAtLeast(1)
    return (0..count).map { first + it * step }
}
