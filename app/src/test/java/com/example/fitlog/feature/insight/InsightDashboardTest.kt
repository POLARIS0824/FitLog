package com.example.fitlog.feature.insight

import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.buildInsightData
import com.example.fitlog.model.record.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class InsightDashboardTest {
    private val today = LocalDate.of(2026, 10, 29)

    private fun record(daysAgo: Long, name: String = "Squat", reps: Int = 5): TrainingRecordDetails {
        val id = UUID.randomUUID().toString()
        return TrainingRecordDetails(
            TrainingRecord(id, null, null, today.minusDays(daysAgo).toString(), null, null, null,
                1, false, RecordOrigin.NATIVE),
            listOf(ConfirmedSessionRecord(ConfirmedSession("$id-session", id, 0, null, null),
                listOf(ConfirmedExerciseRecord(ConfirmedExercise("$id-exercise", "$id-session", 0, name, null, null, null),
                    listOf(ConfirmedSet("$id-set", "$id-exercise", 0, null, null, WeightBasis.UNKNOWN,
                        reps, null, null, null, false)))))))
    }

    private fun dashboard(records: List<TrainingRecordDetails>, range: InsightRange = InsightRange.THIRTY) =
        prepareInsight(buildInsightData(null, null, records, ExerciseAliases(), today), range).dashboard

    @Test fun streakUsesDistinctTrainingDatesAndAllowsTodayToRemainUnrecorded() {
        val result = dashboard(listOf(record(5), record(4), record(3), record(1), record(1),
            record(0, reps = 0), record(-1)))
        assertEquals(1, result.currentStreak)
        assertEquals(3, result.bestStreak)
        assertEquals(0, result.todaySessions)
        assertEquals(2, result.calendar[today.minusDays(1)])
        assertFalse(result.calendar.containsKey(today.plusDays(1)))
    }

    @Test fun selectedRangeClipsStreaksAndRankingWithoutClippingSixMonthContext() {
        val result = dashboard((0L..35L).map { record(it) })
        assertEquals(30, result.currentStreak)
        assertEquals(30, result.bestStreak)
        assertEquals(30, result.ranks.single().days)
        assertEquals(36, result.calendar.size)
        assertEquals(1f, result.ranks.single().share, 0f)
    }

    @Test fun rankingCountsDaysOnceAndMatrixUsesWorkoutDatesRatherThanSaveTimestamps() {
        val result = dashboard(listOf(record(0), record(0), record(1, "Row"), record(35)))
        assertEquals(2, result.todaySessions)
        assertEquals(1, result.ranks.first { it.exercise.name == "Squat" }.days)
        assertEquals(0.5f, result.ranks.first { it.exercise.name == "Squat" }.share, 0f)
        assertEquals(3, result.matrix.sumOf { it.total })
        assertEquals(2, result.matrix.last().sessions[today.dayOfWeek.value - 1])
    }

    @Test fun incompleteAndEmptyRecordsProduceNoActivityOrRatios() {
        val result = dashboard(listOf(record(0, name = ""), record(1, reps = 0)))
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.bestStreak)
        assertNull(result.busiestDay)
        assertTrue(result.ranks.isEmpty())
        assertTrue(result.calendar.isEmpty())
        assertEquals(0, result.weeks.sumOf { it.total })
        assertEquals(0, result.matrix.sumOf { it.total })
    }
}
