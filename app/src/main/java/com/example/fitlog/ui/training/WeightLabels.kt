package com.example.fitlog.ui.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import java.text.NumberFormat
import kotlin.math.floor
import kotlin.math.log10

@Composable
internal fun weightNumberLabel(weight: Double?, maximumFractionDigits: Int = 340): String {
    val locale = LocalConfiguration.current.locales[0]
    val digits = maxOf(maximumFractionDigits,
        if (weight != null && weight > 0 && weight < 1) (2 - floor(log10(weight)).toInt()).coerceAtMost(340) else 0)
    val numbers = remember(locale, digits) { NumberFormat.getNumberInstance(locale).apply {
        this.maximumFractionDigits = digits; isGroupingUsed = false
    } }
    return if (weight == null) stringResource(R.string.detail_value_unknown) else numbers.format(weight)
}

@Composable
internal fun weightLabel(weight: Double?, unit: WeightUnit?, maximumFractionDigits: Int = 340, basis: WeightBasis? = null): String {
    return if (basis == WeightBasis.BODYWEIGHT) stringResource(R.string.detail_weight_bodyweight)
    else if (weight == null) stringResource(R.string.detail_weight_unrecorded)
    else stringResource(R.string.detail_weight_short, weightNumberLabel(weight, maximumFractionDigits), weightUnitLabel(unit))
}

@Composable
internal fun weightUnitLabel(unit: WeightUnit?) = stringResource(when (unit) {
    WeightUnit.KG -> R.string.detail_unit_kg_short
    WeightUnit.LB -> R.string.detail_unit_lb_short
    else -> R.string.detail_unit_unknown_short
})
