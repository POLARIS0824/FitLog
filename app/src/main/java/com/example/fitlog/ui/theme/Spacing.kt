package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The only spacing scale (Material's 4dp-based layout grid). Padding, gaps and
 * `Arrangement.spacedBy` read these; values outside the scale belong in [FitLogDimens]
 * under a component name.
 */
@Immutable
class FitLogSpacing {
    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

internal val LocalFitLogSpacing = staticCompositionLocalOf { FitLogSpacing() }
