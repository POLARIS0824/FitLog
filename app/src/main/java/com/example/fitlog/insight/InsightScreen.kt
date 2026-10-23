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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSectionTitle
import java.time.LocalDate
import com.example.fitlog.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun InsightScreen(vm: InsightViewModel, onOpenLog: () -> Unit, onOpen: (String) -> Unit,
    onExercise: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumed() }
    var sheet by rememberSaveable(state.data?.vaultId) { mutableStateOf<String?>(null) }
    val presentation = state.presentation.takeIf { state.error == null && !state.loading }
    val data = presentation?.data
    LaunchedEffect(state.focusSaved) {
        if (state.focusSaved) { vm.focusSaveHandled(); sheet = null }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (data == null) {
            InsightPage(headerActions = { InsightRefreshAction(state, vm::refresh) }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InsightReadState(state, vm::refresh)
                }
            }
        } else InsightDashboardContent(requireNotNull(presentation), state.range, state.focusedKeys, vm::selectRange,
            onManageFocus = { vm.clearFocusError(); sheet = "focus" }, onExercise, onOpen, onOpenLog,
            onSheet = { sheet = it }, headerActions = { InsightRefreshAction(state, vm::refresh) },
            rangeEnabled = !state.rangeSaving,
            notices = { InsightCoverage(state, showScanTime = false) })
    }
    if (sheet == "focus" && data != null) {
        InsightFocusSheet(state, requireNotNull(presentation), vm::saveFocus, onDismiss = { sheet = null })
    } else if (sheet != null && data != null) {
        InsightSourceSheet(requireNotNull(presentation), requireNotNull(sheet), onDismiss = { sheet = null },
            onSelect = { sheet = it }, onExercise = { key -> sheet = null; onExercise(key) },
            coverage = { InsightCoverage(state) }, onOpen = { key -> sheet = null; onOpen(key) },
            onOpenLog = { sheet = null; onOpenLog() }, onDay = { day ->
                val diaries = presentation.activity[day].orEmpty()
                if (diaries.size == 1) { sheet = null; onOpen(diaries.single().key) } else sheet = day.toString()
            })
    }
}

@Composable
private fun InsightPage(
    headerActions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.insight_title), style = MaterialTheme.typography.headlineLargeEmphasized,
                    modifier = Modifier.semantics { heading() })
            },
            actions = headerActions,
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun InsightRefreshAction(state: InsightUiState, onRefresh: () -> Unit) {
    FilledTonalIconButton(onClick = onRefresh,
        enabled = state.index?.refreshing != true && !state.aliasSaving && !state.focusSaving,
        shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.insight_refresh))
    }
}

/** Shared by the live page and its previews so review fixtures exercise the real layout. */
@Composable
internal fun InsightDashboardContent(
    presentation: InsightPresentation,
    range: InsightRange,
    focusedKeys: Set<String>? = null,
    onSelectRange: (InsightRange) -> Unit = {},
    onManageFocus: () -> Unit = {},
    onExercise: (String) -> Unit = {},
    onOpen: (String) -> Unit = {},
    onOpenLog: () -> Unit = {},
    onSheet: (String) -> Unit = {},
    headerActions: @Composable RowScope.() -> Unit = {},
    rangeEnabled: Boolean = true,
    notices: @Composable () -> Unit = {},
) = InsightPage(headerActions) {
    val data = presentation.data
    val overview = presentation.overview
    val focused = presentation.focused
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { notices() }
        item { InsightRangePicker(range, onSelectRange, enabled = rangeEnabled) }
        item { InsightSummary(overview) }
        if (overview.totals.sessions == 0) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.insight_empty_training), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.insight_empty_training_hint), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onOpenLog, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.insight_open_log))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { FitLogSectionTitle(stringResource(R.string.insight_focus_title)) }
                FilledTonalButton(onClick = onManageFocus, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.insight_focus_choose))
                }
            }
            Text(stringResource(if (focusedKeys == null) R.string.insight_focus_automatic else R.string.insight_focus_selected),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (focused.isEmpty()) item {
            Text(stringResource(R.string.insight_focus_empty), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(focused, key = { "focus-${it.exercise.nameKey}" }) { progress ->
            InsightProgressCard(progress) { onExercise(progress.exercise.nameKey) }
        }
        item { FitLogSectionTitle(stringResource(R.string.insight_recent_records)) }
        if (overview.achievements.isEmpty()) item {
            Text(stringResource(R.string.insight_records_empty), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else item {
            val shown = overview.achievements.take(3)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                shown.forEachIndexed { index, achievement ->
                    InsightAchievementRow(achievement, index, shown.size) { onOpen(achievement.performance.recordId) }
                }
                if (overview.achievements.size > shown.size) FilledTonalButton(onClick = { onSheet("records") },
                    modifier = Modifier.padding(top = 12.dp), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.insight_all_records))
                }
            }
        }
        item { FitLogSectionTitle(stringResource(R.string.insight_weekly_title)) }
        item { InsightWeeklyTrends(overview.weeks) }
        if (overview.exercises.isNotEmpty()) {
            item { FitLogSectionTitle(stringResource(R.string.insight_distribution_title)) }
            item { InsightDistribution(overview.exercises, onExercise) { onSheet("exercises") } }
        }
        item { FitLogSectionTitle(stringResource(R.string.insight_activity)) }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InsightHeatmap(data, presentation.activity)
                    FilledTonalButton(onClick = { onSheet("days") }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.insight_view_days))
                    }
                }
            }
        }
        val recent = presentation.recent
        if (recent.isNotEmpty()) item { FitLogSectionTitle(stringResource(R.string.insight_recent)) }
        items(recent, key = { "recent-${it.key}" }) { diary ->
            InsightRow(insightDate(diary.date), stringResource(R.string.insight_diary_summary,
                diary.title, diary.sessions.size, diary.setCount), { onOpen(diary.key) })
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.record_coverage), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (data.sources.isNotEmpty()) InsightRow(stringResource(R.string.insight_sources_title),
                    stringResource(R.string.insight_sources_summary,
                        data.sources.count { it.status == InsightSourceStatus.CURRENT }, data.sources.size), { onSheet("sources") })
            }
        }
    }
}

@Composable
private fun InsightSourceSheet(presentation: InsightPresentation, selection: String, onDismiss: () -> Unit,
    onSelect: (String) -> Unit, onExercise: (String) -> Unit, coverage: @Composable () -> Unit,
    onOpen: (String) -> Unit, onOpenLog: () -> Unit, onDay: (LocalDate) -> Unit) {
    val data = presentation.data
    val status = InsightSourceStatus.entries.firstOrNull { it.name == selection }
    val day = runCatching { LocalDate.parse(selection) }.getOrNull()
    val achievements = presentation.overview.achievements
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(when { selection == "sources" -> stringResource(R.string.insight_coverage)
                selection == "exercises" -> stringResource(R.string.insight_exercises)
                selection == "records" -> stringResource(R.string.insight_recent_records)
                status != null -> sourceStatusLabel(status); day != null -> insightDate(day)
                else -> stringResource(R.string.insight_view_days) }, style = MaterialTheme.typography.headlineSmall) }
            when {
                selection == "sources" -> {
                    item { coverage() }
                    items(InsightSourceStatus.entries.filter { status -> data.sources.any { it.status == status } }) { sourceStatus ->
                        InsightRow(sourceStatusLabel(sourceStatus),
                            stringResource(R.string.insight_source_count, data.sources.count { it.status == sourceStatus }),
                            { onSelect(sourceStatus.name) })
                    }
                }
                selection == "exercises" -> items(presentation.exercises, key = { it.nameKey }) { exercise ->
                    InsightRow(exercise.name, stringResource(R.string.insight_exercise_summary,
                        exercise.occurrences.map { it.sessionId }.distinct().size,
                        exercise.occurrences.sumOf { it.exercise.sets.size }), { onExercise(exercise.nameKey) })
                }
                selection == "records" -> {
                    itemsIndexed(achievements) { index, achievement ->
                        InsightAchievementRow(achievement, index, achievements.size) { onOpen(achievement.performance.recordId) }
                    }
                }
                status != null -> items(data.sources.filter { it.status == status }, key = { it.key }) { source ->
                    InsightRow(source.key.relPath, sourceStatusLabel(source.status),
                        data.diaries.firstOrNull { it.sourceKey == source.key }?.let { diary -> ({ onOpen(diary.key) }) } ?: onOpenLog)
                }
                day != null -> items(presentation.activity[day].orEmpty(), key = { it.key }) { diary ->
                    InsightRow(diary.title, stringResource(R.string.insight_exercise_summary, diary.sessions.size, diary.setCount), { onOpen(diary.key) })
                }
                else -> {
                    val activity = presentation.activity
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
private fun InsightFocusSheet(state: InsightUiState, presentation: InsightPresentation,
    onSave: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val data = presentation.data
    val options = presentation.focusOptions
    var chosen by rememberSaveable(data.vaultId) {
        mutableStateOf(presentation.focused.map { it.exercise.nameKey })
    }
    ModalBottomSheet(onDismissRequest = { if (!state.focusSaving) onDismiss() },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.insight_focus_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(stringResource(R.string.insight_focus_help), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                if (options.isEmpty()) item { Text(stringResource(R.string.insight_focus_no_options)) }
                itemsIndexed(options, key = { _, exercise -> exercise.nameKey }) { index, exercise ->
                    val checked = exercise.nameKey in chosen
                    SegmentedListItem(checked = checked,
                        onCheckedChange = { selected ->
                            chosen = if (selected) chosen + exercise.nameKey else chosen - exercise.nameKey
                        }, enabled = !state.focusSaving && (checked || chosen.size < 3),
                        shapes = ListItemDefaults.segmentedShapes(index, options.size),
                        content = { Text(exercise.name) },
                        trailingContent = { Checkbox(checked, onCheckedChange = null,
                            enabled = !state.focusSaving && (checked || chosen.size < 3)) })
                }
            }
            state.focusError?.let { FitLogNotice(stringResource(it), error = true) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSave(chosen.toSet()) }, enabled = !state.focusSaving,
                    shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_focus_save)) }
                TextButton(onClick = onDismiss, enabled = !state.focusSaving) { Text(stringResource(R.string.insight_alias_cancel)) }
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
