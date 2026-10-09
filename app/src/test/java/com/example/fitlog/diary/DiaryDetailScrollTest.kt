package com.example.fitlog.diary

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.ui.preview.PreviewDiary
import com.example.fitlog.ui.theme.FitLogTheme
import java.text.NumberFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the actual picker Done callback and the candidate -> pending-review list update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DiaryDetailScrollTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun applyingAWeightCorrectionKeepsTheVisibleExerciseInPlace() = checkPosition(6, DiarySetField.WEIGHT)
    @Test fun applyingARepsCorrectionKeepsTheVisibleExerciseInPlace() = checkPosition(6, DiarySetField.REPS)
    @Test fun firstCorrectionDoesNotPushTheFirstExerciseDown() = checkPosition(0, DiarySetField.WEIGHT, scroll = false)
    @Test fun correctionKeepsExpandedSourceAndTheExercisePosition() = checkPosition(6, DiarySetField.WEIGHT, expanded = true)
    @Test fun editingAConfirmedResultKeepsTheExercisePosition() = checkPosition(6, DiarySetField.WEIGHT, confirmedResult = true)
    @Test fun editingAPartialConfirmationKeepsTheFirstExerciseInPlace() =
        checkPosition(0, DiarySetField.REPS, scroll = false, confirmedResult = true, partial = true)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun convertingToPoundsKeepsTheExerciseAndNextExerciseInPlace() =
        checkPosition(6, DiarySetField.WEIGHT, pounds = true)

    private fun checkPosition(exerciseIndex: Int, field: DiarySetField, scroll: Boolean = true, expanded: Boolean = false,
        confirmedResult: Boolean = false, partial: Boolean = false, pounds: Boolean = false) {
        val candidate = candidate()
        val confirmed = if (confirmedResult) confirmed(candidate, partial) else null
        val state = mutableStateOf(DiaryDetailUiState(PreviewDiary.route, tab = DiaryDetailTab.ANALYSIS,
            parses = DiaryParseRecords(listOf(candidate.attempt), candidate),
            candidateStatus = DiaryResultFreshness.CURRENT, confirmed = confirmed, canReview = true))
        fun initialDraft() = confirmed?.let { DiaryReviewDraft.fromConfirmed(it, candidate) }
            ?: DiaryReviewDraft.fromCandidate(candidate, null)
        val actions = DiaryDetailActions(
            updateWeight = { address, value ->
                val draft = state.value.review ?: initialDraft()
                state.value = state.value.copy(review = draft.withWeight(address, value.weight, value.unit, value.basis))
            },
            updateReps = { address, reps ->
                val draft = state.value.review ?: initialDraft()
                state.value = state.value.copy(review = draft.withReps(address, reps))
            })
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            DiaryDetailContent(state.value, actions, {}, {}, {})
        } } }
        val exerciseName = "Exercise $exerciseIndex"
        if (scroll) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(exerciseName))
        if (expanded) {
            val title = compose.onNodeWithText(exerciseName).fetchSemanticsNode().boundsInRoot.top
            val disclosure = compose.onAllNodesWithText(context.getString(R.string.detail_sources)).fetchSemanticsNodes()
                .filter { it.boundsInRoot.top > title }.minBy { it.boundsInRoot.top }
            compose.onNode(SemanticsMatcher("Selected exercise disclosure") { it.id == disclosure.id })
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText("Exercise $exerciseIndex source").assertExists()
        }
        val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0])
        val value = if (field == DiarySetField.WEIGHT) context.getString(R.string.detail_weight_short,
            number.format(60 + exerciseIndex), context.getString(R.string.detail_unit_kg_short))
            else context.getString(R.string.detail_reps_short, 8 + exerciseIndex)
        val description = context.getString(R.string.detail_edit_value, context.getString(R.string.detail_set, 1),
            context.getString(if (field == DiarySetField.WEIGHT) R.string.detail_weight else R.string.detail_reps), value)
        val editButton = compose.onNodeWithContentDescription(description)
        if (pounds) editButton.performScrollTo()
        editButton.assertIsDisplayed()
        val before = compose.onNodeWithText(exerciseName).fetchSemanticsNode().boundsInRoot.top
        val nextExerciseName = "Exercise ${exerciseIndex + 1}"
        val nextBefore = compose.onAllNodesWithText(nextExerciseName).fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            ?.takeIf { !it.isEmpty }?.top
        if (pounds) editButton.performSemanticsAction(SemanticsActions.OnClick) { it() }
        else editButton.performClick()
        if (pounds) compose.onNodeWithText(context.getString(R.string.detail_unit_lb_short))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        else {
            compose.onNodeWithText(context.getString(R.string.detail_exact_input)).performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNode(hasSetTextAction()).performTextReplacement(if (field == DiarySetField.WEIGHT) "67.5" else "19")
        }
        compose.onNodeWithText(context.getString(R.string.detail_picker_done)).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        val after = compose.onNodeWithText(exerciseName).fetchSemanticsNode().boundsInRoot.top
        assertEquals("Applying $field changed the visible exercise position", before.toDouble(), after.toDouble(), 1.0)
        if (nextBefore != null) {
            val nextAfter = compose.onNodeWithText(nextExerciseName).fetchSemanticsNode().boundsInRoot.top
            assertEquals("The edit marker changed the next exercise position", nextBefore.toDouble(), nextAfter.toDouble(), 1.0)
        }
        if (expanded) compose.onNodeWithText("Exercise $exerciseIndex source").assertExists()
    }

    private fun candidate(): StoredDiaryCandidate {
        val key = DiaryParseKey(SourceKey(PreviewDiary.VAULT_ID, PreviewDiary.PATH), "a".repeat(64), 1, "scroll-test")
        val exercises = (0..11).map { index ->
            val evidence = EvidenceQuote(quote = "Exercise $index source")
            val group = SetGroupCandidate(weight = 60.0 + index, unit = WeightUnit.KG, basis = WeightBasis.TOTAL,
                count = 1, reps = 8 + index)
            val set = ExpandedSet(0, 0, CandidateValue(group.weight, CandidateOrigin.EXPLICIT),
                CandidateValue(WeightUnit.KG, CandidateOrigin.EXPLICIT), CandidateValue(WeightBasis.TOTAL, CandidateOrigin.EXPLICIT),
                CandidateValue(group.reps, CandidateOrigin.EXPLICIT), CandidateOrigin.EXPLICIT, group.weight)
            ValidatedExercise("sessions[0].exercises[$index]", ExerciseCandidate("Exercise $index", evidence, listOf(group)),
                evidence, listOf(set))
        }
        val analysis = DiaryAnalysis(key, listOf(ValidatedSession("sessions[0]", null, null, exercises)),
            listOf(CandidateIssue("sessions", "Clarify training date")), emptyList())
        return StoredDiaryCandidate(DiaryParseAttempt("scroll-run", key, ParseRunStatus.SUCCEEDED, 0, 1, null), analysis)
    }

    private fun confirmed(candidate: StoredDiaryCandidate, partial: Boolean): ConfirmedDiaryRecord {
        val diary = ConfirmedDiaryRow("confirmed", PreviewDiary.VAULT_ID, PreviewDiary.PATH, PreviewDiary.DATE,
            candidate.analysis.parseKey.contentHash, 1, candidate.attempt.id, 1, partial)
        val sourceSession = candidate.analysis.sessions.single()
        val session = ConfirmedSessionRow("session", diary.id, 0, null, sourceSession.path)
        val exercises = sourceSession.exercises.mapIndexed { index, source ->
            val exercise = ConfirmedExerciseRow("exercise-$index", session.id, index, source.candidate.rawName,
                null, source.path, source.evidence)
            ConfirmedExerciseRecord(exercise, source.sets.mapIndexed { position, set ->
                ConfirmedSetRow("set-$index-$position", exercise.id, position, set.weight.value, set.unit.value,
                    set.basis.value, set.reps.value, set.weightKg, set.groupIndex, set.setInGroup, false)
            })
        }
        return ConfirmedDiaryRecord(diary, listOf(ConfirmedSessionRecord(session, exercises)))
    }
}
