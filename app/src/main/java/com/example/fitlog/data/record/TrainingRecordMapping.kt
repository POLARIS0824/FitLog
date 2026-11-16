package com.example.fitlog.data.record

import com.example.fitlog.model.record.ConfirmedExercise
import com.example.fitlog.model.record.ConfirmedExerciseRecord
import com.example.fitlog.model.record.ConfirmedSession
import com.example.fitlog.model.record.ConfirmedSessionRecord
import com.example.fitlog.model.record.ConfirmedSet
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.TrainingRecordDetails

internal fun TrainingRecordRow.toRecord() = TrainingRecord(id, vaultId, relPath, date, contentHash,
    hashVersion, parseRunId, confirmedAt, acceptedPartialResult, origin, kind)

internal fun TrainingRecord.toRow() = TrainingRecordRow(id, vaultId, relPath, date, contentHash,
    hashVersion, parseRunId, confirmedAt, acceptedPartialResult, origin, kind)

internal fun TrainingRecordGraph.toRecord() = TrainingRecordDetails(diary.toRecord(), sessions.map { session ->
    ConfirmedSessionRecord(ConfirmedSession(session.session.id, session.session.diaryId, session.session.position,
        session.session.notes, session.session.sourcePath), session.exercises.map { exercise ->
        ConfirmedExerciseRecord(ConfirmedExercise(exercise.exercise.id, exercise.exercise.sessionId,
            exercise.exercise.position, exercise.exercise.rawName, exercise.exercise.notes,
            exercise.exercise.sourcePath, exercise.exercise.evidence), exercise.sets.map { set ->
            ConfirmedSet(set.id, set.exerciseId, set.position, set.weight, set.unit, set.basis, set.reps,
                set.weightKg, set.groupIndex, set.setInGroup, set.userEdited)
        })
    })
}).ordered()
