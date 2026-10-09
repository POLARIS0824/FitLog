package com.example.fitlog.log

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
import com.example.fitlog.data.index.IndexedScan
import com.example.fitlog.ui.preview.PreviewDiary
import com.example.fitlog.ui.theme.FitLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LogLoadingTest {
    @get:Rule val compose = createComposeRule()
    private fun indicators(count: Int) = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(count)

    private fun render() = mutableStateOf(LogUiState(
        vault = PreviewDiary.VAULT_URI, files = listOf(PreviewDiary.file),
        visibleFiles = listOf(PreviewDiary.file), scanStatus = IndexedScan.COMPLETE,
    )).also { state ->
        compose.mainClock.autoAdvance = false
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            LogContent(state.value, LogActions(), { _, _ -> }, {}, {}, {}, {})
        } } }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun update(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun fastRefreshShowsNoAnimationOrTransientScanningLabel() {
        val state = render()
        val file = compose.onNodeWithText(PreviewDiary.file.name)
        val before = file.fetchSemanticsNode().boundsInRoot
        update { state.value = state.value.copy(refreshing = true) }
        compose.mainClock.advanceTimeBy(250)
        indicators(0)
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.index_scanning)).assertDoesNotExist()
        assertEquals(before, file.fetchSemanticsNode().boundsInRoot)
        update { state.value = state.value.copy(refreshing = false) }
        compose.mainClock.advanceTimeBy(600)
        indicators(0)
        assertEquals(before, file.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun slowRefreshExpandsTheCachedHintAndCollapsesItOnCompletion() {
        val state = render()
        val file = compose.onNodeWithText(PreviewDiary.file.name)
        val cached = compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.index_cached))
        val before = file.fetchSemanticsNode().boundsInRoot
        cached.assertDoesNotExist()
        update { state.value = state.value.copy(refreshing = true) }
        compose.mainClock.advanceTimeBy(600)
        indicators(1)
        cached.assertIsDisplayed()
        val expanded = file.fetchSemanticsNode().boundsInRoot
        assertTrue("The cached hint should occupy space while visible", expanded.top > before.top)
        update { state.value = state.value.copy(refreshing = false) }
        indicators(1)
        compose.mainClock.advanceTimeBy(80)
        val collapsing = file.fetchSemanticsNode().boundsInRoot
        assertTrue("The hint should collapse gradually", collapsing.top > before.top && collapsing.top < expanded.top)
        compose.mainClock.advanceTimeBy(1000)
        indicators(0)
        cached.assertDoesNotExist()
        assertEquals(before, file.fetchSemanticsNode().boundsInRoot)
    }
}
