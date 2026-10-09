package com.example.fitlog.diary

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.ui.preview.PreviewDiary
import com.example.fitlog.ui.theme.FitLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DiaryWeightDisplayTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    private fun check(view: Int) {
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            val baseline = PreviewDiary.candidate()
            val session = baseline.analysis.sessions.single()
            val exercise = session.exercises.single()
            val rows = exercise.sets.mapIndexed { index, set ->
                set.copy(weight = CandidateValue(if (index == 2) 10.0 else null, CandidateOrigin.EXPLICIT),
                    unit = CandidateValue(if (index == 2) WeightUnit.KG else null, CandidateOrigin.EXPLICIT),
                    basis = CandidateValue(listOf(WeightBasis.BODYWEIGHT, WeightBasis.UNKNOWN, WeightBasis.ADDED)[index], CandidateOrigin.EXPLICIT))
            }
            val candidate = baseline.copy(analysis = baseline.analysis.copy(sessions = listOf(
                session.copy(exercises = listOf(exercise.copy(sets = rows))))))
            val draft = DiaryReviewDraft.fromCandidate(candidate, null)
            LazyColumn {
                when (view) {
                    0 -> candidateItems(candidate, DiaryResultFreshness.CURRENT, false, canEdit = true)
                    1 -> reviewItems(draft, candidate, true, {})
                    else -> confirmedItems(record(candidate, draft))
                }
            }
        } } }
        for ((index, label) in listOf(
            context.getString(R.string.detail_weight_bodyweight),
            context.getString(R.string.detail_weight_unrecorded),
            context.getString(R.string.detail_weight_short, "10", "kg"),
        ).withIndex()) {
            val description = context.getString(R.string.detail_edit_value,
                context.getString(R.string.detail_set, index + 1), context.getString(R.string.detail_weight), label)
            compose.onNodeWithContentDescription(description).assertExists()
        }
    }

    private fun record(candidate: StoredDiaryCandidate, draft: DiaryReviewDraft): ConfirmedDiaryRecord {
        val diary = ConfirmedDiaryRow("diary", PreviewDiary.VAULT_ID, PreviewDiary.PATH, PreviewDiary.DATE,
            candidate.analysis.parseKey.contentHash, 1, candidate.attempt.id, 1, false)
        val session = ConfirmedSessionRow("session", diary.id, 0, null, "sessions[0]")
        val exercise = ConfirmedExerciseRow("exercise", session.id, 0, "Exercise", null, "sessions[0].exercises[0]", null)
        return ConfirmedDiaryRecord(diary, listOf(ConfirmedSessionRecord(session, listOf(
            ConfirmedExerciseRecord(exercise, draft.sessions.single().exercises.single().sets.mapIndexed { index, set ->
                ConfirmedSetRow("set-$index", exercise.id, index, set.weight, set.unit, set.basis, set.reps,
                    null, set.groupIndex, set.setInGroup, set.userEdited)
            })
        ))))
    }

    @Test fun candidateDistinguishesBodyweightMissingWeightAndAddedLoad() = check(0)
    @Test fun pendingReviewDistinguishesBodyweightMissingWeightAndAddedLoad() = check(1)
    @Test fun confirmedRecordDistinguishesBodyweightMissingWeightAndAddedLoad() = check(2)
}
