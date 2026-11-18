@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.feature.insight

import com.example.fitlog.feature.insight.components.InsightDashboardContent
import com.example.fitlog.feature.insight.components.InsightPage
import com.example.fitlog.feature.insight.components.InsightSourceSheet
import com.example.fitlog.feature.insight.components.InsightFocusSheet
import com.example.fitlog.feature.insight.components.InsightCoverage
import com.example.fitlog.feature.insight.components.InsightReadState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.R
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogPagePadding

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
                Column(Modifier.fillMaxWidth().glassPageSource().padding(fitLogPagePadding()), verticalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.l)) {
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
private fun InsightRefreshAction(state: InsightUiState, onRefresh: () -> Unit) {
    FilledTonalIconButton(onClick = onRefresh,
        enabled = state.index?.refreshing != true && !state.aliasSaving && !state.focusSaving,
        shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.insight_refresh))
    }
}
