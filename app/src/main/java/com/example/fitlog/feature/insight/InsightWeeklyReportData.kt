package com.example.fitlog.feature.insight

import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.domain.insight.InsightExercise
import com.example.fitlog.domain.insight.InsightRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal data class InsightReportDay(
    val date: LocalDate,
    val sessions: Int,
    val sets: Int,
    val future: Boolean,
)

internal data class InsightWeeklyExercise(
    val exercise: InsightExercise,
    val sets: Int,
    val days: Int,
    val previousSets: Int,
    val share: Float,
)

internal data class InsightWeeklyReport(
    val start: LocalDate,
    val end: LocalDate,
    val comparisonStart: LocalDate,
    val comparisonEnd: LocalDate,
    val elapsedDays: Int,
    val trainingDays: Int,
    val previousTrainingDays: Int,
    val sessions: Int,
    val sets: Int,
    val days: List<InsightReportDay>,
    val exercises: List<InsightWeeklyExercise>,
    val lessRecorded: List<InsightWeeklyExercise>,
    val balance: List<InsightRank>,
) {
    val frequency get() = trainingDays.toFloat() / elapsedDays
    val frequencyChange get() = (trainingDays - previousTrainingDays).toFloat() / elapsedDays
}

/** Sunday-based calendar weeks; compare the same elapsed weekdays, never a partial week to seven days. */
internal fun prepareWeeklyReport(data: InsightData): InsightWeeklyReport {
    val start = data.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val end = start.plusDays(6)
    val elapsed = (data.today.toEpochDay() - start.toEpochDay()).toInt() + 1
    val comparisonStart = start.minusWeeks(1)
    val comparisonEnd = comparisonStart.plusDays(elapsed - 1L)
    val training = data.diaries.filter { it.date <= data.today && it.sessions.isNotEmpty() }
    val current = training.filter { it.date in start..data.today }
    val previous = training.filter { it.date in comparisonStart..comparisonEnd }
    val sets = current.sumOf { it.setCount }
    val exercises = data.copy(diaries = current).exercises(InsightRange.ALL)
    val previousExercises = data.copy(diaries = previous).exercises(InsightRange.ALL).associateBy { it.nameKey }
    fun reportExercise(exercise: InsightExercise): InsightWeeklyExercise {
        val count = exercise.occurrences.sumOf { it.exercise.sets.size }
        return InsightWeeklyExercise(exercise, count, exercise.occurrences.map { it.diary.date }.distinct().size,
            previousExercises[exercise.nameKey]?.occurrences?.sumOf { it.exercise.sets.size } ?: 0,
            if (sets == 0) 0f else count.toFloat() / sets)
    }
    val ranked = exercises.map(::reportExercise).sortedWith(
        compareByDescending<InsightWeeklyExercise> { it.sets }.thenBy { it.exercise.name })
    val currentKeys = ranked.map { it.exercise.nameKey }.toSet()
    val absent = previousExercises.values.filter { it.nameKey !in currentKeys }.map { exercise ->
        InsightWeeklyExercise(exercise, 0, 0, exercise.occurrences.sumOf { it.exercise.sets.size }, 0f)
    }
    val lessRecorded = (ranked + absent).filter { it.sets < it.previousSets }
        .sortedWith(compareBy<InsightWeeklyExercise> { (it.sets - it.previousSets).toFloat() / it.previousSets }
            .thenBy { it.exercise.name })
    return InsightWeeklyReport(start, end, comparisonStart, comparisonEnd, elapsed,
        current.map { it.date }.distinct().size, previous.map { it.date }.distinct().size,
        current.sumOf { it.sessions.size }, sets,
        (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            val records = current.filter { it.date == date }
            InsightReportDay(date, records.sumOf { it.sessions.size }, records.sumOf { it.setCount }, date > data.today)
        }, ranked, lessRecorded, ranked.map { InsightRank(it.exercise, it.days, it.sets, it.share) })
}
