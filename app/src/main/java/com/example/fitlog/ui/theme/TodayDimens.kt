package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Today screen. */
@Immutable
class TodayDimens {
    /** Dumbbell illustration on the training card. */
    val illustrationWidth: Dp = 84.dp
    val illustrationHeight: Dp = 56.dp

    /** The illustration shows only when the content area is at least this tall. */
    val illustrationMinHeight: Dp = 600.dp

    /** At or above this font scale the two-column layout falls back to one column. */
    val singleColumnFontScale: Float = 1.5f

    /** At or above this font scale the write button drops its icon and uses compact padding. */
    val compactButtonFontScale: Float = 1.3f
}
