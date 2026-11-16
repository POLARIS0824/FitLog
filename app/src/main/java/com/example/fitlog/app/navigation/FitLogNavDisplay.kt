package com.example.fitlog.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalReduceMotion
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.drawGlassBackground
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/** Shared rendering and lifecycle boundary for every app destination. */
@Composable
internal fun FitLogNavDisplay(navigationState: FitLogNavigationState, onBack: () -> Unit,
    entryProvider: (NavKey, NavKey) -> NavEntry<NavKey>, modifier: Modifier = Modifier) {
    val look = LocalUiLook.current
    val surface = remember(look) {
        NavEntryDecorator<NavKey> { entry ->
            val isLiquid = look == UiLook.LIQUID_GLASS
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquid) Modifier.drawBehind { drawGlassBackground(dark) }
                        else Modifier
                    ),
                color = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surface
            ) { entry.Content() }
        }
    }
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current == true
    // NavDisplay decorates entry keys, so tab changes are tracked from the state they come from.
    val lastTab = remember { mutableStateOf(navigationState.currentTab) }
    val switchingTabs = lastTab.value != navigationState.currentTab
    SideEffect { lastTab.value = navigationState.currentTab }
    // Top-level destinations fade; lateral motion belongs to the deeper hierarchy.
    val fade = ContentTransform(
        targetContentEnter = fadeIn(animationSpec = motion.defaultEffectsSpec()),
        initialContentExit = fadeOut(animationSpec = motion.defaultEffectsSpec()),
    )
    val forward = ContentTransform(
        targetContentEnter = slideInHorizontally(animationSpec = motion.slowSpatialSpec()) { it },
        initialContentExit = slideOutHorizontally(animationSpec = motion.slowSpatialSpec()) { -it / 3 },
        targetContentZIndex = 1f,
    )
    val backward = ContentTransform(
        targetContentEnter = slideInHorizontally(animationSpec = motion.slowSpatialSpec()) { -it / 3 },
        initialContentExit = slideOutHorizontally(animationSpec = motion.slowSpatialSpec()) { it },
        targetContentZIndex = -1f,
    )

    // Retain decorators for inactive stacks as well as the currently displayed entries.
    val stacks = navigationState.backStacks.mapValues { (root, stack) ->
        rememberDecoratedNavEntries(backStack = stack, entryProvider = { key -> scopedNavEntry(root, key, entryProvider(root, key)) },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator(), surface))
    }
    val activeRoots = if (navigationState.currentTab == navigationState.startTab) listOf(navigationState.startTab)
        else listOf(navigationState.startTab, navigationState.currentTab)
    NavDisplay(
        entries = activeRoots.flatMap { stacks.getValue(it) },
        modifier = modifier,
        onBack = onBack,
        transitionSpec = {
            if (reduceMotion || switchingTabs) fade else forward
        },
        popTransitionSpec = {
            if (reduceMotion || switchingTabs) fade else backward
        },
        predictivePopTransitionSpec = {
            if (reduceMotion) fade else backward
        },
    )
}

/** A common destination can occur in different tabs without sharing its entry storage. */
internal fun scopedNavEntry(root: NavKey, key: NavKey, entry: NavEntry<NavKey>) = NavEntry(
    key = key, contentKey = "$root:${entry.contentKey}", metadata = entry.metadata,
) { entry.Content() }
