@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.insight

import com.example.fitlog.domain.insight.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.analysis.WeightBasis
import com.example.fitlog.data.insight.ExerciseAliasGroup
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogPageHeader
import com.example.fitlog.ui.components.FitLogSectionTitle

@Composable
internal fun ExerciseHistoryScreen(vm: InsightViewModel, nameKey: String, onOpen: (String) -> Unit,
    onOpenLog: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumed() }
    var editingAliases by rememberSaveable { mutableStateOf(false) }
    val data = state.data.takeIf { state.error == null }
    val presentation = state.presentation
    val exercise = presentation?.exercise
    val progress = presentation?.progress
    LaunchedEffect(state.aliasSaved) {
        if (state.aliasSaved) { vm.aliasSaveHandled(); editingAliases = false }
    }
    val title = exercise?.name ?: data?.aliases?.groupFor(nameKey)?.primaryName ?: data?.names?.get(nameKey)
        ?: stringResource(R.string.insight_exercise_history)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { FitLogPageHeader(title, onBack = onBack, actions = {
                FilledTonalIconButton(onClick = vm::refresh, enabled = state.index?.refreshing != true && !state.aliasSaving,
                    shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.insight_refresh))
                }
            }) }
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
                                            TextButton(onClick = { onOpen(comparison.latest.recordId) }) {
                                                Text(stringResource(R.string.insight_latest_source))
                                            }
                                            comparison.previous?.let { previous ->
                                                TextButton(onClick = { onOpen(previous.recordId) }) {
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
                                    InsightChart(curve)
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
                        items(curve.points.reversed(), key = { "${curve.basis}-${it.date}" }) { point ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.insight_point_value, insightDate(point.date), insightNumber(point.value)),
                                    style = MaterialTheme.typography.bodyMedium)
                                point.recordIds.forEach { key ->
                                    InsightRow(presentation.recordTitles[key].orEmpty(), stringResource(R.string.insight_view_source), { onOpen(key) })
                                }
                            }
                        }
                    }
                    item { FitLogSectionTitle(stringResource(R.string.insight_recorded_sets)) }
                    items(exercise.occurrences, key = { it.exercise.exercise.id }) { occurrence ->
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
                item { OutlinedButton(onClick = { vm.clearAliasError(); editingAliases = true }) {
                    Text(stringResource(R.string.insight_manage_aliases))
                } }
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
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.insight_manage_aliases), style = MaterialTheme.typography.headlineSmall)
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
            item {
                state.aliasError?.let { FitLogNotice(stringResource(it), error = true) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(existing?.primaryKey,
                        ExerciseAliasGroup(names[primary] ?: primary, chosen.sorted())) }, enabled = !state.aliasSaving) {
                        Text(stringResource(R.string.insight_alias_save))
                    }
                    OutlinedButton(onClick = dismiss, enabled = !state.aliasSaving) { Text(stringResource(R.string.insight_alias_cancel)) }
                }
                if (existing != null) TextButton(onClick = { onSave(existing.primaryKey, null) }, enabled = !state.aliasSaving) {
                    Text(stringResource(R.string.insight_alias_dissolve))
                }
            }
        }
    }
}
