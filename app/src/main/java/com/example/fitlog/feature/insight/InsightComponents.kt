@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.example.fitlog.feature.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.domain.insight.InsightDiary
import com.example.fitlog.domain.insight.InsightPoint
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.InsightSeries
import com.example.fitlog.model.record.ConfirmedSet
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import com.example.fitlog.ui.components.FitLogNotice
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlinx.coroutines.flow.first

internal fun insightNumber(value: Number): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
internal fun insightDate(value: LocalDate): String = value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
internal fun InsightRangePicker(range: InsightRange, onSelect: (InsightRange) -> Unit, enabled: Boolean = true) {
    FitLogConnectedButtonGroup(InsightRange.entries, range, enabled = enabled, onSelect = onSelect,
        label = { stringResource(when (it) {
            InsightRange.THIRTY -> R.string.insight_range_thirty
            InsightRange.NINETY -> R.string.insight_range_ninety
            InsightRange.YEAR -> R.string.insight_range_year
            InsightRange.ALL -> R.string.insight_range_all
        }) })
}

@Composable
internal fun InsightReadState(state: InsightUiState, onRetry: () -> Unit): Boolean {
    when {
        state.loading -> { LoadingIndicator(); return false }
        state.error != null -> { FitLogNotice(stringResource(requireNotNull(state.error)), error = true)
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_retry)) }; return false }
    }
    return state.data != null
}

@Composable
internal fun InsightCoverage(state: InsightUiState, showScanTime: Boolean = true) {
    val index = state.index
    val timestamp = index?.scan?.completedAt?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showScanTime && index != null) Text(if (timestamp == null) stringResource(R.string.insight_scan_unknown)
            else stringResource(R.string.insight_scan_time, timestamp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (index?.refreshing == true) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        if (index?.scan?.status == IndexedScan.PARTIAL) FitLogNotice(stringResource(R.string.insight_scan_partial))
        if (index?.refreshFailed == true || index?.scan?.status == IndexedScan.FAILED) FitLogNotice(stringResource(R.string.insight_scan_failed), error = true)
        state.message?.let { FitLogNotice(stringResource(it), error = true) }
    }
}

@Composable
internal fun InsightRow(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InsightHeatmap(data: InsightData, activity: Map<LocalDate, List<InsightDiary>>) {
    val first = data.today.minusDays(364)
    val gridStart = first.minusDays(first.dayOfWeek.value.toLong() - 1)
    val weeks = ((data.today.toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activityColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.insight_heatmap_description, activity.size)
    val scroll = rememberScrollState()
    var positioned by rememberSaveable(data.vaultId) { mutableStateOf(false) }
    LaunchedEffect(data.vaultId) {
        if (!positioned) {
            snapshotFlow { scroll.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
            scroll.scrollTo(scroll.maxValue)
            positioned = true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.insight_date_span, insightDate(first), insightDate(data.today)), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Canvas(Modifier.width((weeks * 16).dp).height(112.dp).semantics { contentDescription = description }) {
                val cell = 16.dp.toPx()
                repeat(weeks) { week -> repeat(7) { day ->
                    val date = gridStart.plusDays((week * 7 + day).toLong())
                    if (date in first..data.today) {
                        val count = activity[date].orEmpty().sumOf { it.sessions.size }.coerceAtMost(4)
                        val markerSize = cell - 3.dp.toPx()
                        val origin = Offset(week * cell, day * cell)
                        drawRoundRect(emptyColor, origin, Size(markerSize, markerSize), CornerRadius(2.dp.toPx()))
                        if (count > 0) drawCircle(activityColor, (count + 2).dp.toPx(),
                            center = origin + Offset(markerSize / 2, markerSize / 2))
                    }
                } }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..4).forEach { count ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(13.dp)) {
                        drawRoundRect(emptyColor, cornerRadius = CornerRadius(2.dp.toPx()))
                        if (count > 0) drawCircle(activityColor, (count + 2).dp.toPx())
                    }
                    Text(stringResource(if (count == 4) R.string.insight_heatmap_four_plus else R.string.insight_heatmap_count, count),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(stringResource(R.string.insight_heatmap_size_legend), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InsightChart(series: InsightSeries, onOpen: (String) -> Unit) {
    val points = series.points
    if (points.isEmpty()) return
    var selectedDate by rememberSaveable(series.basis, points) { mutableStateOf(points.last().date.toString()) }
    val selectedIndex = points.indexOfFirst { it.date.toString() == selectedDate }.coerceAtLeast(0)
    val selected = points[selectedIndex]
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val spread = (maximum - minimum).takeIf { it > 0 } ?: 1.0
    val start = points.first().date.toEpochDay()
    val days = (points.last().date.toEpochDay() - start).coerceAtLeast(1)
    val description = stringResource(R.string.insight_chart_description, points.size,
        insightDate(points.first().date), insightDate(points.last().date))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(160.dp).padding(vertical = 4.dp), verticalArrangement = if (minimum == maximum) Arrangement.Center else Arrangement.SpaceBetween) {
            Text(insightNumber(maximum), style = MaterialTheme.typography.labelSmall)
            if (minimum != maximum) {
                Text(insightNumber(minimum + (maximum - minimum) / 2), style = MaterialTheme.typography.labelSmall)
                Text(insightNumber(minimum), style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val inset = 12.dp.toPx()
                        val fraction = ((tap.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val day = start + fraction.toDouble() * days
                        selectedDate = points.minBy { abs(it.date.toEpochDay() - day) }.date.toString()
                    }
                }) {
                val inset = 12.dp.toPx()
                fun position(point: InsightPoint): Offset = Offset(
                    if (points.size == 1) size.width / 2 else inset + ((point.date.toEpochDay() - start).toDouble() / days * (size.width - inset * 2)).toFloat(),
                    if (maximum == minimum) size.height / 2 else size.height - inset - ((point.value - minimum) / spread * (size.height - inset * 2)).toFloat())
                drawLine(axis, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
                points.zipWithNext().forEach { (a, b) -> drawLine(color, position(a), position(b), 3.dp.toPx()) }
                points.forEach { drawCircle(color, 4.dp.toPx(), position(it)) }
                drawCircle(color, 9.dp.toPx(), position(selected), style = Stroke(2.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(insightDate(points.first().date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (points.size > 1) Text(insightDate(points.last().date), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { selectedDate = points[selectedIndex - 1].date.toString() }, enabled = selectedIndex > 0,
            shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.insight_previous_point))
        }
        Text(stringResource(R.string.insight_point_value, insightDate(selected.date), insightNumber(selected.value)),
            style = MaterialTheme.typography.bodyMediumEmphasized,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        IconButton(onClick = { selectedDate = points[selectedIndex + 1].date.toString() }, enabled = selectedIndex < points.lastIndex,
            shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.insight_next_point),
                Modifier.graphicsLayer { rotationZ = 180f })
        }
    }
    selected.recordIds.forEachIndexed { index, id ->
        TextButton(onClick = { onOpen(id) }, shapes = ButtonDefaults.shapes()) {
            Text(if (selected.recordIds.size == 1) stringResource(R.string.insight_view_source)
                else stringResource(R.string.insight_source_number, index + 1))
        }
    }
    if (points.size == 1) Text(stringResource(R.string.insight_single_point), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun basisLabel(basis: WeightBasis?): String = stringResource(when (basis) {
    WeightBasis.TOTAL -> R.string.insight_basis_total
    WeightBasis.PER_SIDE -> R.string.insight_basis_side
    WeightBasis.ADDED -> R.string.insight_basis_added
    WeightBasis.ASSISTED -> R.string.insight_basis_assisted
    WeightBasis.BODYWEIGHT -> R.string.insight_basis_bodyweight
    else -> R.string.insight_basis_unknown
})

@Composable
internal fun setDescription(set: ConfirmedSet): String {
    val unit = when (set.unit) { WeightUnit.KG -> stringResource(R.string.insight_kg)
        WeightUnit.LB -> stringResource(R.string.insight_lb); else -> stringResource(R.string.insight_unknown) }
    val weight = set.weight?.let { stringResource(R.string.insight_weight_value, insightNumber(it), unit) }
        ?: stringResource(R.string.insight_unknown)
    val reps = set.reps?.let { stringResource(R.string.insight_reps_value, it) } ?: stringResource(R.string.insight_unknown)
    return stringResource(R.string.insight_set_value, basisLabel(set.basis), weight, reps)
}
