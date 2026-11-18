package com.example.fitlog.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.fitlog.ui.theme.FitLogTheme

/** A shared, indeterminate wave with a gentle accelerate / decelerate / rest rhythm. */
@Composable
internal fun FitLogWavyProgressIndicator(modifier: Modifier = Modifier) {
    val rhythm = FitLogTheme.dimens.components
    val transition = rememberInfiniteTransition(label = "loading wave rhythm")
    val speed by transition.animateFloat(
        initialValue = RestSpeed,
        targetValue = RestSpeed,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = rhythm.loadingWaveCycleMillis
                RestSpeed at 0 using rhythm.loadingWaveEasing
                rhythm.loadingWavePeakSpeed at rhythm.loadingWavePeakMillis using rhythm.loadingWaveEasing
                RestSpeed at rhythm.loadingWaveRestStartMillis
                RestSpeed at rhythm.loadingWaveCycleMillis
            },
        ),
        label = "loading wave speed",
    )
    LinearWavyProgressIndicator(
        modifier = modifier,
        waveSpeed = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength * speed,
    )
}

// Material resets the wave phase at exactly zero. An imperceptible positive speed keeps the
// wave continuous through the rest of the cycle instead of snapping it back to its starting shape.
private const val RestSpeed = 0.001f
