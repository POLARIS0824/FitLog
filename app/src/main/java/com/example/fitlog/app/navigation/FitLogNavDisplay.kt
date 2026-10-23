package com.example.fitlog.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import kotlin.math.roundToInt

/** Shared rendering and lifecycle boundary for every app destination. */
@Composable
internal fun FitLogNavDisplay(backStack: NavBackStack<NavKey>, onBack: () -> Unit,
    entryProvider: (NavKey) -> NavEntry<NavKey>, modifier: Modifier = Modifier) {
    val surface = remember {
        NavEntryDecorator<NavKey> { entry -> Surface(Modifier.fillMaxSize()) { entry.Content() } }
    }
    val motion = MaterialTheme.motionScheme
    val previewMargin = with(LocalDensity.current) { 8.dp.toPx() }
    NavDisplay(backStack = backStack, modifier = modifier, onBack = onBack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator(), surface),
        predictivePopTransitionSpec = { swipeEdge ->
            // Navigation3's default scales to 70% around the center, ignoring the swipe edge.
            // Use Android's 90% preview with an 8dp far-edge margin and physical (not RTL) direction.
            val direction = when (swipeEdge) {
                NavigationEvent.EDGE_LEFT -> 1
                NavigationEvent.EDGE_RIGHT -> -1
                else -> 0
            }
            // DeferredAnimatedContent holds incoming content at its initial values during the gesture.
            // Keep an explicit opaque alpha track: None leaves the previous scene invisible here.
            ContentTransform(fadeIn(initialAlpha = 1f, animationSpec = motion.defaultEffectsSpec()),
                scaleOut(targetScale = .9f, animationSpec = motion.slowSpatialSpec()) +
                    slideOutHorizontally(animationSpec = motion.slowSpatialSpec()) { width ->
                        (direction * (width * .05f - previewMargin).coerceAtLeast(0f)).roundToInt()
                    })
        },
        entryProvider = entryProvider)
}
