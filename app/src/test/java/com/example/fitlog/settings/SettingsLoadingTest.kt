package com.example.fitlog.settings

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
import com.example.fitlog.ai.AiSettingsActions
import com.example.fitlog.ai.AiSettingsContent
import com.example.fitlog.ai.AiSettingsUiState
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.ui.theme.FitLogTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLoadingTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun indicators(count: Int) = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(count)

    private fun appearance(initialBusy: Boolean = false) = mutableStateOf(initialBusy).also { busy ->
        compose.mainClock.autoAdvance = false
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            AppearanceContent(AppearancePreferences(), !busy.value, busy.value, null, {}, {}, {}, {})
        } } }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun update(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun fastAppearanceReadNeverShowsAnIndicator() {
        val busy = appearance(initialBusy = true)
        indicators(0)
        compose.mainClock.advanceTimeBy(250)
        indicators(0)
        update { busy.value = false }
        compose.mainClock.advanceTimeBy(600)
        indicators(0)
    }

    @Test fun slowAppearanceReadDoesNotMoveSettingsAndFadesOutOnCompletion() {
        val busy = appearance()
        val theme = compose.onNodeWithText(text(R.string.appearance_theme_heading))
        val before = theme.fetchSemanticsNode().boundsInRoot
        update { busy.value = true }
        assertEquals("Starting loading moved the theme group", before, theme.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(250)
        indicators(0)
        compose.mainClock.advanceTimeBy(600)
        indicators(1)
        assertEquals("Showing loading moved the theme group", before, theme.fetchSemanticsNode().boundsInRoot)
        snapshot("appearance-loading")
        update { busy.value = false }
        indicators(1) // The indicator stays composed during its exit fade.
        assertEquals("Hiding loading moved the theme group", before, theme.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(1000)
        indicators(0)
        assertEquals(before, theme.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun eachAppearanceOperationGetsItsOwnDelay() {
        val busy = appearance(initialBusy = true)
        compose.mainClock.advanceTimeBy(250)
        update { busy.value = false }
        update { busy.value = true }
        compose.mainClock.advanceTimeBy(100)
        indicators(0)
        compose.mainClock.advanceTimeBy(600)
        indicators(1)
    }

    @Test fun aiRequestsDoNotInsertProgressOrCancelRowsIntoTheForm() {
        val state = mutableStateOf(AiSettingsUiState(initialized = true, canEdit = true, name = "Test provider"))
        var cancels = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            AiSettingsContent(state.value, AiSettingsActions(cancelRequest = { cancels++ }), {})
        } } }
        compose.mainClock.advanceTimeByFrame()
        val connection = compose.onNodeWithText(text(R.string.ai_connection_heading))
        val before = connection.fetchSemanticsNode().boundsInRoot
        update { state.value = state.value.copy(requestRunning = true) }
        indicators(0)
        assertEquals("Starting an AI request moved the connection group", before, connection.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(600)
        indicators(1)
        assertEquals(before, connection.fetchSemanticsNode().boundsInRoot)
        snapshot("ai-loading")
        compose.onNodeWithContentDescription(text(R.string.ai_cancel_request)).assertIsEnabled().performClick()
        assertEquals(1, cancels)
        update { state.value = state.value.copy(requestRunning = false) }
        compose.mainClock.advanceTimeBy(1000)
        indicators(0)
        assertEquals(before, connection.fetchSemanticsNode().boundsInRoot)
    }

    private fun snapshot(name: String) {
        val output = File("build/settings-previews/$name.png").apply { parentFile?.mkdirs() }
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
