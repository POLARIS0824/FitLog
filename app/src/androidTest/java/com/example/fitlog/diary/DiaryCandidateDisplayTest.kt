package com.example.fitlog.diary

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.analysis.adapter.DiaryModelResponse
import com.example.fitlog.data.analysis.adapter.DiaryModelSource
import com.example.fitlog.data.analysis.adapter.JsonDiaryParser
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import org.junit.Rule
import org.junit.Test

/** Checks the user-visible distinction between model excerpts, training values and missing counts. */
class DiaryCandidateDisplayTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingCountStillShowsKnownValuesAndDoesNotInventASet() {
        display("bench 38kg 8次", """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"bench","groups":[{"weight":38,"unit":"KG","reps":8}]}
        ]}]}""")
        show(context.getString(R.string.detail_count_not_provided))
        show(weight(38))
        show(context.getString(R.string.detail_reps_short, 8))
        compose.onAllNodesWithContentDescription(context.getString(R.string.detail_issue_review), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.detail_set, 1)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_model_excerpt)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_no_sets)).assertDoesNotExist()
    }

    @Test fun excerptHintAppearsBesideModelExcerptAndTrainingRemainsVisible() {
        display("- **bench**：38kg 1x8", """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"bench","evidence":{"quote":"bench：38kg 1x8"},
             "groups":[{"weight":38,"unit":"KG","count":1,"reps":8}]}
        ]}]}""")
        val disclosure = hasText(context.getString(R.string.detail_sources), substring = true)
        compose.onNodeWithTag("candidate-list").performScrollToNode(disclosure)
        compose.onNode(disclosure).performClick()
        show(context.getString(R.string.detail_model_excerpt))
        show(context.getString(R.string.detail_issue_evidence_missing))
        compose.onAllNodes(hasText(context.getString(R.string.detail_issue_error), substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText(context.getString(R.string.detail_issue_review), substring = true)).assertCountEquals(0)
        show(context.getString(R.string.detail_set, 1))
        show(weight(38))
    }

    private fun show(text: String) {
        compose.onNodeWithTag("candidate-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun weight(number: Int) = context.getString(R.string.detail_weight_short,
        NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).format(number), context.getString(R.string.detail_unit_kg_short))

    @Test fun identicalWeightsRemainVisibleInEverySetRow() {
        display("bench 60kg 3x8", """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"bench","groups":[{"weight":60,"unit":"KG","basis":"TOTAL","count":3,"reps":8}]}
        ]}]}""")
        compose.onNodeWithTag("candidate-list").performScrollToNode(hasText(context.getString(R.string.detail_set, 3)))
        compose.onAllNodesWithText(weight(60)).assertCountEquals(3)
    }

    private fun display(original: String, response: String) {
        val input = DiaryParseInput.fromSnapshot(
            SourceKey("00000000-0000-4000-8000-000000000001", "note.md"), original, "display-test",
        )
        val analysis = runBlocking {
            (JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(response) }).parse(input) as DiaryParseResult.Success).analysis
        }
        val attempt = DiaryParseAttempt("run", input.parseKey, ParseRunStatus.SUCCEEDED, 0, 1, null)
        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("candidate-list")) {
                    candidateItems(StoredDiaryCandidate(attempt, analysis), DiaryResultFreshness.CURRENT, false)
                }
            }
        }
    }
}
