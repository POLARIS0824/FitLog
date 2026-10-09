package com.example.fitlog.vault

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
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
class VaultSetupLoadingTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun indicators(count: Int) = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(count)

    private fun render(initial: VaultSetupUiState) = mutableStateOf(initial).also { state ->
        compose.mainClock.autoAdvance = false
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            VaultSetupScreen(state.value, {}, {}, {})
        } } }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun update(action: () -> Unit) {
        compose.runOnUiThread(action)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun fastConfigReadDoesNotFlashLoadingDuringTheOutgoingBodyTransition() {
        val state = render(VaultSetupUiState())
        indicators(0)
        compose.mainClock.advanceTimeBy(250)
        indicators(0)
        update { state.value = state.value.copy(configState = VaultConfigUiState.NotConfigured) }
        compose.mainClock.advanceTimeBy(1000)
        indicators(0)
        compose.onNodeWithText(text(R.string.vault_setup_loading_config)).assertDoesNotExist()
    }

    @Test fun buttonFeedbackIsDelayedAndDoesNotMoveTheButtonOrItsCenteredLabel() {
        val state = render(VaultSetupUiState(configState = VaultConfigUiState.NotConfigured))
        val before = compose.onNode(hasClickAction() and hasText(text(R.string.vault_setup_btn_choose)))
            .fetchSemanticsNode().boundsInRoot
        val labelCenter = compose.onNodeWithText(text(R.string.vault_setup_btn_choose), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x
        update { state.value = state.value.copy(stage = VaultOperationStage.Checking) }
        compose.mainClock.advanceTimeBy(250)
        indicators(0)
        val checking = compose.onNode(hasClickAction() and hasText(text(R.string.vault_setup_checking)))
        assertEquals(before, checking.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(600)
        indicators(1)
        assertEquals(before, checking.fetchSemanticsNode().boundsInRoot)
        assertEquals(labelCenter, compose.onNodeWithText(text(R.string.vault_setup_checking), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x)
        val output = File("build/settings-previews/vault-button-loading.png").apply { parentFile?.mkdirs() }
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        update { state.value = state.value.copy(stage = VaultOperationStage.Idle) }
        indicators(1)
        compose.mainClock.advanceTimeBy(1000)
        indicators(0)
        assertEquals(before, compose.onNode(hasClickAction() and hasText(text(R.string.vault_setup_btn_choose)))
            .fetchSemanticsNode().boundsInRoot)
    }
}
