package com.example.fitlog.domain.record

import com.example.fitlog.data.record.TrainingRecords
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.ReviewedSession
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
