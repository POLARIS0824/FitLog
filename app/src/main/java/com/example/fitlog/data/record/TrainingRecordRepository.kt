package com.example.fitlog.data.record

import androidx.room.withTransaction
import com.example.fitlog.data.local.FitLogDao
import com.example.fitlog.data.local.FitLogDatabase
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.ReviewedSession
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local facts shared by native entry, Markdown confirmation and statistics. */
class TrainingRecordRepository internal constructor(private val database: FitLogDatabase,
    private val computationDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis) : TrainingRecords {
    private val dao = database.analysis()
    override fun observeRecords() = dao.observeRecords().map { records -> records.map { it.toRecord() } }.flowOn(computationDispatcher)
    override suspend fun read(id: String) = withContext(computationDispatcher) { dao.record(id)?.toRecord() }

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
internal suspend fun replaceRecord(dao: FitLogDao, record: TrainingRecord,
    sessions: List<ReviewedSession>, exists: Boolean) {
    if (exists) {
        check(dao.updateDiary(record.toRow()) == 1)
        dao.deleteSessions(record.id)
    } else dao.insertDiary(record.toRow())
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
