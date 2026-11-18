@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.feature.insight

import com.example.fitlog.feature.insight.components.AliasEditor
import com.example.fitlog.feature.insight.components.InsightChart
import com.example.fitlog.feature.insight.components.InsightComparisonContent
import com.example.fitlog.feature.insight.components.InsightCoverage
import com.example.fitlog.feature.insight.components.InsightRangePicker
import com.example.fitlog.feature.insight.components.InsightReadState
import com.example.fitlog.feature.insight.components.InsightRow
import com.example.fitlog.feature.insight.components.basisLabel
import com.example.fitlog.feature.insight.components.insightDate
import com.example.fitlog.feature.insight.components.insightNumber
import com.example.fitlog.feature.insight.components.setDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSectionTitle
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.FitLogType
import com.example.fitlog.ui.theme.fitLogPagePadding

@Composable
internal fun ExerciseHistoryScreen(vm: InsightViewModel, nameKey: String, onOpen: (String) -> Unit,
    onOpenLog: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumed() }
    var editingAliases by rememberSaveable { mutableStateOf(false) }
    var showRecordedSets by rememberSaveable(nameKey) { mutableStateOf(false) }
    var expandedCurves by rememberSaveable(nameKey) { mutableStateOf(listOf<String>()) }
    val data = state.data.takeIf { state.error == null }
    val presentation = state.presentation
    val exercise = presentation?.exercise
    val progress = presentation?.progress
    LaunchedEffect(state.aliasSaved) {
        if (state.aliasSaved) { vm.aliasSaveHandled(); editingAliases = false }
    }
    val title = exercise?.name ?: data?.aliases?.groupFor(nameKey)?.primaryName ?: data?.names?.get(nameKey)
        ?: stringResource(R.string.insight_exercise_history)
    val behavior = rememberFitLogTopAppBarBehavior()
    val pagePadding = fitLogPagePadding()
    val spacing = FitLogTheme.spacing
    val page = FitLogTheme.dimens.page
    Column(modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        FitLogTopAppBar(title, behavior, onBack = onBack, actions = {
            if (data != null) IconButton(onClick = { vm.clearAliasError(); editingAliases = true },
                enabled = !state.aliasSaving, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.insight_manage_aliases))
            }
            FilledTonalIconButton(onClick = vm::refresh, enabled = state.index?.refreshing != true && !state.aliasSaving,
                shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.insight_refresh))
            }
        })
        LazyColumn(Modifier.weight(1f).widthIn(max = page.wideContentWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = pagePadding, top = spacing.xl, end = pagePadding, bottom = page.navigationBottomPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.l)) {
            if (state.loading || state.error != null) item { InsightReadState(state, vm::refresh) }
            if (data != null) {
                item { InsightCoverage(state) }
                item { InsightRangePicker(state.range, vm::selectRange, enabled = !state.rangeSaving) }
                if (exercise == null) item { FitLogNotice(stringResource(R.string.insight_no_exercise_records)) }
                else {
                    item { Text(stringResource(R.string.insight_exercise_summary,
                        exercise.occurrences.map { it.sessionId }.distinct().size, exercise.occurrences.sumOf { it.exercise.sets.size }),
                        style = MaterialTheme.typography.titleLarge)
                        val unknown = exercise.occurrences.count { it.exercise.sets.isEmpty() }
                        if (unknown > 0) Text(stringResource(R.string.insight_unknown_groups, unknown),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!progress?.comparisons.isNullOrEmpty()) {
                        item { FitLogSectionTitle(stringResource(R.string.insight_performance_title)) }
                        progress.comparisons.forEach { comparison ->
                            item(key = "performance-${comparison.latest.basis}") {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
                                        InsightComparisonContent(comparison)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                                            TextButton(onClick = { onOpen(comparison.latest.recordId) }, shapes = ButtonDefaults.shapes()) {
                                                Text(stringResource(R.string.insight_latest_source))
                                            }
                                            comparison.previous?.let { previous ->
                                                TextButton(onClick = { onOpen(previous.recordId) }, shapes = ButtonDefaults.shapes()) {
                                                    Text(stringResource(R.string.insight_previous_source))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val series = presentation.series
                    if (series.isNotEmpty()) item { FitLogSectionTitle(stringResource(R.string.insight_load_history)) }
                    if (series.isEmpty()) item { FitLogNotice(stringResource(R.string.insight_no_numeric_data)) }
                    series.forEach { curve ->
                        item(key = "curve-${curve.basis}") {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                                    Text(basisLabel(curve.basis), style = MaterialTheme.typography.titleMediumEmphasized)
                                    Text(stringResource(if (curve.basis == WeightBasis.BODYWEIGHT) R.string.insight_metric_reps else R.string.insight_metric_kg),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    InsightChart(curve, onOpen)
                                }
                            }
                        }
                        val record = requireNotNull(curve.record)
                        item(key = "record-${curve.basis}") {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                                Text(stringResource(if (curve.basis == WeightBasis.ASSISTED) R.string.insight_record_lowest else R.string.insight_record_highest,
                                    insightNumber(record.value), insightDate(record.date)), style = MaterialTheme.typography.titleMedium)
                                SourceRows(record.recordIds, presentation.recordTitles, onOpen)
                            }
                        }
                        item(key = "toggle-points-${curve.basis}") {
                            TextButton(onClick = {
                                expandedCurves = if (curve.basis.name in expandedCurves)
                                    expandedCurves - curve.basis.name else expandedCurves + curve.basis.name
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(if (curve.basis.name in expandedCurves) R.string.insight_hide_points else R.string.insight_show_points)) }
                        }
                        if (curve.basis.name in expandedCurves) items(curve.points.reversed(), key = { "${curve.basis}-${it.date}" }) { point ->
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                                Text(stringResource(R.string.insight_point_value, insightDate(point.date), insightNumber(point.value)),
                                    style = FitLogType.data)
                                SourceRows(point.recordIds, presentation.recordTitles, onOpen)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { showRecordedSets = !showRecordedSets }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(if (showRecordedSets) R.string.insight_hide_sets else R.string.insight_show_sets))
                        }
                    }
                    if (showRecordedSets) items(exercise.occurrences, key = { it.exercise.exercise.id }) { occurrence ->
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                            InsightRow(insightDate(occurrence.diary.date), occurrence.diary.title, { onOpen(occurrence.diary.key) })
                            Text(occurrence.exercise.exercise.rawName, style = MaterialTheme.typography.titleSmall)
                            if (occurrence.exercise.sets.isEmpty()) Text(stringResource(R.string.insight_sets_unknown),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            occurrence.exercise.sets.forEachIndexed { index, set ->
                                Text(stringResource(R.string.insight_numbered_set, index + 1, setDescription(set)),
                                    style = FitLogType.data)
                            }
                        }
                    }
                }
            }
        }
    }
    if (editingAliases && data != null) AliasEditor(state, data, nameKey, vm::saveAliases) { editingAliases = false }
}

/** Source records behind one value, grouped as one segmented list. */
@Composable
private fun SourceRows(recordIds: List<String>, titles: Map<String, String>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        recordIds.forEachIndexed { index, key ->
            InsightRow(titles[key].orEmpty(), stringResource(R.string.insight_view_source), { onOpen(key) },
                index = index, count = recordIds.size)
        }
    }
}
