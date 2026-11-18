package com.example.fitlog.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared components in ui/components. */
@Immutable
class ComponentDimens {
    /** Leading icon container of a settings entry; matches the 48dp touch target. */
    val settingsEntryIconContainerSize: Dp = 48.dp

    /** Wait before a loading hint appears, so fast operations never flash feedback. */
    val loadingHintDelayMillis: Long = 300L

    /**
     * Rhythm of the shared indeterminate wave. An infinite repeat needs a duration-based spec, so
     * this is the one place motion uses keyframes instead of `MaterialTheme.motionScheme` springs.
     */
    val loadingWaveCycleMillis: Int = 2000
    val loadingWavePeakMillis: Int = 650
    val loadingWaveRestStartMillis: Int = 1600
    val loadingWavePeakSpeed: Float = 1.6f
    val loadingWaveEasing: Easing = FastOutSlowInEasing
}
