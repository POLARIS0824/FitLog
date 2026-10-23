package com.example.fitlog.domain

import com.example.fitlog.data.analysis.*
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Business validation is independent of Room, Android and Compose. */
internal class SaveNativeRecord(private val records: TrainingRecords,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default) {
    suspend operator fun invoke(id: String, date: LocalDate, sessions: List<ReviewedSession>, kind: RecordKind) = withContext(computationDispatcher) {
        require(date.year in 1..9999)
        validateRecordValues(sessions, allowIncomplete = true)
        require(kind != RecordKind.REST || sessions.all { it.exercises.isEmpty() })
        records.saveNative(id, date, sessions, kind)
    }
}

internal fun validateRecordValues(sessions: List<ReviewedSession>, allowIncomplete: Boolean = false) {
    sessions.forEach { session -> session.exercises.forEach { exercise ->
        require(allowIncomplete || exercise.rawName.isNotBlank())
        require(exercise.sets.size <= 1000)
        exercise.sets.forEach { set ->
            require(set.weight == null || (set.weight.isFinite() && set.weight >= 0))
            require(set.reps == null || set.reps > 0)
        }
    } }
}

/** Validate final values and source references, deriving the group-level edit flag locally. */
internal fun normalizeReview(request: DiaryConfirmation, analysis: DiaryAnalysis): List<ReviewedSession> {
    require(request.date.year in 1..9999)
    val sessionPaths = mutableSetOf<String>()
    val exercisePaths = mutableSetOf<String>()
    val originalExercises = analysis.sessions.flatMap { it.exercises }.associateBy { it.path }
    return request.sessions.map { session ->
        session.sourcePath?.let { path ->
            require(sessionPaths.add(path))
            requireNotNull(analysis.sessions.find { it.path == path })
        }
        session.copy(exercises = session.exercises.map { exercise ->
            require(exercise.rawName.isNotBlank())
            require(exercise.sets.size <= 1000)
            val original = exercise.sourcePath?.let { path ->
                require(exercisePaths.add(path))
                requireNotNull(originalExercises[path])
            }
            exercise.evidence?.let {
                require(it.segmentId == DiaryParseInput.SEGMENT_ID && it.quote.isNotBlank())
            }
            val sourceSets = mutableSetOf<Pair<Int, Int>>()
            exercise.copy(sets = exercise.sets.map { set ->
                require(set.weight == null || (set.weight.isFinite() && set.weight >= 0))
                require(set.reps == null || set.reps > 0)
                require((set.groupIndex == null) == (set.setInGroup == null))
                val baseline = if (set.groupIndex != null && set.setInGroup != null) {
                    require(sourceSets.add(set.groupIndex to set.setInGroup))
                    requireNotNull(original?.sets?.find {
                        it.groupIndex == set.groupIndex && it.setInGroup == set.setInGroup
                    })
                } else null
                set.copy(userEdited = baseline == null || set.weight != baseline.weight.value ||
                    set.unit != baseline.unit.value || set.basis != baseline.basis.value || set.reps != baseline.reps.value)
            })
        })
    }
}
