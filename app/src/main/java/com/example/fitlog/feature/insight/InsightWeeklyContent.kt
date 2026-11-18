package com.example.fitlog.feature.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.feature.insight.components.insightNumber
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.FitLogType
import com.example.fitlog.ui.theme.fitLogPagePadding
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
    val spacing = FitLogTheme.spacing
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().glassPageSource(),
        contentPadding = PaddingValues(start = padding, end = padding, bottom = FitLogTheme.dimens.page.navigationBottomPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        item(key = "view") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) { viewPicker(); notices() }
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

/** The report's hero moment: the weekly frequency set as a huge number on a primary gradient. */
@Composable
private fun WeeklyReportHero(report: InsightWeeklyReport, onOpenLog: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = FitLogTheme.spacing
    val format = DateTimeFormatter.ofPattern(if (report.start.year == report.end.year) "MMM d" else "MMM d, yyyy", locale)
    val percent = NumberFormat.getPercentInstance(locale).format(report.frequency.toDouble())
    val points = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        .format(abs(report.frequencyChange * 100))
    val primary = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary
    val end = reportGradientEnd(primary, MaterialTheme.colorScheme.primaryContainer, foreground)
    Surface(shape = FitLogContainment.heroShape, color = primary, contentColor = foreground) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(primary, end))).padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.l)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(stringResource(R.string.weekly_report), style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.insight_date_span, report.start.format(format), report.end.format(format)),
                    style = FitLogType.data)
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(percent, style = FitLogType.heroNumber())
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
            // Tonal container on the primary hero: a clear target that keeps its own role pair.
            if (report.sessions == 0) FilledTonalButton(onClick = onOpenLog, shapes = ButtonDefaults.shapes()) {
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
    val spacing = FitLogTheme.spacing
    val percent = NumberFormat.getPercentInstance(locale)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val itemColors = FitLogContainment.pageListItemColors()
    // Both groups are segmented lists, so the analysis sits on the page under a title rather than in a card.
    InsightDashboardSection(stringResource(R.string.weekly_analysis)) {
        val star = report.exercises.firstOrNull()
        if (star == null) {
            Text(stringResource(R.string.weekly_no_records), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(stringResource(R.string.weekly_most_recorded), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val share: @Composable () -> Unit = {
                    Text(percent.format(star.share.toDouble()), style = FitLogType.metricNumber,
                        color = MaterialTheme.colorScheme.primary)
                }
                SegmentedListItem(onClick = { onExercise(star.exercise.nameKey) },
                    shapes = ListItemDefaults.segmentedShapes(0, 1), colors = itemColors,
                    trailingContent = if (largeText) null else share,
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                            Text(weeklyExerciseTotals(star))
                            if (largeText) share()
                        }
                    }) {
                    Text(star.exercise.name, style = MaterialTheme.typography.titleMediumEmphasized)
                }
                Text(stringResource(R.string.weekly_share_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(top = spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(stringResource(R.string.weekly_less_recorded), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.lessRecorded.isEmpty()) Text(stringResource(if (report.previousTrainingDays == 0)
                R.string.weekly_no_comparison else R.string.weekly_no_decrease), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                report.lessRecorded.forEachIndexed { index, exercise ->
                    SegmentedListItem(onClick = { onExercise(exercise.exercise.nameKey) },
                        shapes = ListItemDefaults.segmentedShapes(index, report.lessRecorded.size), colors = itemColors,
                        supportingContent = {
                            Text(stringResource(R.string.weekly_set_change, exercise.previousSets, exercise.sets))
                        },
                        trailingContent = {
                            Text(percent.format((exercise.sets - exercise.previousSets).toDouble() / exercise.previousSets),
                                // A decrease is not an error, so it stays in the low-emphasis role.
                                style = FitLogType.dataEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }) {
                        Text(exercise.exercise.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text(stringResource(R.string.weekly_comparison_span, report.comparisonStart.format(dateFormat), report.comparisonEnd.format(dateFormat)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun weeklyExerciseTotals(exercise: InsightWeeklyExercise): String = stringResource(R.string.weekly_exercise_totals,
    pluralStringResource(R.plurals.weekly_set_count, exercise.sets, exercise.sets),
    pluralStringResource(R.plurals.weekly_training_days, exercise.days, exercise.days))

@Composable
private fun WeeklyActivity(report: InsightWeeklyReport, onViewDays: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = FitLogTheme.spacing
    val dimens = FitLogTheme.dimens.insight
    val color = MaterialTheme.colorScheme.primary
    // Tracks recede into the card like a nested tile; the bars carry the data in primary.
    val track = FitLogContainment.nestedColor
    val maximum = report.days.maxOf { it.sessions }.coerceAtLeast(1)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    InsightDashboardCard(stringResource(R.string.weekly_activity), stringResource(R.string.weekly_activity_description)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = spacing.s
            val width = maxOf(dimens.weekdayColumnMinWidth * fontScale, (maxWidth - gap * 6) / 7)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                report.days.forEach { day ->
                    val description = if (day.future) stringResource(R.string.weekly_future_day, day.date.format(dateFormat))
                        else stringResource(R.string.weekly_day_description, day.date.format(dateFormat), day.sessions, day.sets)
                    Column(Modifier.width(width).clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Canvas(Modifier.fillMaxWidth().height(dimens.weekdayBarHeight)) {
                            val inset = size.width * 0.22f
                            val barWidth = size.width - inset * 2
                            val radius = CornerRadius(barWidth / 2)
                            drawRoundRect(track, topLeft = Offset(inset, 0f),
                                size = Size(barWidth, size.height), cornerRadius = radius)
                            if (day.sessions > 0) {
                                val height = size.height * day.sessions / maximum
                                drawRoundRect(color, topLeft = Offset(inset, size.height - height),
                                    size = Size(barWidth, height), cornerRadius = radius)
                            }
                        }
                        if (day.future) Text(stringResource(R.string.weekly_not_yet), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text(insightNumber(day.sessions), style = FitLogType.dataEmphasized, color = color)
                        Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = onViewDays, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_view_days)) }
    }
}
