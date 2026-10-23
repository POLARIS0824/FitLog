package com.example.fitlog.model.record

import com.example.fitlog.model.analysis.EvidenceQuote
import kotlinx.serialization.Serializable

/** Final user-reviewed values; detailed extraction provenance stays in the candidate. */
@Serializable
data class ReviewedSet(
    val weight: Double? = null,
    val unit: WeightUnit? = null,
    val basis: WeightBasis? = null,
    val reps: Int? = null,
    val groupIndex: Int? = null,
    val setInGroup: Int? = null,
    val userEdited: Boolean = false,
)

@Serializable
data class ReviewedExercise(
    val rawName: String,
    val sets: List<ReviewedSet> = emptyList(),
    val notes: String? = null,
    val evidence: EvidenceQuote? = null,
    val sourcePath: String? = null,
)

@Serializable
data class ReviewedSession(
    val exercises: List<ReviewedExercise> = emptyList(),
    val notes: String? = null,
    val sourcePath: String? = null,
)

fun TrainingRecordDetails.reviewedSessions() = sessions.map { session ->
    ReviewedSession(session.exercises.map { exercise ->
        ReviewedExercise(exercise.exercise.rawName, exercise.sets.map { set ->
            ReviewedSet(set.weight, set.unit, set.basis, set.reps, set.groupIndex, set.setInGroup, set.userEdited)
        }, exercise.exercise.notes, exercise.exercise.evidence, exercise.exercise.sourcePath)
    }, session.session.notes, session.session.sourcePath)
}
