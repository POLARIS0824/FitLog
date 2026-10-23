package com.example.fitlog.data.analysis

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface TrainingRecords {
    fun observeRecords(): kotlinx.coroutines.flow.Flow<List<TrainingRecordDetails>>
    suspend fun read(id: String): TrainingRecordDetails?
    suspend fun saveNative(id: String, date: LocalDate, sessions: List<ReviewedSession>, kind: RecordKind)
    suspend fun delete(id: String)
    suspend fun deleteConfirmation(key: SourceKey)
}

/** Local facts shared by native entry, Markdown confirmation and statistics. */
class TrainingRecordRepository(private val database: DiaryAnalysisDatabase,
    private val computationDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis) : TrainingRecords {
    private val dao = database.analysis()
    override fun observeRecords() = dao.observeRecords().map { records -> records.map { it.ordered() } }.flowOn(computationDispatcher)
    override suspend fun read(id: String) = withContext(computationDispatcher) { dao.record(id)?.ordered() }

    override suspend fun saveNative(id: String, date: LocalDate, sessions: List<ReviewedSession>, kind: RecordKind) {
        database.withTransaction {
            val previous = dao.record(id)
            require(previous == null || previous.diary.origin == RecordOrigin.NATIVE)
            val record = TrainingRecord(id, null, null, date.toString(), null, null, null,
                maxOf(now(), (previous?.diary?.confirmedAt ?: 0L) + 1), false,
                RecordOrigin.NATIVE, kind)
            replaceRecord(dao, record, sessions, previous != null)
        }
    }

    override suspend fun delete(id: String): Unit = database.withTransaction { dao.deleteRecord(id) }
    override suspend fun deleteConfirmation(key: SourceKey): Unit = database.withTransaction {
        dao.confirmed(key.vaultId, key.relPath)?.let { dao.deleteRecord(it.diary.id) }
    }
}

/** Call inside a Room transaction; all entry paths write the same record graph. */
internal suspend fun replaceRecord(dao: DiaryAnalysisDao, record: TrainingRecord,
    sessions: List<ReviewedSession>, exists: Boolean) {
    if (exists) {
        check(dao.updateDiary(record) == 1)
        dao.deleteSessions(record.id)
    } else dao.insertDiary(record)
    sessions.forEachIndexed { sessionIndex, session ->
        val storedSession = ConfirmedSessionRow(UUID.randomUUID().toString(), record.id, sessionIndex, session.notes, session.sourcePath)
        dao.insertSession(storedSession)
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            val storedExercise = ConfirmedExerciseRow(UUID.randomUUID().toString(), storedSession.id, exerciseIndex,
                exercise.rawName, exercise.notes, exercise.sourcePath, exercise.evidence)
            dao.insertExercise(storedExercise)
            dao.insertSets(exercise.sets.mapIndexed { index, set ->
                val kg = when (set.unit) {
                    WeightUnit.KG -> set.weight
                    WeightUnit.LB -> set.weight?.times(0.45359237)
                    else -> null
                }
                ConfirmedSetRow(UUID.randomUUID().toString(), storedExercise.id, index, set.weight, set.unit,
                    set.basis, set.reps, kg, set.groupIndex, set.setInGroup, set.userEdited)
            })
        }
    }
}

fun TrainingRecordDetails.reviewedSessions() = sessions.map { session ->
    ReviewedSession(session.exercises.map { exercise ->
        ReviewedExercise(exercise.exercise.rawName, exercise.sets.map { set ->
            ReviewedSet(set.weight, set.unit, set.basis, set.reps, set.groupIndex, set.setInGroup, set.userEdited)
        }, exercise.exercise.notes, exercise.exercise.evidence, exercise.exercise.sourcePath)
    }, session.session.notes, session.session.sourcePath)
}
