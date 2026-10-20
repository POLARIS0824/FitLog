package com.example.fitlog.diary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*

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

private fun questionWasRemoved(issue: CandidateIssue, draft: DiaryReviewDraft?, candidate: StoredDiaryCandidate) =
    draft != null && questionTarget(issue, draft.sessions) == null &&
        questionTarget(issue, DiaryReviewDraft.fromCandidate(candidate, null).sessions) != null

@Composable
internal fun DiaryReviewQuestion(issue: CandidateIssue, candidate: StoredDiaryCandidate, draft: DiaryReviewDraft?, canEdit: Boolean,
    onEdit: (DiarySetEdit) -> Unit, onEditExercise: (DiaryExerciseAddress) -> Unit) {
    val sessions = draft?.sessions ?: DiaryReviewDraft.fromCandidate(candidate, null).sessions
    val target = questionTarget(issue, sessions)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(issue.question, style = MaterialTheme.typography.bodyLarge)
            if (questionWasRemoved(issue, draft, candidate)) Text(stringResource(R.string.detail_question_removed),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            if (questionWasEdited(issue, draft, candidate)) Text(stringResource(R.string.detail_question_updated),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            if (target != null) {
                val exercise = sessions[target.exercise.session].exercises[target.exercise.exercise]
                Text(exercise.rawName, style = MaterialTheme.typography.labelLarge)
                exercise.evidence?.quote?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val set = target.set?.let { exercise.sets.getOrNull(it.set) }
                if (target.set != null && set != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (target.field?.let { listOf(it) } ?: DiarySetField.entries).forEach { field ->
                            TextButton(onClick = { onEdit(DiarySetEdit(target.set, target.set.set + 1, field,
                                set.weight, set.unit, set.reps, set.basis)) }, enabled = canEdit) {
                                Text(stringResource(R.string.detail_clarify_field, stringResource(when (field) {
                                    DiarySetField.BASIS -> R.string.detail_basis
                                    DiarySetField.WEIGHT -> if (issue.path.endsWith(".unit")) R.string.detail_unit else R.string.detail_weight
                                    DiarySetField.REPS -> R.string.detail_reps
                                })))
                            }
                        }
                    }
                }
                if (target.field == null || set == null) TextButton(onClick = { onEditExercise(target.exercise) }, enabled = canEdit) {
                    Text(stringResource(R.string.detail_edit_exercise))
                }
            } else if (!questionWasRemoved(issue, draft, candidate))
                Text(stringResource(R.string.detail_question_general_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}
