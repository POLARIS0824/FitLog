package com.example.fitlog.ui.training

import com.example.fitlog.R
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

/** Weight and unit are one edit; a missing unit never implies kilograms. */
internal data class WeightEditValue(val weight: Double?, val unit: WeightUnit?, val converted: Boolean = false,
    val basis: WeightBasis? = null) {
    fun inUnit(target: WeightUnit?): WeightEditValue {
        val value = weight ?: return copy(unit = target)
        val converted = when {
            unit == WeightUnit.KG && target == WeightUnit.LB ->
                BigDecimal.valueOf(value).divide(KILOGRAMS_PER_POUND, MathContext.DECIMAL128).toDouble()
            unit == WeightUnit.LB && target == WeightUnit.KG ->
                BigDecimal.valueOf(value).multiply(KILOGRAMS_PER_POUND).toDouble()
            else -> value
        }
        return WeightEditValue(converted, target,
            unit != target && unit in listOf(WeightUnit.KG, WeightUnit.LB) && target in listOf(WeightUnit.KG, WeightUnit.LB), basis)
    }

    /** Only arithmetic roundoff is restored; manually entered precision is never rounded. */
    fun restoringConversionRoundoff(original: WeightEditValue?): WeightEditValue {
        if (!converted || original == null || unit != original.unit || basis != original.basis || weight == null || original.weight == null) return this
        return if (abs(weight - original.weight) <= Math.ulp(original.weight))
            copy(weight = original.weight, converted = false) else this
    }
}

private val KILOGRAMS_PER_POUND = BigDecimal("0.45359237")

internal fun enteredWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

internal fun enteredReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

internal fun weightBasisLabel(basis: WeightBasis?) = when (basis) {
    WeightBasis.PER_SIDE -> R.string.detail_basis_per_side
    WeightBasis.TOTAL -> R.string.detail_basis_total
    WeightBasis.BODYWEIGHT -> R.string.detail_basis_bodyweight
    WeightBasis.ADDED -> R.string.detail_basis_added
    WeightBasis.ASSISTED -> R.string.detail_basis_assisted
    WeightBasis.UNKNOWN -> R.string.detail_basis_unknown_short
    null -> R.string.detail_basis_unknown_short
}
