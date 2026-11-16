package com.example.fitlog.feature.log.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogConnectedButtonGroup
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior

@Composable
internal fun RecordsLogHeader(markdown: Boolean, refreshing: Boolean, onMarkdown: (Boolean) -> Unit,
    onRefresh: () -> Unit, onConnect: () -> Unit, onManage: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior = rememberFitLogTopAppBarBehavior()) {
    LogTopAppBar(markdown, refreshing, onRefresh, onConnect, onManage, scrollBehavior)
    RecordSourceSelector(markdown, onMarkdown)
}

@Composable
private fun RecordSourceSelector(markdown: Boolean, onMarkdown: (Boolean) -> Unit) {
    FitLogConnectedButtonGroup(listOf(false, true), markdown,
        label = { stringResource(if (it) R.string.record_markdown else R.string.record_native) },
        onSelect = onMarkdown,
        modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth)
            .padding(horizontal = fitLogPagePadding(), vertical = 8.dp))
}
