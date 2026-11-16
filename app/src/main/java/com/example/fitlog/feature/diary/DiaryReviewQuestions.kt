package com.example.fitlog.feature.diary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.example.fitlog.model.analysis.CandidateIssue
import com.example.fitlog.model.analysis.StoredDiaryCandidate
import com.example.fitlog.model.record.ReviewedSession

internal data class DiaryQuestionTarget(val exercise: DiaryExerciseAddress, val set: DiarySetAddress?, val field: DiarySetField?)

private fun questionGroup(issue: CandidateIssue) = Regex("\\.groups\\[(\\d+)]").find(issue.path)?.groupValues?.get(1)?.toIntOrNull()

private fun String.within(path: String): Boolean {
    val value = removePrefix("$.")
    val prefix = path.removePrefix("$.")
    return value == prefix || value.startsWith("$prefix.") || value.startsWith("$prefix[")
}

/** Route by the model's JSON path, never by guessing what its free-text question means. */
internal fun questionTarget(issue: CandidateIssue, sessions: List<ReviewedSession>): DiaryQuestionTarget? {
    sessions.forEachIndexed { sessionIndex, session -> session.exercises.forEachIndexed { exerciseIndex, exercise ->
        if (exercise.sourcePath?.let { issue.path.within(it) } == true) {
            val group = questionGroup(issue)
            val set = exercise.sets.indexOfFirst { group == null || it.groupIndex == group }.takeIf { it >= 0 }
            val field = when (issue.path.substringAfterLast('.')) {
                "basis" -> DiarySetField.BASIS
                "weight", "unit" -> DiarySetField.WEIGHT
                "reps", "repsList" -> DiarySetField.REPS
                else -> null
            }
            return DiaryQuestionTarget(DiaryExerciseAddress(sessionIndex, exerciseIndex),
                set?.let { DiarySetAddress(sessionIndex, exerciseIndex, it) }, field)
        }
    } }
    return null
}

internal fun questionWasEdited(issue: CandidateIssue, draft: DiaryReviewDraft?, candidate: StoredDiaryCandidate): Boolean {
    if (draft == null) return false
    val original = DiaryReviewDraft.fromCandidate(candidate, null)
    val target = questionTarget(issue, draft.sessions) ?: return false
    val before = questionTarget(issue, original.sessions) ?: return false
    val exercise = draft.sessions[target.exercise.session].exercises[target.exercise.exercise]
    val baseline = original.sessions[before.exercise.session].exercises[before.exercise.exercise]
    if (target.field == null) return exercise != baseline ||
        draft.fragments.filter { it.session == target.exercise.session && it.exercise == target.exercise.exercise } !=
        original.fragments.filter { it.session == before.exercise.session && it.exercise == before.exercise.exercise }
    val group = questionGroup(issue)
    val values = exercise.sets.filter { group == null || it.groupIndex == group }
    val oldValues = baseline.sets.filter { group == null || it.groupIndex == group }
    return when (issue.path.substringAfterLast('.')) {
        "basis" -> values.map { it.basis } != oldValues.map { it.basis }
        "weight" -> values.map { it.weight } != oldValues.map { it.weight }
        "unit" -> values.map { it.unit } != oldValues.map { it.unit }
        else -> values.map { it.reps } != oldValues.map { it.reps }
    }
}

internal fun questionWasRemoved(issue: CandidateIssue, draft: DiaryReviewDraft?, candidate: StoredDiaryCandidate) =
    draft != null && questionTarget(issue, draft.sessions) == null &&
        questionTarget(issue, DiaryReviewDraft.fromCandidate(candidate, null).sessions) != null
