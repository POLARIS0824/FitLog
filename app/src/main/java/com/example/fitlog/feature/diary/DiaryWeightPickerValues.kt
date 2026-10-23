package com.example.fitlog.feature.diary

import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.ui.training.enteredWeight
import java.math.BigDecimal

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


internal fun enteredWeightStep(text: String): Double? = enteredWeight(text)?.takeIf { it > 0 }
