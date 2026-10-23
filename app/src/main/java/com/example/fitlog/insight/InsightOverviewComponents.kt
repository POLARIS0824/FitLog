package com.example.fitlog.insight

import com.example.fitlog.domain.insight.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.WeightBasis

@Composable
internal fun InsightSummary(overview: InsightOverview) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.insight_overview_label), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (overview.improvedExercises > 0) pluralStringResource(R.plurals.insight_improved_exercises,
                overview.improvedExercises, overview.improvedExercises)
                else stringResource(R.string.insight_overview_title), style = MaterialTheme.typography.headlineMediumEmphasized)
            if (overview.improvedExercises > 0) Text(stringResource(R.string.insight_progress_basis),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightMetric(overview.totals.days, stringResource(R.string.insight_training_days))
                InsightMetric(overview.totals.sets, stringResource(R.string.insight_recorded_set_count))
                InsightMetric(overview.totals.sessions, stringResource(R.string.insight_session_count))
            }
        }
    }
}

@Composable
private fun InsightMetric(value: Int, label: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(insightNumber(value), style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun performanceDescription(value: InsightPerformance): String = if (value.basis == WeightBasis.BODYWEIGHT) {
    stringResource(R.string.insight_reps_value, value.reps)
} else stringResource(R.string.insight_performance_value, insightNumber(requireNotNull(value.loadKg)), value.reps)

@Composable
internal fun InsightComparisonContent(comparison: InsightComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(basisLabel(comparison.latest.basis), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(performanceDescription(comparison.latest), style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.insight_latest_performance_date, insightDate(comparison.latest.date)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val previous = comparison.previous
        if (previous != null) {
            Text(stringResource(R.string.insight_previous_performance, insightDate(previous.date), performanceDescription(previous)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(when (comparison.change) {
                InsightChange.MORE_REPS -> if (comparison.latest.basis == WeightBasis.BODYWEIGHT)
                    R.string.insight_change_bodyweight_reps else R.string.insight_change_more_reps
                InsightChange.MORE_LOAD -> R.string.insight_change_more_load
                InsightChange.LESS_ASSISTANCE -> R.string.insight_change_less_assistance
                InsightChange.SAME -> if (comparison.latest.basis == WeightBasis.BODYWEIGHT)
                    R.string.insight_change_bodyweight_same else R.string.insight_change_same
                else -> R.string.insight_change_mixed
            }), style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (comparison.improved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(stringResource(R.string.insight_progress_baseline), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InsightProgressCard(progress: InsightExerciseProgress, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(progress.exercise.name, style = MaterialTheme.typography.titleLargeEmphasized)
            if (progress.comparisons.isEmpty()) Text(stringResource(R.string.insight_focus_no_comparison),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress.comparisons.forEach { InsightComparisonContent(it) }
            Text(stringResource(R.string.insight_view_exercise), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun InsightAchievementRow(achievement: InsightAchievement, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(onClick = onClick, shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(when (achievement.kind) {
                    InsightRecordKind.LOAD -> R.string.insight_record_load
                    InsightRecordKind.REPS -> if (achievement.performance.basis == WeightBasis.BODYWEIGHT)
                        R.string.insight_record_bodyweight_reps else R.string.insight_record_reps
                    InsightRecordKind.ASSISTANCE -> R.string.insight_record_assistance
                }), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLargeEmphasized)
                Text(achievement.name)
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.insight_record_performance, basisLabel(achievement.performance.basis),
                    performanceDescription(achievement.performance)))
                Text(stringResource(R.string.insight_previous_performance, insightDate(achievement.previous.date),
                    performanceDescription(achievement.previous)))
                Text(insightDate(achievement.performance.date), style = MaterialTheme.typography.bodySmall)
            }
        })
}

/** Marks represent recorded quantities, with no implied goal or judgement about missing days. */
@Composable
private fun InsightQuantityBar(value: Int, maximum: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { }) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (value > 0 && maximum > 0) drawRoundRect(color,
            size = Size(size.width * value.toFloat() / maximum, size.height), cornerRadius = radius)
    }
}

@Composable
internal fun InsightWeeklyTrends(weeks: List<InsightWeek>) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.insight_weekly_description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val maximum = weeks.maxOfOrNull { it.sets } ?: 0
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.insight_date_span, insightDate(week.start), insightDate(week.end)),
                        style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(if (week.partial) R.string.insight_week_partial else R.string.insight_week_totals,
                        week.days, week.sets), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InsightQuantityBar(week.sets, maximum)
                }
            }
            Text(stringResource(R.string.insight_recorded_sets_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InsightDistribution(exercises: List<InsightExercise>, onExercise: (String) -> Unit, onShowAll: () -> Unit) {
    val sorted = exercises.sortedWith(compareByDescending<InsightExercise> { item -> item.occurrences.sumOf { it.exercise.sets.size } }
        .thenBy { it.name })
    val shown = sorted.take(5)
    val maximum = sorted.firstOrNull()?.occurrences?.sumOf { it.exercise.sets.size } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        shown.forEachIndexed { index, exercise ->
            val sets = exercise.occurrences.sumOf { it.exercise.sets.size }
            SegmentedListItem(onClick = { onExercise(exercise.nameKey) },
                shapes = ListItemDefaults.segmentedShapes(index, shown.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                content = { Text(exercise.name) }, supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.insight_exercise_summary,
                            exercise.occurrences.map { it.sessionId }.distinct().size, sets))
                        InsightQuantityBar(sets, maximum)
                    }
                })
        }
        if (exercises.isNotEmpty()) FilledTonalButton(onClick = onShowAll, modifier = Modifier.padding(top = 12.dp),
            shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_all_exercises)) }
    }
}
