package com.example.fitlog.log

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.analysis.adapter.DiaryModelResponse
import com.example.fitlog.data.analysis.adapter.DiaryModelSource
import com.example.fitlog.data.analysis.adapter.JsonDiaryParser
import com.example.fitlog.data.index.IndexedSource
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Device acceptance for labeled selection controls and a reachable action with large text. */
class BatchReviewInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun noticeCanBeSelectedThroughItsFileLabelAndBlockedCandidateHasNoCheckbox() {
        display()
        compose.onNode(hasText("2026-10-09.md", substring = true) and isToggleable()).assertIsOn()
        val notice = hasText("2026-10-08.md", substring = true) and isToggleable()
        compose.onNode(hasScrollAction()).performScrollToNode(notice)
        compose.onNode(notice).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText(context.getString(R.string.batch_review_confirm, 2)).assertIsEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("note.md"))
        compose.onNode(hasText("note.md", substring = true) and isToggleable()).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.batch_review_date_problem)).assertIsDisplayed()
    }

    @Test fun readySelectionHasAnAccessibleLabelAndFooterStaysVisibleWithLargeText() {
        display(fontScale = 2f)
        val readyLabel = context.getString(R.string.batch_review_select_ready)
        compose.onNodeWithText(readyLabel).assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.batch_review_confirm, 0)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(readyLabel).performClick()
        compose.onNodeWithText(context.getString(R.string.batch_review_confirm, 1)).assertIsDisplayed().assertIsEnabled()
    }

    private fun display(fontScale: Float = 1f) {
        val ready = item("2026-10-09.md", missingWeight = false)
        val notice = item("2026-10-08.md", missingWeight = true)
        val blocked = item("note.md", missingWeight = false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    var selected by remember { mutableStateOf(setOf(ready.id)) }
                    BatchReviewContent(listOf(ready, notice, blocked), selected, false, false, true, false, null,
                        BatchConfirmationProgress(), false,
                        onToggle = { selected = if (it.id in selected) selected - it.id else selected + it.id },
                        onToggleReady = { selected = if (ready.id in selected) selected - ready.id else selected + ready.id },
                        onSubmit = {}, onRefresh = {}, onOpen = {}, onBack = {})
                }
            }
        }
    }

    private fun item(name: String, missingWeight: Boolean): BatchReviewItem {
        val key = SourceKey("00000000-0000-4000-8000-000000000001", name)
        val input = DiaryParseInput.fromSnapshot(key, "squat 60kg 1x8", "interaction-test")
        val weight = if (missingWeight) "" else "\"weight\":60,\"unit\":\"KG\","
        val response = """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"squat","evidence":{"quote":"squat 60kg 1x8"},
             "groups":[{$weight"basis":"TOTAL","count":1,"reps":8}]}
        ]}]}"""
        val analysis = runBlocking {
            (JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(response) }).parse(input) as DiaryParseResult.Success).analysis
        }
        val attempt = DiaryParseAttempt(name, input.parseKey, ParseRunStatus.SUCCEEDED, 0, 1, null)
        val summary = DiaryAnalysisSummary(key, attempt, StoredDiaryCandidate(attempt, analysis))
        val source = IndexedSource(key.vaultId, "content://test/$name", name, name, null, false,
            contentHash = input.parseKey.contentHash, hashVersion = input.parseKey.hashVersion)
        return BatchReviewItem(source, summary, assessBatchReview(summary, indexedVersion(source), true))
    }
}
