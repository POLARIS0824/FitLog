@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.feature.insight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.model.record.ExerciseAliasGroup
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSectionTitle
import com.example.fitlog.ui.components.FitLogSheetActions
import com.example.fitlog.ui.components.FitLogSheetDefaults
import com.example.fitlog.ui.components.FitLogSheetTitle
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior

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
        LazyColumn(Modifier.weight(1f).widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = pagePadding, top = 24.dp, end = pagePadding, bottom = FitLogDimens.NavigationBottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        if (unknown > 0) Text(stringResource(R.string.insight_unknown_groups, unknown))
                    }
                    if (!progress?.comparisons.isNullOrEmpty()) {
                        item { FitLogSectionTitle(stringResource(R.string.insight_performance_title)) }
                        progress.comparisons.forEach { comparison ->
                            item(key = "performance-${comparison.latest.basis}") {
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        InsightComparisonContent(comparison)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(basisLabel(curve.basis), style = MaterialTheme.typography.titleMediumEmphasized)
                                    Text(stringResource(if (curve.basis == WeightBasis.BODYWEIGHT) R.string.insight_metric_reps else R.string.insight_metric_kg),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    InsightChart(curve, onOpen)
                                }
                            }
                        }
                        val record = requireNotNull(curve.record)
                        item(key = "record-${curve.basis}") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(if (curve.basis == WeightBasis.ASSISTED) R.string.insight_record_lowest else R.string.insight_record_highest,
                                    insightNumber(record.value), insightDate(record.date)), style = MaterialTheme.typography.titleMedium)
                                record.recordIds.forEach { key ->
                                    InsightRow(presentation.recordTitles[key].orEmpty(), stringResource(R.string.insight_view_source), { onOpen(key) })
                                }
                            }
                        }
                        item(key = "toggle-points-${curve.basis}") {
                            TextButton(onClick = {
                                expandedCurves = if (curve.basis.name in expandedCurves)
                                    expandedCurves - curve.basis.name else expandedCurves + curve.basis.name
                            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(if (curve.basis.name in expandedCurves) R.string.insight_hide_points else R.string.insight_show_points)) }
                        }
                        if (curve.basis.name in expandedCurves) items(curve.points.reversed(), key = { "${curve.basis}-${it.date}" }) { point ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.insight_point_value, insightDate(point.date), insightNumber(point.value)),
                                    style = MaterialTheme.typography.bodyMedium)
                                point.recordIds.forEach { key ->
                                    InsightRow(presentation.recordTitles[key].orEmpty(), stringResource(R.string.insight_view_source), { onOpen(key) })
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { showRecordedSets = !showRecordedSets }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(if (showRecordedSets) R.string.insight_hide_sets else R.string.insight_show_sets))
                        }
                    }
                    if (showRecordedSets) items(exercise.occurrences, key = { it.exercise.exercise.id }) { occurrence ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightRow(insightDate(occurrence.diary.date), occurrence.diary.title, { onOpen(occurrence.diary.key) })
                            Text(occurrence.exercise.exercise.rawName, style = MaterialTheme.typography.titleSmall)
                            if (occurrence.exercise.sets.isEmpty()) Text(stringResource(R.string.insight_sets_unknown))
                            occurrence.exercise.sets.forEachIndexed { index, set ->
                                Text(stringResource(R.string.insight_numbered_set, index + 1, setDescription(set)),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if (editingAliases && data != null) AliasEditor(state, data, nameKey, vm::saveAliases) { editingAliases = false }
}

@Composable
private fun AliasEditor(state: InsightUiState, data: InsightData, nameKey: String,
    onSave: (String?, ExerciseAliasGroup?) -> Unit, onDismiss: () -> Unit) {
    val existing = data.aliases.groupFor(nameKey)
    var chosen by rememberSaveable(nameKey) { mutableStateOf(existing?.members ?: listOf(nameKey)) }
    var primary by rememberSaveable(nameKey) { mutableStateOf(existing?.primaryKey ?: nameKey) }
    val names = data.names + mapOf(nameKey to (data.names[nameKey] ?: nameKey))
    val options = names.entries.sortedBy { it.value }
    val dismiss = { if (!state.aliasSaving) onDismiss() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { !state.aliasSaving || it != SheetValue.Hidden })) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = FitLogSheetDefaults.ContentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { FitLogSheetTitle(stringResource(R.string.insight_manage_aliases))
                    Text(stringResource(R.string.insight_alias_help), style = MaterialTheme.typography.bodyMedium) }
                itemsIndexed(options, key = { _, entry -> "member-${entry.key}" }) { index, (key, name) ->
                    val owner = data.aliases.groupFor(key)
                    val occupied = owner != null && owner.primaryKey != existing?.primaryKey
                    SegmentedListItem(checked = key in chosen, shapes = ListItemDefaults.segmentedShapes(index, options.size), onCheckedChange = { checked ->
                        val changed = if (checked) chosen + key else chosen - key
                        chosen = changed
                        if (primary !in changed) primary = changed.first()
                    }, enabled = !state.aliasSaving && !occupied && !(chosen.size == 1 && key in chosen),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        content = { Text(name) }, supportingContent = {
                            if (occupied) Text(stringResource(R.string.insight_alias_owned, requireNotNull(owner).primaryName))
                        }, trailingContent = { Checkbox(key in chosen, onCheckedChange = null, enabled = !occupied && !state.aliasSaving) })
                }
                item { FitLogSectionTitle(stringResource(R.string.insight_alias_primary)) }
                itemsIndexed(chosen.toList(), key = { _, key -> "primary-$key" }) { index, key ->
                    SegmentedListItem(onClick = { primary = key }, enabled = !state.aliasSaving,
                        shapes = ListItemDefaults.segmentedShapes(index, chosen.size),
                        modifier = Modifier.semantics { role = Role.RadioButton; selected = primary == key },
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        content = { Text(names[key] ?: key) },
                        trailingContent = { RadioButton(primary == key, onClick = null, enabled = !state.aliasSaving) })
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = FitLogDimens.SheetPadding).padding(top = 12.dp, bottom = FitLogDimens.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.aliasError?.let { FitLogNotice(stringResource(it), error = true) }
                FitLogSheetActions(dismiss, onConfirm = { onSave(existing?.primaryKey,
                    ExerciseAliasGroup(names[primary] ?: primary, chosen.sorted())) },
                    enabled = !state.aliasSaving, dismissEnabled = !state.aliasSaving,
                    confirmLabel = stringResource(R.string.insight_alias_save))
                if (existing != null) TextButton(onClick = { onSave(existing.primaryKey, null) }, enabled = !state.aliasSaving, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.insight_alias_dissolve))
                }
            }
        }
    }
}
