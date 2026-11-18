package com.example.fitlog.feature.diary.components

import com.example.fitlog.feature.diary.DiaryExerciseAddress
import com.example.fitlog.feature.diary.DiaryReviewDraft
import com.example.fitlog.feature.diary.DiarySetEdit
import com.example.fitlog.feature.diary.DiarySetField
import com.example.fitlog.feature.diary.questionTarget
import com.example.fitlog.feature.diary.questionWasEdited
import com.example.fitlog.feature.diary.questionWasRemoved
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.model.analysis.CandidateIssue
import com.example.fitlog.model.analysis.StoredDiaryCandidate
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun DiaryReviewQuestion(issue: CandidateIssue, candidate: StoredDiaryCandidate, draft: DiaryReviewDraft?, canEdit: Boolean,
    onEdit: (DiarySetEdit) -> Unit, onEditExercise: (DiaryExerciseAddress) -> Unit) {
    val sessions = draft?.sessions ?: DiaryReviewDraft.fromCandidate(candidate, null).sessions
    val target = questionTarget(issue, sessions)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(FitLogTheme.spacing.l), verticalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.s)) {
            Text(issue.question, style = MaterialTheme.typography.bodyLarge)
            if (questionWasRemoved(issue, draft, candidate)) Text(stringResource(R.string.detail_question_removed),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            if (questionWasEdited(issue, draft, candidate)) Text(stringResource(R.string.detail_question_updated),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            if (target != null) {
                val exercise = sessions[target.exercise.session].exercises[target.exercise.exercise]
                Text(exercise.rawName, style = MaterialTheme.typography.titleSmallEmphasized)
                exercise.evidence?.quote?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val set = target.set?.let { exercise.sets.getOrNull(it.set) }
                if (target.set != null && set != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.s)) {
                        (target.field?.let { listOf(it) } ?: DiarySetField.entries).forEach { field ->
                            TextButton(onClick = { onEdit(DiarySetEdit(target.set, target.set.set + 1, field,
                                set.weight, set.unit, set.reps, set.basis)) }, enabled = canEdit, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.detail_clarify_field, stringResource(when (field) {
                                    DiarySetField.BASIS -> R.string.detail_basis
                                    DiarySetField.WEIGHT -> if (issue.path.endsWith(".unit")) R.string.detail_unit else R.string.detail_weight
                                    DiarySetField.REPS -> R.string.detail_reps
                                })))
                            }
                        }
                    }
                }
                if (target.field == null || set == null) TextButton(onClick = { onEditExercise(target.exercise) }, enabled = canEdit, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.detail_edit_exercise))
                }
            } else if (!questionWasRemoved(issue, draft, candidate))
                Text(stringResource(R.string.detail_question_general_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}
