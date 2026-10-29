package com.example.fitlog.feature.insight

import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.buildInsightData
import com.example.fitlog.model.record.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class InsightWeeklyReportTest {
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun record(date: LocalDate, name: String = "Squat", sets: Int = 1, reps: Int = 5): TrainingRecordDetails {
        val id = UUID.randomUUID().toString()
        return TrainingRecordDetails(TrainingRecord(id, null, null, date.toString(), null, null, null,
            1, false, RecordOrigin.NATIVE),
            listOf(ConfirmedSessionRecord(ConfirmedSession("$id-session", id, 0, null, null),
                listOf(ConfirmedExerciseRecord(ConfirmedExercise("$id-exercise", "$id-session", 0, name, null, null, null),
                    (0 until sets).map { set -> ConfirmedSet("$id-set-$set", "$id-exercise", set,
                        null, null, WeightBasis.UNKNOWN, reps, null, null, null, false) })))))
    }

    private fun report(today: LocalDate, records: List<TrainingRecordDetails>, aliases: ExerciseAliases = ExerciseAliases()) =
        prepareWeeklyReport(buildInsightData(null, null, records, aliases, today))

    @Test fun partialWeekComparesOnlyTheSameElapsedWeekdays() {
        val result = report(thursday, listOf(
            record(LocalDate.of(2026, 10, 25)), record(LocalDate.of(2026, 10, 26)),
            record(thursday), record(thursday),
            record(LocalDate.of(2026, 10, 18)), record(LocalDate.of(2026, 10, 19)),
            record(LocalDate.of(2026, 10, 23), sets = 10)))
        assertEquals(LocalDate.of(2026, 10, 25), result.start)
        assertEquals(LocalDate.of(2026, 10, 31), result.end)
        assertEquals(LocalDate.of(2026, 10, 22), result.comparisonEnd)
        assertEquals(5, result.elapsedDays)
        assertEquals(3, result.trainingDays)
        assertEquals(2, result.previousTrainingDays)
        assertEquals(4, result.sessions)
        assertEquals(0.6f, result.frequency, 0.00001f)
        assertEquals(0.2f, result.frequencyChange, 0.00001f)
        assertEquals(2, result.days.last { !it.future }.sessions)
        assertEquals(2, result.days.count { it.future })
        assertEquals(2, result.exercises.single().previousSets)
    }

    @Test fun sundayBeginsANewWeekWithANonzeroDenominator() {
        val sunday = LocalDate.of(2026, 11, 1)
        val result = report(sunday, listOf(record(sunday), record(sunday.minusDays(6))))
        assertEquals(sunday, result.start)
        assertEquals(1, result.elapsedDays)
        assertEquals(1f, result.frequency, 0f)
        assertEquals(0, result.previousTrainingDays)
        assertEquals(6, result.days.count { it.future })
    }

    @Test fun weekSpanningNewYearKeepsAllSevenDatesInOrder() {
        val today = LocalDate.of(2026, 1, 1)
        val result = report(today, emptyList())
        assertEquals(LocalDate.of(2025, 12, 28), result.start)
        assertEquals(LocalDate.of(2026, 1, 3), result.end)
        assertEquals(LocalDate.of(2025, 12, 25), result.comparisonEnd)
        assertEquals(7, result.days.map { it.date }.distinct().size)
        assertEquals(5, result.elapsedDays)
    }

    @Test fun invalidAndFutureSetsAreExcludedWhileUnknownLoadsStillCount() {
        val result = report(thursday, listOf(record(thursday), record(thursday, sets = 2),
            record(thursday, name = ""), record(thursday, reps = 0), record(thursday.plusDays(1))))
        assertEquals(1, result.trainingDays)
        assertEquals(2, result.sessions)
        assertEquals(3, result.sets)
        assertEquals(1f, result.exercises.single().share, 0f)
        assertTrue(result.days.filter { it.future }.all { it.sessions == 0 && it.sets == 0 })
    }

    @Test fun aliasesCombineAcrossWeeksAndAnAbsentExerciseIsKeptInTheComparison() {
        val aliases = ExerciseAliases(groups = listOf(ExerciseAliasGroup("Bench press", listOf("bench press", "bb bench"))))
        val result = report(thursday, listOf(
            record(thursday, "Bench press", sets = 2), record(thursday.minusDays(1), "bb bench"),
            record(thursday.minusDays(8), "bb bench", sets = 4),
            record(thursday.minusDays(8), "Row", sets = 2)), aliases)
        assertEquals(1, result.exercises.size)
        assertEquals(3, result.exercises.single().sets)
        assertEquals(4, result.exercises.single().previousSets)
        assertEquals("bench press", result.exercises.single().exercise.nameKey)
        assertEquals(2, result.lessRecorded.size)
        val absent = result.lessRecorded.first { it.exercise.name == "Row" }
        assertEquals(0, absent.sets)
        assertEquals(2, absent.previousSets)
        assertTrue(result.balance.all { it.sets > 0 })
    }

    @Test fun completeWeekAndEmptyWeekHaveFiniteFrequencies() {
        val saturday = LocalDate.of(2026, 10, 31)
        val full = report(saturday, (0L..13L).map { record(saturday.minusDays(it)) })
        assertEquals(7, full.elapsedDays)
        assertEquals(7, full.trainingDays)
        assertEquals(7, full.previousTrainingDays)
        assertEquals(1f, full.frequency, 0f)
        assertEquals(0f, full.frequencyChange, 0f)
        assertTrue(full.lessRecorded.isEmpty())
        val empty = report(thursday, emptyList())
        assertEquals(0f, empty.frequency, 0f)
        assertEquals(0f, empty.frequencyChange, 0f)
        assertTrue(empty.balance.isEmpty())
    }

    @Test fun weeklyReportIsIndependentOfTheOverviewRange() {
        val data = buildInsightData(null, null, (0L..60L).map { record(thursday.minusDays(it)) }, ExerciseAliases(), thursday)
        assertEquals(prepareInsight(data, InsightRange.THIRTY).weekly, prepareInsight(data, InsightRange.ALL).weekly)
    }
}
