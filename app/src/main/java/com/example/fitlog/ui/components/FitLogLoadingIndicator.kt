package com.example.fitlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Each operation gets its own delay; completion cancels a pending hint immediately. */
@Composable
internal fun rememberDelayedLoading(loading: Boolean): Boolean {
    var ready by remember(loading) { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(300L)
            ready = true
        }
    }
    return loading && ready
}

/** Place this in a Box overlay, so fading feedback never changes the content's layout. */
@Composable
internal fun FitLogLoadingIndicator(
    loading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { FitLogWavyProgressIndicator(Modifier.fillMaxWidth()) },
) {
    FitLogProgressFeedback(rememberDelayedLoading(loading), modifier, content)
}

/** For independent reads whose delayed visibility also controls their status labels. */
@Composable
internal fun FitLogProgressFeedback(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { FitLogWavyProgressIndicator(Modifier.fillMaxWidth()) },
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
    ) {
        content()
    }
}
