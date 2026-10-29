package com.example.fitlog.feature.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.fitLogPagePadding
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun InsightWeeklyContent(
    report: InsightWeeklyReport,
    onExercise: (String) -> Unit,
    onOpenLog: () -> Unit,
    onViewDays: () -> Unit,
    viewPicker: @Composable () -> Unit,
    notices: @Composable () -> Unit,
    listState: LazyListState,
) {
    val padding = fitLogPagePadding()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().glassPageSource(),
        contentPadding = PaddingValues(start = padding, end = padding, bottom = FitLogDimens.NavigationBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "view") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { viewPicker(); notices() }
        }
        item(key = "weekly-report") { WeeklyReportHero(report, onOpenLog) }
        item(key = "period-analysis") { WeeklyPeriodAnalysis(report, onExercise) }
        item(key = "weekly-activity") { WeeklyActivity(report, onViewDays) }
        item(key = "weekly-balance") {
            InsightTrainingBalance(report.balance, stringResource(R.string.weekly_balance_description),
                emptyMessage = stringResource(R.string.weekly_no_records))
        }
        item(key = "weekly-coverage") {
            Text(stringResource(R.string.weekly_recorded_only), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyReportHero(report: InsightWeeklyReport, onOpenLog: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern(if (report.start.year == report.end.year) "MMM d" else "MMM d, yyyy", locale)
    val percent = NumberFormat.getPercentInstance(locale).format(report.frequency.toDouble())
    val points = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        .format(abs(report.frequencyChange * 100))
    val primary = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary
    val end = reportGradientEnd(primary, MaterialTheme.colorScheme.primaryContainer, foreground)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Surface(shape = RoundedCornerShape(32.dp), color = primary, contentColor = foreground) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(primary, end))).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.weekly_report), style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.insight_date_span, report.start.format(format), report.end.format(format)),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(percent, style = MaterialTheme.typography.displayLargeEmphasized.copy(
                    fontSize = if (largeText) 48.sp else 72.sp,
                    lineHeight = if (largeText) 56.sp else 80.sp, fontFeatureSettings = "tnum"))
                Text(stringResource(R.string.weekly_frequency), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.weekly_days_recorded, report.trainingDays, report.elapsedDays),
                    style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.weekly_totals,
                    pluralStringResource(R.plurals.weekly_session_count, report.sessions, report.sessions),
                    pluralStringResource(R.plurals.weekly_set_count, report.sets, report.sets)),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(when {
                report.previousTrainingDays == 0 -> R.string.weekly_no_comparison
                report.frequencyChange > 0 -> R.string.weekly_frequency_up
                report.frequencyChange < 0 -> R.string.weekly_frequency_down
                else -> R.string.weekly_frequency_same
            }, points), style = MaterialTheme.typography.titleSmallEmphasized)
            if (report.sessions == 0) OutlinedButton(onClick = onOpenLog,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground),
                border = androidx.compose.foundation.BorderStroke(1.dp, foreground), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.insight_open_log))
            }
        }
    }
}

/** Keep the gradient's lightest endpoint readable with the theme's on-primary text. */
private fun reportGradientEnd(primary: Color, target: Color, foreground: Color): Color {
    for (step in 10 downTo 0) {
        val candidate = lerp(primary, target, step / 20f)
        val a = foreground.luminance()
        val b = candidate.luminance()
        if ((max(a, b) + 0.05f) / (min(a, b) + 0.05f) >= 4.5f) return candidate
    }
    return primary
}

@Composable
private fun WeeklyPeriodAnalysis(report: InsightWeeklyReport, onExercise: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = NumberFormat.getPercentInstance(locale)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    InsightDashboardCard(stringResource(R.string.weekly_analysis)) {
        val star = report.exercises.firstOrNull()
        if (star == null) {
            Text(stringResource(R.string.weekly_no_records), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.weekly_most_recorded), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(onClick = { onExercise(star.exercise.nameKey) }, shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    val share: @Composable () -> Unit = {
                        Text(percent.format(star.share.toDouble()), style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (largeText) Column(Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeeklyExerciseLabel(star)
                        share()
                    } else Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeeklyExerciseLabel(star, Modifier.weight(1f))
                        share()
                    }
                }
                Text(stringResource(R.string.weekly_share_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weekly_less_recorded), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.lessRecorded.isEmpty()) Text(stringResource(if (report.previousTrainingDays == 0)
                R.string.weekly_no_comparison else R.string.weekly_no_decrease), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            report.lessRecorded.forEach { exercise ->
                Surface(onClick = { onExercise(exercise.exercise.nameKey) }, shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(exercise.exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.weekly_set_change, exercise.previousSets, exercise.sets),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(percent.format((exercise.sets - exercise.previousSets).toDouble() / exercise.previousSets),
                            style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(stringResource(R.string.weekly_comparison_span, report.comparisonStart.format(dateFormat), report.comparisonEnd.format(dateFormat)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyExerciseLabel(exercise: InsightWeeklyExercise, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(exercise.exercise.name, style = MaterialTheme.typography.titleMediumEmphasized)
        Text(stringResource(R.string.weekly_exercise_totals,
            pluralStringResource(R.plurals.weekly_set_count, exercise.sets, exercise.sets),
            pluralStringResource(R.plurals.weekly_training_days, exercise.days, exercise.days)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeeklyActivity(report: InsightWeeklyReport, onViewDays: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val maximum = report.days.maxOf { it.sessions }.coerceAtLeast(1)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    InsightDashboardCard(stringResource(R.string.weekly_activity), stringResource(R.string.weekly_activity_description)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val spacing = 8.dp
            val width = maxOf(40.dp * fontScale, (maxWidth - spacing * 6) / 7)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing)) {
                report.days.forEach { day ->
                    val description = if (day.future) stringResource(R.string.weekly_future_day, day.date.format(dateFormat))
                        else stringResource(R.string.weekly_day_description, day.date.format(dateFormat), day.sessions, day.sets)
                    Column(Modifier.width(width).clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Canvas(Modifier.fillMaxWidth().height(144.dp)) {
                            val inset = size.width * 0.22f
                            val barWidth = size.width - inset * 2
                            val radius = CornerRadius(barWidth / 2)
                            drawRoundRect(track, topLeft = androidx.compose.ui.geometry.Offset(inset, 0f),
                                size = Size(barWidth, size.height), cornerRadius = radius)
                            if (day.sessions > 0) {
                                val height = size.height * day.sessions / maximum
                                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(inset, size.height - height),
                                    size = Size(barWidth, height), cornerRadius = radius)
                            }
                        }
                        Text(if (day.future) stringResource(R.string.weekly_not_yet) else insightNumber(day.sessions),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = if (day.future) MaterialTheme.colorScheme.onSurfaceVariant else color)
                        Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = onViewDays, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_view_days)) }
    }
}
