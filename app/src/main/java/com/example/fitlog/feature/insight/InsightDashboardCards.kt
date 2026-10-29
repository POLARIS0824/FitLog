package com.example.fitlog.feature.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.glass.LookSurface
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.first

private val DashboardShape = RoundedCornerShape(32.dp)
private val MetricShape = RoundedCornerShape(40.dp)

@Composable
internal fun InsightDashboardCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LookSurface(shape = DashboardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
internal fun InsightAtAGlance(presentation: InsightPresentation, onOpenLog: () -> Unit) {
    val dashboard = presentation.dashboard
    val totals = presentation.overview.totals
    val locale = LocalConfiguration.current.locales[0]
    val outline = MaterialTheme.colorScheme.outlineVariant
    InsightDashboardCard(stringResource(R.string.dashboard_glance)) {
        Column(Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(outline, cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
        }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dashboard_weekday_pattern), style = MaterialTheme.typography.titleSmallEmphasized)
            Text(dashboard.busiestDay?.let { day ->
                stringResource(R.string.dashboard_weekday_description,
                    day.getDisplayName(TextStyle.FULL, locale), dashboard.busiestSessions)
            } ?: stringResource(R.string.insight_empty_training_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DashboardMetric(insightNumber(dashboard.todaySessions), stringResource(R.string.dashboard_today), Modifier.fillMaxWidth())
        if (LocalDensity.current.fontScale >= 1.5f) {
            DashboardMetric(insightNumber(dashboard.currentStreak), stringResource(R.string.dashboard_current_streak), Modifier.fillMaxWidth())
            DashboardMetric(insightNumber(dashboard.bestStreak), stringResource(R.string.dashboard_best_streak), Modifier.fillMaxWidth())
            DashboardMetric(insightNumber(totals.days), stringResource(R.string.insight_training_days), Modifier.fillMaxWidth())
            DashboardMetric(insightNumber(totals.sets), stringResource(R.string.insight_recorded_set_count), Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetric(insightNumber(dashboard.currentStreak), stringResource(R.string.dashboard_current_streak), Modifier.weight(1f))
                DashboardMetric(insightNumber(dashboard.bestStreak), stringResource(R.string.dashboard_best_streak), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetric(insightNumber(totals.days), stringResource(R.string.insight_training_days), Modifier.weight(1f))
                DashboardMetric(insightNumber(totals.sets), stringResource(R.string.insight_recorded_set_count), Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.dashboard_range_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (totals.sessions == 0) FilledTonalButton(onClick = onOpenLog, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.insight_open_log))
        }
    }
}

@Composable
private fun DashboardMetric(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = MetricShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMediumEmphasized.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private enum class ActivityChart { GRID, BARS, LINE }

@Composable
internal fun InsightConsistency(presentation: InsightPresentation, onViewDays: () -> Unit) {
    var chart by rememberSaveable { mutableStateOf(ActivityChart.GRID) }
    val dashboard = presentation.dashboard
    InsightDashboardCard(stringResource(R.string.dashboard_consistency), stringResource(R.string.dashboard_consistency_description)) {
        FitLogConnectedButtonGroup(ActivityChart.entries, chart, label = {
            stringResource(when (it) { ActivityChart.GRID -> R.string.dashboard_grid
                ActivityChart.BARS -> R.string.dashboard_bars; ActivityChart.LINE -> R.string.dashboard_line })
        }, onSelect = { chart = it })
        Text(stringResource(R.string.insight_date_span, insightDate(dashboard.calendarStart), insightDate(presentation.data.today)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (chart) {
            ActivityChart.GRID -> DashboardCalendar(dashboard, presentation.data.today)
            else -> DashboardWeeklyChart(dashboard.weeks, chart)
        }
        TextButton(onClick = onViewDays, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_view_days)) }
    }
}

@Composable
private fun DashboardCalendar(dashboard: InsightDashboard, today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val shades = (0..3).map { lerp(empty, primary, it / 3f) }
    val description = stringResource(R.string.dashboard_calendar_description, dashboard.calendar.size)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cellSize = 18.dp * fontScale
    val monthHeight = 24.dp * fontScale
    val measurer = rememberTextMeasurer()
    val monthStyle = MaterialTheme.typography.labelSmall.copy(color = primary)
    val months = dashboard.weeks.mapIndexedNotNull { index, week ->
        if (index == 0 || dashboard.weeks[index - 1].start.month != week.start.month)
            index to measurer.measure(week.start.month.getDisplayName(TextStyle.SHORT, locale), monthStyle)
        else null
    }
    val scroll = rememberScrollState()
    LaunchedEffect(dashboard.calendarStart) {
        val maximum = snapshotFlow { scroll.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
        scroll.scrollTo(maximum)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(top = monthHeight)) {
            DayOfWeek.entries.forEach { day ->
                Box(Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                    Text(if (day.value % 2 == 1) day.getDisplayName(TextStyle.NARROW, locale) else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll)) {
            Canvas(Modifier.width(cellSize * dashboard.weeks.size).height(monthHeight)) {
                months.forEach { (index, label) -> drawText(label, topLeft = Offset(index * cellSize.toPx(), 0f)) }
            }
            Canvas(Modifier.width(cellSize * dashboard.weeks.size).height(cellSize * 7)
                .semantics { contentDescription = description }) {
                val cell = cellSize.toPx()
                dashboard.weeks.forEachIndexed { column, week -> repeat(7) { row ->
                    val date = week.start.plusDays(row.toLong())
                    if (date in dashboard.calendarStart..today) {
                        val count = dashboard.calendar[date] ?: 0
                        drawRoundRect(shades[count.coerceAtMost(3)], Offset(column * cell, row * cell),
                            Size(cell - 3.dp.toPx(), cell - 3.dp.toPx()), CornerRadius(3.dp.toPx()))
                    }
                } }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.dashboard_less), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(4.dp))
        shades.forEach { color -> Canvas(Modifier.size(12.dp).padding(1.dp)) {
            drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()))
        } }
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.dashboard_more), style = MaterialTheme.typography.labelSmall)
    }
    Text(stringResource(R.string.dashboard_calendar_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DashboardWeeklyChart(weeks: List<InsightActivityWeek>, chart: ActivityChart) {
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val maximum = (weeks.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    val description = stringResource(R.string.dashboard_weekly_description, weeks.sumOf { it.total }, maximum)
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
        val inset = 8.dp.toPx()
        val height = size.height - inset * 2
        val width = size.width / weeks.size.coerceAtLeast(1)
        repeat(4) { index ->
            val y = inset + height * index / 3
            drawLine(axis, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val points = weeks.mapIndexed { index, week ->
            Offset(width * (index + 0.5f), size.height - inset - height * week.total / maximum)
        }
        if (chart == ActivityChart.BARS) weeks.forEachIndexed { index, week ->
            if (week.total > 0) drawRoundRect(color, Offset(width * index + width * 0.18f, points[index].y),
                Size(width * 0.64f, height * week.total / maximum), CornerRadius(3.dp.toPx()))
        } else {
            points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 3.dp.toPx(), StrokeCap.Round) }
            points.forEach { drawCircle(color, 2.5.dp.toPx(), it) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(insightDate(weeks.first().start), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.dashboard_weekly_max, maximum), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun InsightExerciseRanking(dashboard: InsightDashboard, onExercise: (String) -> Unit, onAll: () -> Unit) {
    InsightDashboardCard(stringResource(R.string.dashboard_ranking), stringResource(R.string.dashboard_ranking_description)) {
        if (dashboard.ranks.isEmpty()) DashboardEmpty()
        dashboard.ranks.take(5).forEachIndexed { index, rank ->
            Surface(onClick = { onExercise(rank.exercise.nameKey) }, shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = if (index == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text(insightNumber(index + 1), style = MaterialTheme.typography.labelLargeEmphasized)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(rank.exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(NumberFormat.getPercentInstance().format(rank.share.toDouble()),
                                style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                        LinearProgressIndicator(progress = { rank.share }, modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            gapSize = 0.dp, drawStopIndicator = {})
                        Text(stringResource(R.string.dashboard_rank_detail, rank.days, rank.sets), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (dashboard.ranks.isNotEmpty()) TextButton(onClick = onAll, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.insight_all_exercises))
        }
    }
}

@Composable
internal fun InsightTrainingBalance(dashboard: InsightDashboard) {
    InsightTrainingBalance(dashboard.ranks, stringResource(R.string.dashboard_balance_description))
}

@Composable
internal fun InsightTrainingBalance(exercises: List<InsightRank>, description: String, emptyMessage: String? = null) {
    val ranks = exercises.sortedByDescending { it.sets }.take(5)
    val color = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    val grid = MaterialTheme.colorScheme.outlineVariant
    InsightDashboardCard(stringResource(R.string.dashboard_balance), description) {
        if (ranks.isEmpty()) {
            if (emptyMessage == null) DashboardEmpty() else Text(emptyMessage,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val description = ranks.map { stringResource(R.string.dashboard_balance_item, it.exercise.name, it.sets) }.joinToString()
            val measurer = rememberTextMeasurer()
            val labels = ranks.indices.map { measurer.measure(insightNumber(it + 1),
                MaterialTheme.typography.labelMedium.copy(color = color)) }
            Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
                val count = maxOf(3, ranks.size)
                val center = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) * 0.37f
                fun vertex(index: Int, fraction: Float): Offset {
                    val angle = -PI / 2 + 2 * PI * index / count
                    return center + Offset((cos(angle) * radius * fraction).toFloat(), (sin(angle) * radius * fraction).toFloat())
                }
                fun polygon(values: List<Float>) = Path().apply {
                    values.forEachIndexed { index, value -> val point = vertex(index, value)
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
                    close()
                }
                (1..4).forEach { ring -> drawPath(polygon(List(count) { ring / 4f }), grid, style = Stroke(1.dp.toPx())) }
                repeat(count) { drawLine(grid, center, vertex(it, 1f), 1.dp.toPx()) }
                val maximum = ranks.maxOf { it.sets }.coerceAtLeast(1)
                val values = List(count) { ranks.getOrNull(it)?.sets?.toFloat()?.div(maximum) ?: 0f }
                drawPath(polygon(values), fill)
                drawPath(polygon(values), color, style = Stroke(2.dp.toPx()))
                values.forEachIndexed { index, value -> drawCircle(color, 4.dp.toPx(), vertex(index, value)) }
                labels.forEachIndexed { index, label ->
                    drawText(label, topLeft = vertex(index, 1.14f) - Offset(label.size.width / 2f, label.size.height / 2f))
                }
            }
            ranks.forEachIndexed { index, rank ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(insightNumber(index + 1), style = MaterialTheme.typography.labelLarge, color = color)
                    Text(rank.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(insightNumber(rank.sets), style = MaterialTheme.typography.labelLargeEmphasized, color = color)
                }
            }
        }
    }
}

@Composable
internal fun InsightActivityMatrix(dashboard: InsightDashboard, today: LocalDate, onViewDays: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.surfaceContainerHigh
    val format = DateTimeFormatter.ofPattern("M/d", locale)
    InsightDashboardCard(stringResource(R.string.dashboard_matrix), stringResource(R.string.dashboard_matrix_description)) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.widthIn(min = 260.dp).fillMaxWidth()) {
                Spacer(Modifier.width(44.dp))
                dashboard.matrix.forEach { week ->
                    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                        Text(week.start.format(format), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            DayOfWeek.entries.forEachIndexed { row, day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.getDisplayName(TextStyle.SHORT, locale), Modifier.width(44.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dashboard.matrix.forEach { week ->
                        val date = week.start.plusDays(row.toLong())
                        val count = week.sessions[row]
                        val description = stringResource(R.string.dashboard_matrix_cell, insightDate(date), count)
                        Canvas(Modifier.size(56.dp, 40.dp).semantics { if (date <= today) contentDescription = description }) {
                            if (date <= today) {
                                drawCircle(grid, 2.dp.toPx())
                                if (count > 0) {
                                    val maximum = dashboard.matrix.maxOf { it.sessions.maxOrNull() ?: 0 }.coerceAtLeast(1)
                                    drawCircle(color, (5 + 10 * kotlin.math.sqrt(count.toFloat() / maximum)).dp.toPx())
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.dashboard_matrix_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onViewDays, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_view_days)) }
    }
}

@Composable
private fun DashboardEmpty() {
    Text(stringResource(R.string.insight_empty_training_hint), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
