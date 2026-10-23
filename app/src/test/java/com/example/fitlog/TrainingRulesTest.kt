@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog

import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.insight.ExerciseAliases
import com.example.fitlog.domain.SaveNativeRecord
import com.example.fitlog.domain.insight.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class TrainingRulesTest {
    @Test fun incompleteNativeInputIsPersistableButInvalidNumbersAreRejected() = runTest {
        val records = FakeRecords()
        val save = SaveNativeRecord(records)
        val id = UUID.randomUUID().toString()
        save(id, LocalDate.of(2026, 10, 22), listOf(ReviewedSession(listOf(ReviewedExercise("")))), RecordKind.TRAINING)
        assertEquals(1, records.rows.value.size)
        var rejected = false
        try {
            save(id, LocalDate.of(2026, 10, 22), listOf(ReviewedSession(listOf(ReviewedExercise("squat",
                listOf(ReviewedSet(weight = Double.NaN)))))), RecordKind.TRAINING)
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun statisticsExcludeIncompleteSetsAndFutureRecordsButKeepUnknownLoadsInCounts() {
        val today = LocalDate.of(2026, 10, 22)
        fun record(date: LocalDate, name: String, reps: Int?, load: Double?): TrainingRecordDetails {
            val id = UUID.randomUUID().toString()
            return TrainingRecordDetails(TrainingRecord(id, null, null, date.toString(), null, null, null, 1, false, RecordOrigin.NATIVE),
                listOf(ConfirmedSessionRecord(ConfirmedSessionRow("s", id, 0, null, null),
                    listOf(ConfirmedExerciseRecord(ConfirmedExerciseRow("e", "s", 0, name, null, null, null),
                        listOf(ConfirmedSetRow("set", "e", 0, load, WeightUnit.KG, WeightBasis.TOTAL, reps, load, null, null, false)))))))
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
