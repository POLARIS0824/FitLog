package com.example.fitlog.feature.log

import com.example.fitlog.feature.log.LogActions
import com.example.fitlog.feature.log.LogViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.source.MarkdownFile
import com.example.fitlog.feature.log.components.LogContent

@Composable
internal fun MarkdownLogContent(
    vm: LogViewModel,
    onOpen: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    onReview: () -> Unit = {},
    onAiSettings: () -> Unit = {},
    showAppBar: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {

    val state by vm.uiState.collectAsStateWithLifecycle()
    LogContent(
        state = state,
        actions = LogActions(
            refresh = { vm.refresh() },
            changeSort = { vm.changeSort(it) },
            toggleMissing = { vm.toggleMissing() },
            filter = vm::filter, startAnalysis = vm::startAnalysis, cancelAnalysis = vm::cancelAnalysis,
            openAiSettings = onAiSettings,
        ),
        onOpen = onOpen, onConnect = onConnect,
        onManage = onManage, modifier = modifier, onReview = onReview, showAppBar = showAppBar, listState = listState,
        searchFieldState = vm.searchFieldState,
    )
}
