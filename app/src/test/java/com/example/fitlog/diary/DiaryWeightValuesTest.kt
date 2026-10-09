package com.example.fitlog.diary

import com.example.fitlog.data.analysis.WeightUnit
import org.junit.Assert.*
import org.junit.Test

class DiaryWeightValuesTest {
    @Test fun equivalentMassConversionKeepsFullPrecisionAndDoesNotSnapToPickerIntervals() {
        val pounds = DiaryWeightValue(100.0, WeightUnit.LB).inUnit(WeightUnit.KG)
        assertEquals(45.359237, pounds.weight!!, 1e-12)
        assertEquals(WeightUnit.KG, pounds.unit)
        val kilograms = DiaryWeightValue(60.0, WeightUnit.KG)
        val converted = kilograms.inUnit(WeightUnit.LB)
        assertEquals(132.27735731092655, converted.weight!!, 1e-12)
        assertTrue(weightOptions(converted.weight, defaultWeightStep(WeightUnit.LB)).contains(converted.weight))
        assertEquals(kilograms, kilograms.inUnit(WeightUnit.KG))
    }

    @Test fun assigningAnUnknownUnitRetainsTheRecordedNumberAndMissingWeightStaysMissing() {
        for (unit in listOf(null, WeightUnit.UNKNOWN)) {
            assertEquals(DiaryWeightValue(60.0, WeightUnit.LB), DiaryWeightValue(60.0, unit).inUnit(WeightUnit.LB))
            assertEquals(DiaryWeightValue(null, WeightUnit.KG), DiaryWeightValue(null, unit).inUnit(WeightUnit.KG))
        }
        assertEquals(0.0, DiaryWeightValue(0.0, WeightUnit.KG).inUnit(WeightUnit.LB).weight!!, 0.0)
    }

    @Test fun unitSpecificShortcutsAndCustomDecimalsRetainExactOffGridWeights() {
        val kg = weightOptions(62.3, defaultWeightStep(WeightUnit.KG))
        val lb = weightOptions(62.3, defaultWeightStep(WeightUnit.LB))
        assertTrue(kg.contains(62.5)); assertFalse(lb.contains(62.5))
        assertTrue(lb.contains(65.0)); assertTrue(kg.contains(62.3)); assertTrue(lb.contains(62.3))
        assertTrue(weightOptions(62.3, 0.1).contains(62.2))
        assertTrue(weightOptions(0.3, 0.1).contains(0.3))
        assertNull(weightOptions(null, 5.0).first())
    }

    @Test fun tinyIntervalsAndVeryLargeValuesCannotCreateAnUnboundedOptionList() {
        for ((weight, step) in listOf(1100.1 to 2.5, 60.0 to 1e-100, Double.MAX_VALUE to 10.0)) {
            val options = weightOptions(weight, step)
            assertTrue(options.size <= 204)
            assertTrue(options.contains(weight))
            assertTrue(options.filterNotNull().all { it.isFinite() && it >= 0 })
        }
    }

    @Test fun customIntervalValidationKeepsZeroDistinctFromAValidWeight() {
        assertEquals(0.0, enteredWeight("0")!!, 0.0)
        for (text in listOf("", "0", "-1", "NaN", "Infinity", "1e999")) assertNull(enteredWeightStep(text))
        assertEquals(1.25, enteredWeightStep("1,25")!!, 0.0)
        assertEquals(0.1, enteredWeightStep("0.1")!!, 0.0)
    }
}
