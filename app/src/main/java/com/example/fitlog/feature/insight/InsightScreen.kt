@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.feature.insight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.InsightSourceStatus
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSectionTitle
import com.example.fitlog.ui.components.FitLogSheetActions
import com.example.fitlog.ui.components.FitLogSheetDefaults
import com.example.fitlog.ui.components.FitLogSheetTitle
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import java.time.LocalDate

internal enum class InsightDashboardView { OVERVIEW, WEEKLY }

@Composable
internal fun InsightScreen(vm: InsightViewModel, onOpenLog: () -> Unit, onOpen: (String) -> Unit,
    onExercise: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val weeklyListState = rememberLazyListState()
    var view by rememberSaveable { mutableStateOf(InsightDashboardView.OVERVIEW) }
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
                Column(Modifier.fillMaxWidth().glassPageSource().padding(fitLogPagePadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InsightReadState(state, vm::refresh)
                }
            }
        } else InsightDashboardContent(requireNotNull(presentation), state.range, state.focusedKeys, vm::selectRange,
            onManageFocus = { vm.clearFocusError(); sheet = "focus" }, onExercise, onOpen, onOpenLog,
            onSheet = { sheet = it }, headerActions = { InsightRefreshAction(state, vm::refresh) },
            rangeEnabled = !state.rangeSaving,
            notices = { InsightCoverage(state, showScanTime = false) }, listState = listState,
            view = view, onSelectView = { view = it }, weeklyListState = weeklyListState)
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
    val behavior = rememberFitLogTopAppBarBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        FitLogTopAppBar(stringResource(R.string.insight_title), behavior, editorial = true,
            maxContentWidth = FitLogDimens.ContentWidth, actions = headerActions)
        Box(Modifier.weight(1f).widthIn(max = FitLogDimens.ContentWidth).fillMaxWidth()) { content() }
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
    listState: LazyListState = rememberLazyListState(),
    view: InsightDashboardView = InsightDashboardView.OVERVIEW,
    onSelectView: (InsightDashboardView) -> Unit = {},
    weeklyListState: LazyListState = rememberLazyListState(),
) = InsightPage(headerActions) {
    val viewPicker: @Composable () -> Unit = {
        FitLogConnectedButtonGroup(InsightDashboardView.entries, view,
            label = { stringResource(if (it == InsightDashboardView.OVERVIEW) R.string.weekly_overview_tab else R.string.weekly_tab) },
            onSelect = onSelectView, prominentSelection = true)
    }
    if (view == InsightDashboardView.WEEKLY) {
        InsightWeeklyContent(presentation.weekly, onExercise, onOpenLog, onViewDays = { onSheet("weekly-days") },
            viewPicker = viewPicker, notices = notices, listState = weeklyListState)
        return@InsightPage
    }
    val pagePadding = fitLogPagePadding()
    val data = presentation.data
    val overview = presentation.overview
    val focused = presentation.focused
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().glassPageSource(),
        contentPadding = PaddingValues(start = pagePadding, top = 0.dp, end = pagePadding, bottom = FitLogDimens.NavigationBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "view") { viewPicker() }
        item(key = "range") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightRangePicker(range, onSelectRange, enabled = rangeEnabled)
                notices()
            }
        }
        item(key = "glance") { InsightAtAGlance(presentation, onOpenLog) }
        item(key = "consistency") { InsightConsistency(presentation) { onSheet("days") } }
        item(key = "ranking") { InsightExerciseRanking(presentation.dashboard, onExercise) { onSheet("exercises") } }
        item(key = "balance") { InsightTrainingBalance(presentation.dashboard) }
        item(key = "matrix") { InsightActivityMatrix(presentation.dashboard, data.today) { onSheet("days") } }
        if (data.diaries.isNotEmpty()) item(key = "focus") {
            InsightDashboardCard(stringResource(R.string.insight_focus_title),
                stringResource(if (focusedKeys == null) R.string.insight_focus_automatic else R.string.insight_focus_selected)) {
                FilledTonalButton(onClick = onManageFocus, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.insight_focus_choose))
                }
                if (focused.isEmpty()) Text(stringResource(R.string.insight_focus_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                focused.forEach { progress -> InsightProgressCard(progress) { onExercise(progress.exercise.nameKey) } }
            }
        }
        if (data.diaries.isNotEmpty()) item(key = "records") {
            InsightDashboardCard(stringResource(R.string.insight_recent_records)) {
                if (overview.achievements.isEmpty()) Text(stringResource(R.string.insight_records_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val shown = overview.achievements.take(3)
                    shown.forEachIndexed { index, achievement ->
                        InsightAchievementRow(achievement, index, shown.size) { onOpen(achievement.performance.recordId) }
                    }
                    if (overview.achievements.size > shown.size) TextButton(onClick = { onSheet("records") },
                        shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.insight_all_records)) }
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
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = FitLogSheetDefaults.ContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FitLogSheetTitle(when { selection == "sources" -> stringResource(R.string.insight_coverage)
                selection == "exercises" -> stringResource(R.string.insight_exercises)
                selection == "records" -> stringResource(R.string.insight_recent_records)
                status != null -> sourceStatusLabel(status); day != null -> insightDate(day)
                else -> stringResource(R.string.insight_view_days) }) }
            when {
                selection == "weekly-days" -> {
                    val days = presentation.weekly.days.filter { it.sessions > 0 }
                    if (days.isEmpty()) item { Text(stringResource(R.string.weekly_no_records)) }
                    items(days, key = { it.date.toString() }) { entry ->
                        InsightRow(insightDate(entry.date), stringResource(R.string.insight_exercise_summary,
                            entry.sessions, entry.sets), { onDay(entry.date) })
                    }
                }
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
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding().padding(FitLogSheetDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FitLogSheetTitle(stringResource(R.string.insight_focus_title))
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
            FitLogSheetActions(onDismiss, onConfirm = { onSave(chosen.toSet()) },
                enabled = !state.focusSaving, dismissEnabled = !state.focusSaving,
                confirmLabel = stringResource(R.string.insight_focus_save))
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
