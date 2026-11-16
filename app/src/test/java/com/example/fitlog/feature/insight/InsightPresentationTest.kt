@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.feature.insight

import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.buildInsightData
import com.example.fitlog.model.record.ConfirmedExercise
import com.example.fitlog.model.record.ConfirmedExerciseRecord
import com.example.fitlog.model.record.ConfirmedSession
import com.example.fitlog.model.record.ConfirmedSessionRecord
import com.example.fitlog.model.record.ConfirmedSet
import com.example.fitlog.model.record.ExerciseAliases
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class InsightPresentationTest {
    @Test fun statisticsExcludeIncompleteSetsAndFutureRecordsButKeepUnknownLoadsInCounts() {
        val today = LocalDate.of(2026, 10, 22)
        fun record(date: LocalDate, name: String, reps: Int?, load: Double?): TrainingRecordDetails {
            val id = UUID.randomUUID().toString()
            return TrainingRecordDetails(TrainingRecord(id, null, null, date.toString(), null, null, null, 1, false, RecordOrigin.NATIVE),
                listOf(ConfirmedSessionRecord(ConfirmedSession("s", id, 0, null, null),
                    listOf(ConfirmedExerciseRecord(ConfirmedExercise("e", "s", 0, name, null, null, null),
                        listOf(ConfirmedSet("set", "e", 0, load, WeightUnit.KG, WeightBasis.TOTAL, reps, load, null, null, false)))))))
        }
        val data = buildInsightData(null, null, listOf(record(today, "squat", 5, null),
            record(today, "", 5, 100.0), record(today, "squat", null, 100.0),
            record(today.plusDays(1), "squat", 5, 100.0)), ExerciseAliases(), today)
        val prepared = prepareInsight(data, InsightRange.NINETY, nameKey = "squat")
        assertEquals(1, prepared.overview.totals.sets)
        assertTrue(prepared.series.isEmpty())
        assertEquals(1, prepared.recent.size)
    }
}
