package com.example.fitlog.feature.insight

import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.domain.insight.InsightExercise
import com.example.fitlog.domain.insight.InsightOverview
import com.example.fitlog.domain.insight.InsightRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal data class InsightRank(val exercise: InsightExercise, val days: Int, val sets: Int, val share: Float)
internal data class InsightActivityWeek(val start: LocalDate, val sessions: List<Int>) {
    val total get() = sessions.sum()
}
internal data class InsightDashboard(
    val todaySessions: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val busiestDay: DayOfWeek?,
    val busiestSessions: Int,
    val ranks: List<InsightRank>,
    val calendarStart: LocalDate,
    val calendar: Map<LocalDate, Int>,
    val weeks: List<InsightActivityWeek>,
    val matrix: List<InsightActivityWeek>,
)

/** Counts come only from valid training sets. Save timestamps are never treated as workout times. */
internal fun prepareDashboard(data: InsightData, range: InsightRange, overview: InsightOverview): InsightDashboard {
    val training = data.diaries.filter { it.date <= data.today && it.sessions.isNotEmpty() }
    val selected = training.filter { range.includes(it.date, data.today) }
    val dates = selected.map { it.date }.distinct().sorted()
    var run = 0
    var best = 0
    var previous: LocalDate? = null
    dates.forEach { date ->
        run = if (previous?.plusDays(1) == date) run + 1 else 1
        best = maxOf(best, run)
        previous = date
    }
    val active = dates.toHashSet()
    var cursor = if (data.today in active) data.today else data.today.minusDays(1)
    var current = 0
    while (cursor in active) { current++; cursor = cursor.minusDays(1) }
    val weekdays = selected.groupBy { it.date.dayOfWeek }.mapValues { (_, records) -> records.sumOf { it.sessions.size } }
    val busiest = weekdays.entries.sortedWith(compareByDescending<Map.Entry<DayOfWeek, Int>> { it.value }
        .thenBy { it.key.value }).firstOrNull()
    val ranks = overview.exercises.map { exercise ->
        val days = exercise.occurrences.map { it.diary.date }.distinct().size
        InsightRank(exercise, days, exercise.occurrences.sumOf { it.exercise.sets.size },
            if (dates.isEmpty()) 0f else days.toFloat() / dates.size)
    }.sortedWith(compareByDescending<InsightRank> { it.days }.thenByDescending { it.sets }.thenBy { it.exercise.name })
    // This section deliberately keeps a fixed six-month context, independent of the summary's range.
    val calendarStart = data.today.minusMonths(6).plusDays(1)
    val calendar = training.filter { it.date >= calendarStart }.groupBy { it.date }
        .mapValues { (_, records) -> records.sumOf { it.sessions.size } }
    val firstMonday = calendarStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun week(start: LocalDate, counts: Map<LocalDate, Int>) =
        InsightActivityWeek(start, (0L..6L).map { counts[start.plusDays(it)] ?: 0 })
    val weeks = generateSequence(firstMonday) { it.plusWeeks(1) }.takeWhile { it <= data.today }
        .map { week(it, calendar) }.toList()
    val selectedCounts = selected.groupBy { it.date }.mapValues { (_, records) -> records.sumOf { it.sessions.size } }
    val thisMonday = data.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val matrix = (3L downTo 0L).map { week(thisMonday.minusWeeks(it), selectedCounts) }
    return InsightDashboard(training.filter { it.date == data.today }.sumOf { it.sessions.size }, current, best,
        busiest?.key, busiest?.value ?: 0, ranks, calendarStart, calendar, weeks, matrix)
}
