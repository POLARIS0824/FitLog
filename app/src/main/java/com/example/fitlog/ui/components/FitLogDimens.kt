package com.example.fitlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

internal object FitLogDimens {
    val CompactPadding = 16.dp
    val ExpandedPadding = 24.dp
    val ContentWidth = 640.dp
    val WideContentWidth = 840.dp
    val NavigationBottomPadding = 100.dp
    val TodayBottomPadding = 120.dp
    val SheetPadding = 24.dp
}

@Composable
internal fun fitLogPagePadding(): androidx.compose.ui.unit.Dp {
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(LocalDensity.current) { windowSize.width.toDp() }
    return if (windowWidth < 600.dp) FitLogDimens.CompactPadding else FitLogDimens.ExpandedPadding
}
