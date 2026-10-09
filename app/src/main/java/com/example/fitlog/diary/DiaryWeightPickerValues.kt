package com.example.fitlog.diary

import com.example.fitlog.data.analysis.WeightUnit
import com.example.fitlog.data.analysis.WeightBasis
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

/** Weight and unit are one edit; a missing unit never implies kilograms. */
internal data class DiaryWeightValue(val weight: Double?, val unit: WeightUnit?, val converted: Boolean = false,
    val basis: WeightBasis? = null) {
    fun inUnit(target: WeightUnit?): DiaryWeightValue {
        val value = weight ?: return copy(unit = target)
        val converted = when {
            unit == WeightUnit.KG && target == WeightUnit.LB ->
                BigDecimal.valueOf(value).divide(KILOGRAMS_PER_POUND, MathContext.DECIMAL128).toDouble()
            unit == WeightUnit.LB && target == WeightUnit.KG ->
                BigDecimal.valueOf(value).multiply(KILOGRAMS_PER_POUND).toDouble()
            else -> value
        }
        return DiaryWeightValue(converted, target,
            unit != target && unit in listOf(WeightUnit.KG, WeightUnit.LB) && target in listOf(WeightUnit.KG, WeightUnit.LB), basis)
    }

    /** Only arithmetic roundoff is restored; manually entered precision is never rounded. */
    fun restoringConversionRoundoff(original: DiaryWeightValue?): DiaryWeightValue {
        if (!converted || original == null || unit != original.unit || basis != original.basis || weight == null || original.weight == null) return this
        return if (abs(weight - original.weight) <= Math.ulp(original.weight))
            copy(weight = original.weight, converted = false) else this
    }
}

internal enum class DiaryWeightType { UNRECORDED, BODYWEIGHT, NUMERIC }

internal fun weightType(weight: Double?, basis: WeightBasis?) = when {
    basis == WeightBasis.BODYWEIGHT -> DiaryWeightType.BODYWEIGHT
    weight == null -> DiaryWeightType.UNRECORDED
    else -> DiaryWeightType.NUMERIC
}

internal fun numericWeightBasis(basis: WeightBasis?) =
    if (basis == WeightBasis.BODYWEIGHT) WeightBasis.UNKNOWN else basis

internal fun reviewWeightUnit(weight: Double?, unit: WeightUnit?) =
    if (weight != null && unit != WeightUnit.KG && unit != WeightUnit.LB) WeightUnit.KG else unit

private val KILOGRAMS_PER_POUND = BigDecimal("0.45359237")

internal fun defaultWeightStep(unit: WeightUnit?) = if (unit == WeightUnit.LB) 5.0 else 2.5

// These are shortcuts, not limits. Keep exact values and a bounded window around the current load.
internal fun weightOptions(current: Double?, step: Double = 2.5): List<Double?> {
    require(step.isFinite() && step > 0)
    val increment = BigDecimal.valueOf(step)
    val first = BigDecimal.valueOf(current ?: 0.0).divideToIntegralValue(increment)
        .subtract(BigDecimal(100)).max(BigDecimal.ZERO)
    val values = (0..200).map { first.add(BigDecimal(it)).multiply(increment).toDouble() }
        .filter { it.isFinite() }
    return listOf(null) + (values + 0.0 + listOfNotNull(current)).distinct().sorted()
}

internal fun enteredWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

internal fun enteredWeightStep(text: String): Double? = enteredWeight(text)?.takeIf { it > 0 }
