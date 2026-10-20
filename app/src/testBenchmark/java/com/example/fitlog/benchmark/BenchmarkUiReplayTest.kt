package com.example.fitlog.benchmark

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.diary.*
import com.example.fitlog.log.*
import com.example.fitlog.ui.preview.PreviewDiary
import com.example.fitlog.ui.theme.FitLogTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Production UI content with replayed states. These tests do not measure device rendering speed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BenchmarkUiReplayTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun parsingProgressOffersCancellationAndThenFirstCandidateCanBeReviewed() {
        val state = mutableStateOf(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS, parsing = true))
        var cancels = 0
        var reviews = 0
        lateinit var candidate: StoredDiaryCandidate
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            candidate = PreviewDiary.candidate()
            DiaryDetailContent(state.value, DiaryDetailActions(cancelParse = { cancels++ }, beginReview = { reviews++; false }), {}, {}, {})
        } } }
        compose.onNodeWithText(app.getString(R.string.ai_parsing)).assertExists()
        compose.onNodeWithText(app.getString(R.string.ai_cancel_request)).performClick()
        assertEquals(1, cancels)
        compose.runOnIdle { state.value = state.value.copy(parsing = false, parses = DiaryParseRecords(listOf(candidate.attempt), candidate),
            candidateStatus = DiaryResultFreshness.CURRENT, canReview = true) }
        compose.onNodeWithText(app.getString(R.string.ai_cancel_request)).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.getString(R.string.detail_confirm_save)))
        compose.onNodeWithText(app.getString(R.string.detail_confirm_save)).assertIsEnabled().performClick()
        assertEquals(1, reviews)
    }

    @Test fun completedBatchShowsReviewEntryAndCancellationDoesNotRemovePersistedResult() {
        val source = AnalysisSource(SourceKey(PreviewDiary.VAULT_ID, PreviewDiary.PATH), "benchmark://source")
        val run = DiaryAnalysisRun(vaultId = PreviewDiary.VAULT_ID, active = true, batch = true, total = 8,
            items = listOf(AnalysisItemResult(source, AnalysisItemOutcome.SUCCEEDED)))
        val state = mutableStateOf(LogUiState(vault = PreviewDiary.VAULT_ID, run = run, analysisBusy = true, pendingCount = 1))
        var cancels = 0
        var reviews = 0
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            LogAnalysisControls(state.value, LogActions(cancelAnalysis = { cancels++ }), { reviews++ })
        } } }
        compose.onNodeWithText(app.getString(R.string.ai_cancel_request)).performClick()
        assertEquals(1, cancels)
        compose.runOnIdle { state.value = state.value.copy(run = run.copy(active = false, stopped = true), analysisBusy = false) }
        compose.onNodeWithText(app.getString(R.string.log_review_count, 1)).assertIsEnabled().performClick()
        assertEquals(1, reviews)
        assertEquals(1, state.value.run.succeeded)
    }
}
