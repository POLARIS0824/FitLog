package com.example.fitlog.domain.insight

import com.example.fitlog.data.analysis.WeightBasis
import com.example.fitlog.data.analysis.WeightUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** A real recorded set. Loads are converted to kg, never doubled or combined with body weight. */
data class InsightPerformance(
    val date: LocalDate,
    val recordId: String,
    val basis: WeightBasis,
    val loadKg: Double?,
    val reps: Int,
)

enum class InsightChange { MORE_REPS, MORE_LOAD, LESS_ASSISTANCE, SAME, CHANGED }
data class InsightComparison(
    val latest: InsightPerformance,
    val previous: InsightPerformance?,
    val change: InsightChange?,
) {
    val improved get() = change in setOf(InsightChange.MORE_REPS, InsightChange.MORE_LOAD, InsightChange.LESS_ASSISTANCE)
}

enum class InsightRecordKind { LOAD, REPS, ASSISTANCE }
data class InsightAchievement(
    val nameKey: String,
    val name: String,
    val performance: InsightPerformance,
    val previous: InsightPerformance,
    val kind: InsightRecordKind,
)

data class InsightExerciseProgress(
    val exercise: InsightExercise,
    val comparisons: List<InsightComparison>,
    val achievements: List<InsightAchievement>,
)

data class InsightWeek(val start: LocalDate, val end: LocalDate, val days: Int, val sets: Int,
    val partial: Boolean)
data class InsightOverview(
    val totals: InsightTotals,
    val exercises: List<InsightExercise>,
    val progress: List<InsightExerciseProgress>,
    val achievements: List<InsightAchievement>,
    val weeks: List<InsightWeek>,
) {
    val improvedExercises get() = progress.count { item -> item.comparisons.any { it.improved } }
}

private const val LOAD_TOLERANCE_KG = 0.000001
private fun sameLoad(a: InsightPerformance, b: InsightPerformance) =
    a.basis == b.basis && (a.basis == WeightBasis.BODYWEIGHT ||
        (a.loadKg != null && b.loadKg != null && abs(a.loadKg - b.loadKg) < LOAD_TOLERANCE_KG))

private fun InsightExercise.performances(): List<InsightPerformance> = occurrences.flatMap { occurrence ->
    occurrence.exercise.sets.mapNotNull { set ->
        val basis = set.basis?.takeUnless { it == WeightBasis.UNKNOWN } ?: return@mapNotNull null
        val reps = set.reps?.takeIf { it > 0 } ?: return@mapNotNull null
        val load = if (basis == WeightBasis.BODYWEIGHT) null else {
            val value = when (set.unit) {
                WeightUnit.KG -> set.weight
                WeightUnit.LB -> set.weight?.times(0.45359237)
                else -> null
            }
            value?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
        }
        InsightPerformance(occurrence.diary.date, occurrence.diary.key, basis, load, reps)
    }
}.sortedWith(compareBy<InsightPerformance> { it.date }.thenBy { it.recordId })

/** Pick an actual set, rather than pairing one set's heaviest load with another set's reps. */
private fun bestPerformance(sets: List<InsightPerformance>): InsightPerformance = sets.maxWith(
    compareBy<InsightPerformance> {
        if (it.basis == WeightBasis.ASSISTED) -(it.loadKg ?: 0.0) else it.loadKg ?: 0.0
    }.thenBy { it.reps },
)

private fun comparePerformance(latest: InsightPerformance, history: List<InsightPerformance>): InsightComparison {
    // A same-load comparison makes rep progress visible even when the heaviest load stays flat.
    val same = history.filter { sameLoad(it, latest) }
    val candidates = same.ifEmpty { history }
    val previousDate = candidates.maxOfOrNull { it.date }
    val previous = previousDate?.let { date -> bestPerformance(candidates.filter { it.date == date }) }
        ?: return InsightComparison(latest, null, null)
    val change = when {
        sameLoad(latest, previous) && latest.reps > previous.reps -> InsightChange.MORE_REPS
        sameLoad(latest, previous) && latest.reps == previous.reps -> InsightChange.SAME
        !sameLoad(latest, previous) && latest.reps >= previous.reps && latest.basis == WeightBasis.ASSISTED &&
            requireNotNull(latest.loadKg) < requireNotNull(previous.loadKg) -> InsightChange.LESS_ASSISTANCE
        !sameLoad(latest, previous) && latest.reps >= previous.reps && latest.basis != WeightBasis.ASSISTED &&
            requireNotNull(latest.loadKg) > requireNotNull(previous.loadKg) -> InsightChange.MORE_LOAD
        else -> InsightChange.CHANGED
    }
    return InsightComparison(latest, previous, change)
}

internal fun InsightExercise.progress(range: InsightRange, today: LocalDate): InsightExerciseProgress {
    val comparisons = mutableListOf<InsightComparison>()
    val achievements = mutableListOf<InsightAchievement>()
    performances().groupBy { it.basis }.forEach { (basis, sets) ->
        val latestDate = sets.last().date
        if (range.includes(latestDate, today)) {
            val latestSets = sets.filter { it.date == latestDate }
            val history = sets.filter { it.date < latestDate }
            val topSet = comparePerformance(bestPerformance(latestSets), history)
            // A flat heavy single must not hide rep progress in another recorded set that day.
            comparisons += if (topSet.improved) topSet else latestSets.map { comparePerformance(it, history) }
                .filter { it.improved }.maxByOrNull { it.latest.reps - (it.previous?.reps ?: 0) } ?: topSet
        }
        val history = mutableListOf<InsightPerformance>()
        // Same-day sets cannot establish each other's baseline. The first day establishes a baseline only.
        sets.groupBy { it.date }.forEach { (date, dailySets) ->
            if (history.isNotEmpty() && range.includes(date, today)) {
                val best = bestPerformance(dailySets)
                val priorBest = bestPerformance(history)
                val loadRecord = when {
                    basis == WeightBasis.ASSISTED && requireNotNull(best.loadKg) < requireNotNull(priorBest.loadKg) - LOAD_TOLERANCE_KG ->
                        InsightAchievement(nameKey, name, best, priorBest, InsightRecordKind.ASSISTANCE)
                    basis != WeightBasis.BODYWEIGHT && basis != WeightBasis.ASSISTED &&
                        requireNotNull(best.loadKg) > requireNotNull(priorBest.loadKg) + LOAD_TOLERANCE_KG ->
                        InsightAchievement(nameKey, name, best, priorBest, InsightRecordKind.LOAD)
                    else -> null
                }
                val repRecord = if (loadRecord != null) null else dailySets.mapNotNull { set ->
                    val previous = history.filter { sameLoad(it, set) }.maxByOrNull { it.reps }
                    previous?.takeIf { set.reps > it.reps }?.let {
                        InsightAchievement(nameKey, name, set, it, InsightRecordKind.REPS)
                    }
                }.maxByOrNull { it.performance.reps - it.previous.reps }
                (loadRecord ?: repRecord)?.let(achievements::add)
            }
            history += dailySets
        }
    }
    return InsightExerciseProgress(this, comparisons.sortedByDescending { it.latest.date }, achievements)
}

fun InsightData.overview(range: InsightRange): InsightOverview {
    val exercises = exercises(range)
    val progress = exercises(InsightRange.ALL).map { it.progress(range, today) }
    val rangeStart = range.days?.let { today.minusDays(it - 1) }
        ?: diaries.minOfOrNull { it.date } ?: today
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // Keep the overview readable; full history and the yearly activity map remain available below.
    val firstMonday = maxOf(rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), thisMonday.minusWeeks(5))
    val records = diaries(range)
    val weeks = generateSequence(firstMonday) { it.plusWeeks(1) }.takeWhile { it <= thisMonday }.map { monday ->
        val start = maxOf(monday, rangeStart)
        val end = minOf(monday.plusDays(6), today)
        val weekRecords = records.filter { it.date in start..end && it.sessions.isNotEmpty() }
        InsightWeek(start, end, weekRecords.map { it.date }.distinct().size,
            weekRecords.sumOf { it.setCount }, start != monday || end != monday.plusDays(6))
    }.toList()
    return InsightOverview(totals(range), exercises, progress,
        progress.flatMap { it.achievements }.sortedWith(compareByDescending<InsightAchievement> { it.performance.date }
            .thenBy { it.name }).distinctBy { it.nameKey to it.performance.basis }, weeks)
}

/** Alias changes retain the user's choice through any member name. Null means automatic selection. */
fun InsightOverview.focused(keys: Set<String>?): List<InsightExerciseProgress> = if (keys == null) {
    progress.filter { it.comparisons.isNotEmpty() }.sortedWith(
        compareByDescending<InsightExerciseProgress> { item ->
            exercises.firstOrNull { it.nameKey == item.exercise.nameKey }?.occurrences?.map { it.sessionId }?.distinct()?.size ?: 0
        }.thenByDescending { it.comparisons.first().latest.date }.thenBy { it.exercise.name },
    ).take(3)
} else progress.filter { item -> item.exercise.memberKeys.any { it in keys } }
    .sortedBy { it.exercise.name }
