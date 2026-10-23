package com.example.fitlog.model.record

import com.example.fitlog.model.analysis.DiaryContentVersion
import com.example.fitlog.model.analysis.EvidenceQuote
import com.example.fitlog.model.source.SourceKey
import kotlinx.serialization.Serializable

@Serializable
enum class RecordOrigin { NATIVE, MARKDOWN }
@Serializable
enum class RecordKind { TRAINING, REST }

@Serializable
data class TrainingRecord(
    val id: String,
    val vaultId: String?,
    val relPath: String?,
    val date: String,
    val contentHash: String?,
    val hashVersion: Int?,
    val parseRunId: String?,
    val confirmedAt: Long,
    val acceptedPartialResult: Boolean,
    val origin: RecordOrigin = RecordOrigin.MARKDOWN,
    val kind: RecordKind = RecordKind.TRAINING,
) {
    init {
        require(java.util.UUID.fromString(id).toString() == id)
        if (origin == RecordOrigin.NATIVE) {
            require(vaultId == null && relPath == null && contentHash == null && hashVersion == null && parseRunId == null)
        } else {
            SourceKey(requireNotNull(vaultId), requireNotNull(relPath))
            DiaryContentVersion(requireNotNull(contentHash), requireNotNull(hashVersion))
        }
    }
}

@Serializable
data class ConfirmedSession(val id: String, val diaryId: String, val position: Int,
    val notes: String?, val sourcePath: String?)

@Serializable
data class ConfirmedExercise(
    val id: String, val sessionId: String, val position: Int,
    val rawName: String, val notes: String?, val sourcePath: String?,
    val evidence: EvidenceQuote?,
)

@Serializable
data class ConfirmedSet(
    val id: String, val exerciseId: String, val position: Int,
    val weight: Double?, val unit: WeightUnit?, val basis: WeightBasis?, val reps: Int?, val weightKg: Double?,
    val groupIndex: Int?, val setInGroup: Int?, val userEdited: Boolean,
)

@Serializable
data class ConfirmedExerciseRecord(val exercise: ConfirmedExercise,
    val sets: List<ConfirmedSet>)

@Serializable
data class ConfirmedSessionRecord(val session: ConfirmedSession,

    val exercises: List<ConfirmedExerciseRecord>)

@Serializable
data class TrainingRecordDetails(val diary: TrainingRecord,

    val sessions: List<ConfirmedSessionRecord>) {
    internal fun ordered() = copy(sessions = sessions.sortedBy { it.session.position }.map { session ->
        session.copy(exercises = session.exercises.sortedBy { it.exercise.position }.map { exercise ->
            exercise.copy(sets = exercise.sets.sortedBy { it.position })
        })
    })
}
