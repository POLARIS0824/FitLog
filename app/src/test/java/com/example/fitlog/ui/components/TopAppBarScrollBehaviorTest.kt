package com.example.fitlog.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class TopAppBarScrollBehaviorTest {

    @Test
    fun uninitializedHeightOffsetLimitAbsorbsAllNegativeScroll() {
        // Reproduces the root cause of the scrolling bug:
        // When a scroll behavior is created without being bound to a Material 3 TopAppBar,
        // heightOffsetLimit defaults to -Float.MAX_VALUE.
        val state = TopAppBarState(
            initialHeightOffsetLimit = -Float.MAX_VALUE,
            initialHeightOffset = 0f,
            initialContentOffset = 0f,
        )

        val scrollDeltaY = -50f
        val prevHeightOffset = state.heightOffset
        state.heightOffset = (state.heightOffset + scrollDeltaY).coerceIn(state.heightOffsetLimit, 0f)

        val consumedY = if (prevHeightOffset != state.heightOffset) scrollDeltaY else 0f
        // Demonstrates the bug: all -50f is consumed in preScroll, starving child scrollable views
        assertEquals(-50f, consumedY, 0.001f)
    }

    @Test
    fun pinnedHeightOffsetLimitDoesNotAbsorbScroll() {
        // With pinned behavior or clamped limit at 0f, heightOffset cannot change
        val state = TopAppBarState(
            initialHeightOffsetLimit = 0f,
            initialHeightOffset = 0f,
            initialContentOffset = 0f,
        )

        val scrollDeltaY = -50f
        val prevHeightOffset = state.heightOffset
        state.heightOffset = (state.heightOffset + scrollDeltaY).coerceIn(state.heightOffsetLimit, 0f)

        val consumedY = if (prevHeightOffset != state.heightOffset) scrollDeltaY else 0f
        // Pinned behavior consumes 0f, allowing child scrollable views to receive the full scroll
        assertEquals(0f, consumedY, 0.001f)
    }
}
