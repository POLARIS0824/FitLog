package com.example.fitlog.diary

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.vault.MarkdownScan
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.ui.preview.PreviewDiary
import com.example.fitlog.ui.theme.FitLogTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DiaryDetailLoadingTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private fun snapshot() = MarkdownSnapshot(PreviewDiary.file,
        context.getString(R.string.preview_diary_markdown), "preview", false)

    private fun renderPaused(initial: DiaryDetailUiState) = mutableStateOf(initial).also { state ->
        compose.mainClock.autoAdvance = false
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            DiaryDetailContent(state.value, DiaryDetailActions(), {}, {}, {})
        } } }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun indicators(count: Int) = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(count)

    private fun applyStateChange() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun initialReadKeepsTheEditSlotEmptyUntilFileCapabilitiesAreKnown() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, sourceLoading = true, canEdit = false))
        val edit = compose.onNodeWithContentDescription(context.getString(R.string.detail_edit))
        val menu = compose.onNodeWithContentDescription(context.getString(R.string.log_options))
        val before = menu.fetchSemanticsNode().boundsInRoot
        edit.assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnUiThread { state.value = state.value.copy(original = snapshot(), sourceLoading = false, canEdit = true) }
        applyStateChange()
        edit.assertIsEnabled()
        assertEquals("Showing the edit action moved the options action", before, menu.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun readOnlyFilesStillShowADisabledEditAction() {
        renderPaused(DiaryDetailUiState(PreviewDiary.route,
            original = snapshot().let { it.copy(file = it.file.copy(writable = false)) }, canEdit = false))
        compose.onNodeWithContentDescription(context.getString(R.string.detail_edit)).assertIsNotEnabled()
    }

    @Test fun savingAReviewStillDisablesTheEditAction() {
        renderPaused(DiaryDetailUiState(PreviewDiary.route, original = snapshot(), canEdit = true, reviewSaving = true))
        compose.onNodeWithContentDescription(context.getString(R.string.detail_edit)).assertIsNotEnabled()
    }

    @Test fun fastSourceReadShowsContentWithoutFlashingAnIndicator() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, sourceLoading = true))
        indicators(0)
        compose.mainClock.advanceTimeBy(200)
        indicators(0)
        val snapshot = snapshot()
        compose.runOnUiThread { state.value = state.value.copy(sourceLoading = false, original = snapshot) }
        applyStateChange()
        compose.onNodeWithText(snapshot.text).assertIsDisplayed()
        indicators(0)
        compose.mainClock.advanceTimeBy(500)
        indicators(0)
    }

    @Test fun slowSourceReadShowsFeedbackWithoutMovingContentAndHidesItImmediatelyOnCompletion() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, original = snapshot(), sourceLoading = true))
        val before = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(250)
        indicators(0)
        compose.mainClock.advanceTimeBy(100)
        indicators(1)
        val during = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot.top
        assertEquals("Showing delayed feedback moved the original viewport", before.toDouble(), during.toDouble(), 1.0)
        compose.runOnUiThread { state.value = state.value.copy(sourceLoading = false) }
        applyStateChange()
        indicators(0)
    }

    @Test fun aNewReadGetsItsOwnDelayInsteadOfReusingThePreviousReadTimer() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, sourceLoading = true))
        compose.mainClock.advanceTimeBy(250)
        compose.runOnUiThread { state.value = state.value.copy(sourceLoading = false) }
        applyStateChange()
        compose.runOnUiThread { state.value = state.value.copy(sourceLoading = true) }
        applyStateChange()
        compose.mainClock.advanceTimeBy(100)
        indicators(0)
        compose.mainClock.advanceTimeBy(250)
        indicators(1)
    }

    @Test fun fastAnalysisReadShowsNeitherLoadingTextNorAnIndicator() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS,
            parsesLoading = true, confirmationLoading = true))
        indicators(0)
        compose.onNodeWithText(context.getString(R.string.detail_status_loading)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_not_parsed)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_not_confirmed)).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnUiThread { state.value = state.value.copy(parsesLoading = false, confirmationLoading = false) }
        applyStateChange()
        compose.onNodeWithText(context.getString(R.string.detail_not_parsed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_not_confirmed)).assertIsDisplayed()
        indicators(0)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(context.getString(R.string.detail_status_loading)).assertDoesNotExist()
        indicators(0)
    }

    @Test fun independentAnalysisReadsDoNotDelayReadyResultsOrMoveTheStatusCard() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS,
            parsesLoading = true, confirmationLoading = true))
        val before = compose.onNodeWithText(context.getString(R.string.ai_parse)).fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(200)
        compose.runOnUiThread { state.value = state.value.copy(parsesLoading = false) }
        applyStateChange()
        compose.onNodeWithText(context.getString(R.string.detail_not_parsed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_status_loading)).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithText(context.getString(R.string.detail_status_loading)).assertIsDisplayed()
        indicators(1)
        val during = compose.onNodeWithText(context.getString(R.string.ai_parse)).fetchSemanticsNode().boundsInRoot.top
        assertEquals("Showing delayed feedback moved the analysis action", before.toDouble(), during.toDouble(), 1.0)
        compose.runOnUiThread { state.value = state.value.copy(confirmationLoading = false) }
        applyStateChange()
        compose.onNodeWithText(context.getString(R.string.detail_not_confirmed)).assertIsDisplayed()
        indicators(0)
    }

    @Test fun aiParsingStillShowsImmediateFeedback() {
        renderPaused(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS, parsing = true))
        compose.onNodeWithText(context.getString(R.string.ai_parsing)).assertIsDisplayed()
        indicators(1)
    }

    @Test fun savingAReviewStillShowsImmediateFeedback() {
        renderPaused(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS, reviewSaving = true))
        compose.onNodeWithText(context.getString(R.string.detail_review_saving)).assertIsDisplayed()
        indicators(1)
    }

    @Test fun aFastSourceReadFailureIsShownImmediatelyAndCancelsThePendingHint() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, sourceLoading = true))
        compose.mainClock.advanceTimeBy(200)
        compose.runOnUiThread { state.value = state.value.copy(sourceLoading = false, sourceReadFailed = true) }
        applyStateChange()
        compose.onNodeWithText(context.getString(R.string.detail_source_failed)).assertIsDisplayed()
        indicators(0)
        compose.mainClock.advanceTimeBy(500)
        indicators(0)
    }

    @Test fun aFastAnalysisReadFailureIsShownImmediatelyAndCancelsThePendingHints() {
        val state = renderPaused(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS,
            parsesLoading = true, confirmationLoading = true))
        compose.mainClock.advanceTimeBy(200)
        compose.runOnUiThread { state.value = state.value.copy(parsesLoading = false, confirmationLoading = false,
            parsesReadFailed = true, confirmationReadFailed = true) }
        applyStateChange()
        compose.onNodeWithText(context.getString(R.string.detail_analysis_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_retry_analysis)).assertIsDisplayed()
        indicators(0)
        compose.mainClock.advanceTimeBy(500)
        indicators(0)
    }

    @Test fun completingTheInitialReadDoesNotMoveTheOriginalViewport() {
        val state = mutableStateOf(DiaryDetailUiState(PreviewDiary.route, sourceLoading = true))
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            DiaryDetailContent(state.value, DiaryDetailActions(), {}, {}, {})
        } } }
        val before = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.value = state.value.copy(sourceLoading = false, original = snapshot())
        }
        val after = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertEquals("Finishing the initial read moved the original viewport", before.top.toDouble(), after.top.toDouble(), 1.0)
    }

    @Test fun refreshingTheOriginalDoesNotMoveVisibleText() {
        val snapshot = snapshot()
        val state = mutableStateOf(DiaryDetailUiState(PreviewDiary.route, original = snapshot))
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            DiaryDetailContent(state.value, DiaryDetailActions(), {}, {}, {})
        } } }
        val before = compose.onNodeWithText(snapshot.text).fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = state.value.copy(sourceLoading = true) }
        val during = compose.onNodeWithText(snapshot.text).fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = state.value.copy(sourceLoading = false) }
        val after = compose.onNodeWithText(snapshot.text).fetchSemanticsNode().boundsInRoot.top
        assertEquals("Starting a refresh moved the visible original", before.toDouble(), during.toDouble(), 1.0)
        assertEquals("Finishing a refresh moved the visible original", before.toDouble(), after.toDouble(), 1.0)
    }

    @Test fun fastInitialReadThenNavigationResumeKeepsTheOriginalAndEditActionStable() {
        val snapshot = snapshot()
        val resumeRead = CompletableDeferred<Unit>()
        var reads = 0
        val documents = object : MarkdownDocuments {
            override suspend fun read(uri: String): MarkdownSnapshot {
                reads++
                if (reads > 1) resumeRead.await()
                return snapshot
            }
            override suspend fun scan(vault: String): MarkdownScan = error("No scan expected")
            override suspend fun find(vault: String, name: String): MarkdownFile? = error("No lookup expected")
            override suspend fun create(vault: String, name: String): MarkdownFile = error("No creation expected")
            override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("No write expected")
        }
        val reader = object : DiaryAnalysisReader {
            override fun observeParses(sourceKey: SourceKey) = flowOf(DiaryParseRecords())
            override fun observeConfirmed(sourceKey: SourceKey) = flowOf<ConfirmedDiaryRecord?>(null)
            override suspend fun readCandidate(sourceKey: SourceKey, parseRunId: String): StoredDiaryCandidate? = null
        }
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        val store = ViewModelStore()
        lateinit var vm: DiaryDetailViewModel
        try {
            compose.runOnUiThread {
                owner.lifecycle.currentState = Lifecycle.State.STARTED
                vm = DiaryDetailViewModel(PreviewDiary.route, documents, reader, SavedStateHandle(),
                    { error("Opening a diary must not parse it") }, hashingDispatcher = Dispatchers.Unconfined)
                store.put("detail", vm)
            }
            compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FitLogTheme(dynamicColor = false) { Surface { DiaryDetailScreen(vm, {}, {}, {}) } }
            } }
            val before = compose.onNodeWithText(snapshot.text).fetchSemanticsNode().boundsInRoot.top
            val edit = compose.onNodeWithContentDescription(context.getString(R.string.detail_edit))
            edit.assertIsEnabled()
            compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            val during = compose.onNodeWithText(snapshot.text).fetchSemanticsNode().boundsInRoot.top
            edit.assertIsEnabled()
            compose.runOnIdle { assertEquals("The entry-time resume read was not exercised", 2, reads) }
            compose.runOnUiThread { resumeRead.complete(Unit) }
            val after = compose.onNodeWithText(snapshot.text).fetchSemanticsNode().boundsInRoot.top
            edit.assertIsEnabled()
            assertEquals("Resuming after a fast initial read moved the original", before.toDouble(), during.toDouble(), 1.0)
            assertEquals("Completing the resume read moved the original", before.toDouble(), after.toDouble(), 1.0)
        } finally {
            compose.runOnUiThread { resumeRead.complete(Unit); store.clear() }
        }
    }
}
