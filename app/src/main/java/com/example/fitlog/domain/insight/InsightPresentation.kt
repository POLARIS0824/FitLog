package com.example.fitlog.domain.insight

import java.time.LocalDate

/** Calculated together off the UI thread so composables only render prepared values. */
internal data class InsightPresentation(
    val data: InsightData,
    val overview: InsightOverview,
    val focused: List<InsightExerciseProgress>,
    val focusOptions: List<InsightExercise>,
    val exercises: List<InsightExercise>,
    val recent: List<InsightDiary>,
    val activity: Map<LocalDate, List<InsightDiary>>,
    val exercise: InsightExercise?,
    val progress: InsightExerciseProgress?,
    val series: List<InsightSeries>,
    val recordTitles: Map<String, String>,
)

internal fun prepareInsight(data: InsightData, range: InsightRange, focusedKeys: Set<String>? = null,
    nameKey: String? = null): InsightPresentation {
    val overview = data.overview(range)
    val exercises = overview.exercises
    val canonical = nameKey?.let { data.aliases.groupFor(it)?.primaryKey ?: it }
    val exercise = exercises.firstOrNull { it.nameKey == canonical }
    return InsightPresentation(data, overview, overview.focused(focusedKeys),
        overview.progress.map { it.exercise }.sortedBy { it.name }, exercises,
        data.diaries(range).filter { it.sessions.isNotEmpty() }.take(3),
        data.activity().toSortedMap(compareByDescending { it }), exercise,
        overview.progress.firstOrNull { it.exercise.nameKey == canonical },
        exercise?.let(::exerciseSeries).orEmpty(), data.diaries.associate { it.key to it.title })
}

internal sealed interface InsightRecordDestination {
    data class Native(val id: String) : InsightRecordDestination
    data class Markdown(val vaultUri: String, val vaultId: String, val document: String,
        val path: String, val directory: String, val name: String) : InsightRecordDestination
}
