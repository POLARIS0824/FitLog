@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.insight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.data.analysis.SourceKey
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSectionTitle
import java.time.LocalDate
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogPageHeader

@Composable
fun InsightScreen(vm: InsightViewModel, onOpenLog: () -> Unit, onOpen: (SourceKey) -> Unit,
    onExercise: (String) -> Unit, modifier: Modifier = Modifier) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumed() }
    var sheet by rememberSaveable(vm.data?.vaultId) { mutableStateOf<String?>(null) }
    val data = vm.data.takeIf { vm.error == null && vm.activeVault }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { FitLogPageHeader(stringResource(R.string.insight_title), actions = {
                FilledTonalButton(onClick = vm::refresh, enabled = vm.index?.refreshing != true && !vm.aliasSaving) {
                    Text(stringResource(R.string.insight_refresh))
                }
            }) }
            item { InsightReadState(vm, onOpenLog) }
            if (data != null) {
                item { InsightCoverage(vm) }
                item { InsightRangePicker(vm.range, vm::selectRange) }
                item {
                    val totals = data.totals(vm.range)
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.insight_sessions_value, totals.sessions), style = MaterialTheme.typography.headlineLargeEmphasized)
                            Text(stringResource(R.string.insight_totals, totals.days, totals.sets), style = MaterialTheme.typography.titleMedium)
                            if (totals.unknownGroups > 0) Text(stringResource(R.string.insight_unknown_groups, totals.unknownGroups),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.insight_confirmed_only), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { FitLogSectionTitle(stringResource(R.string.insight_coverage)) }
                items(InsightSourceStatus.entries.filter { status -> data.sources.any { it.status == status } }, key = { "coverage-${it.name}" }) { status ->
                    InsightRow(sourceStatusLabel(status), stringResource(R.string.insight_source_count, data.sources.count { it.status == status }), { sheet = status.name })
                }
                item { FitLogSectionTitle(stringResource(R.string.insight_activity)) }
                item { InsightHeatmap(data) }
                item { FilledTonalButton(onClick = { sheet = "days" }) { Text(stringResource(R.string.insight_view_days)) } }
                item { FitLogSectionTitle(stringResource(R.string.insight_exercises)) }
                val exercises = data.exercises(vm.range)
                if (exercises.isEmpty()) item {
                    FitLogNotice(stringResource(R.string.insight_empty))
                    FilledTonalButton(onClick = onOpenLog) { Text(stringResource(R.string.insight_open_log)) }
                }
                items(exercises, key = { "exercise-${it.nameKey}" }) { exercise ->
                    InsightRow(exercise.name, stringResource(R.string.insight_exercise_summary,
                        exercise.occurrences.map { it.sessionId }.distinct().size,
                        exercise.occurrences.sumOf { it.exercise.sets.size }), { onExercise(exercise.nameKey) })
                }
                item { FitLogSectionTitle(stringResource(R.string.insight_recent)) }
                items(data.diaries(vm.range).filter { it.sessions.isNotEmpty() }.take(6), key = { "recent-${it.key.relPath}" }) { diary ->
                    InsightRow(insightDate(diary.date), stringResource(R.string.insight_diary_summary,
                        diary.key.relPath, diary.sessions.size, diary.setCount), { onOpen(diary.key) })
                }
            }
        }
    }
    if (sheet != null && data != null) {
        InsightSourceSheet(data, requireNotNull(sheet), onDismiss = { sheet = null }, onOpen = { key -> sheet = null; onOpen(key) },
            onOpenLog = { sheet = null; onOpenLog() }, onDay = { day ->
                val diaries = data.activity()[day].orEmpty()
                if (diaries.size == 1) { sheet = null; onOpen(diaries.single().key) } else sheet = day.toString()
            })
    }
}

@Composable
private fun InsightSourceSheet(data: InsightData, selection: String, onDismiss: () -> Unit,
    onOpen: (SourceKey) -> Unit, onOpenLog: () -> Unit, onDay: (LocalDate) -> Unit) {
    val status = InsightSourceStatus.entries.firstOrNull { it.name == selection }
    val day = runCatching { LocalDate.parse(selection) }.getOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(when { status != null -> sourceStatusLabel(status); day != null -> insightDate(day)
                else -> stringResource(R.string.insight_view_days) }, style = MaterialTheme.typography.headlineSmall) }
            when {
                status != null -> items(data.sources.filter { it.status == status }, key = { it.key.relPath }) { source ->
                    InsightRow(source.key.relPath, sourceStatusLabel(source.status),
                        if (source.source?.status == IndexedSource.AVAILABLE) ({ onOpen(source.key) }) else onOpenLog)
                }
                day != null -> items(data.activity()[day].orEmpty(), key = { it.key.relPath }) { diary ->
                    InsightRow(diary.key.relPath, stringResource(R.string.insight_exercise_summary, diary.sessions.size, diary.setCount), { onOpen(diary.key) })
                }
                else -> {
                    val activity = data.activity().toSortedMap(compareByDescending { it })
                    if (activity.isEmpty()) item { Text(stringResource(R.string.insight_empty)) }
                    items(activity.keys.toList(), key = { it.toString() }) { date ->
                        val diaries = activity.getValue(date)
                        InsightRow(insightDate(date), stringResource(R.string.insight_exercise_summary,
                            diaries.sumOf { it.sessions.size }, diaries.sumOf { it.setCount }), { onDay(date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceStatusLabel(status: InsightSourceStatus) = stringResource(when (status) {
    InsightSourceStatus.CURRENT -> R.string.insight_status_current
    InsightSourceStatus.UNCONFIRMED -> R.string.insight_status_unconfirmed
    InsightSourceStatus.STALE -> R.string.insight_status_stale
    InsightSourceStatus.MISSING -> R.string.insight_status_missing
    InsightSourceStatus.UNVERIFIABLE -> R.string.insight_status_unverifiable
    InsightSourceStatus.FUTURE -> R.string.insight_status_future
})
