package com.example.fitlog.model.analysis

import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.model.record.ReviewedSession
import com.example.fitlog.model.record.ReviewedSet
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate

data class DiaryConfirmation(
    val sourceKey: SourceKey,
    val parseRunId: String,
    val date: LocalDate,
    val sessions: List<ReviewedSession>,
    val acceptedPartialResult: Boolean = false,
    val expectedConfirmedAt: Long? = null,
    val requireLatestCandidate: Boolean = false,
    val editConfirmed: Boolean = false,
    val expectedContentVersion: DiaryContentVersion? = null,
    val kind: RecordKind = RecordKind.TRAINING,
) {
    companion object {
        fun fromCandidate(
            parseRunId: String,
            analysis: DiaryAnalysis,
            date: LocalDate,
            acceptedPartialResult: Boolean = false,
        ) = DiaryConfirmation(
            analysis.parseKey.sourceKey, parseRunId, date,
            analysis.sessions.map { session ->
                ReviewedSession(session.exercises.map { exercise ->
                    ReviewedExercise(
                        exercise.candidate.rawName,
                        exercise.sets.map { set ->
                            ReviewedSet(set.weight.value, set.unit.value, set.basis.value,
                                set.reps.value, set.groupIndex, set.setInGroup)
                        },
                        exercise.candidate.notes,
                        exercise.evidence.takeIf { it.segmentId == DIARY_SEGMENT_ID && it.quote.isNotBlank() },
                        exercise.path,
                    )
                }, session.notes, session.path)
            }, acceptedPartialResult,
        )
    }
}

sealed interface DiaryConfirmationResult {
    data class Confirmed(val diary: TrainingRecordDetails) : DiaryConfirmationResult
    data class Invalid(val reason: ConfirmationFailure) : DiaryConfirmationResult
}

enum class ConfirmationFailure {
    INVALID_PARSE_RUN, SOURCE_MISMATCH, INVALID_DATA, PARTIAL_RESULT_NOT_ACCEPTED,
    SOURCE_CHANGED, SOURCE_UNAVAILABLE, CONFIRMATION_CHANGED, CANDIDATE_CHANGED,
}
