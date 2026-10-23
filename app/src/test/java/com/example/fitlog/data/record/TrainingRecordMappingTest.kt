package com.example.fitlog.data.record

import com.example.fitlog.model.analysis.EvidenceQuote
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import org.junit.Assert.*
import org.junit.Test

class TrainingRecordMappingTest {
    @Test fun readingARelationGraphKeepsProvenanceAndOrdersEveryLevel() {
        val id = "00000000-0000-0000-0000-000000000002"
        val row = TrainingRecordRow(id, "00000000-0000-0000-0000-000000000001", "2026-10-22.md",
            "2026-10-22", "a".repeat(64), 1, "parse-run", 42, true)
        val evidence = EvidenceQuote(quote = "Squat")
        fun session(position: Int) = ConfirmedSessionGraph(
            ConfirmedSessionRow("session-$position", id, position, "notes-$position", "sessions[$position]"),
            listOf(1, 0).map { exercise ->
                val exerciseId = "$position-exercise-$exercise"
                ConfirmedExerciseGraph(ConfirmedExerciseRow(exerciseId, "session-$position", exercise,
                    "Squat", null, "sessions[$position].exercises[$exercise]", evidence),
                    listOf(1, 0).map { set -> ConfirmedSetRow("$exerciseId-set-$set", exerciseId, set,
                        null, WeightUnit.UNKNOWN, WeightBasis.ASSISTED, 8, null, 2, set, true) })
            })

        val result = TrainingRecordGraph(row, listOf(session(1), session(0))).toRecord()

        assertEquals(RecordOrigin.MARKDOWN, result.diary.origin)
        assertEquals(row, result.diary.toRow())
        assertEquals(listOf(0, 1), result.sessions.map { it.session.position })
        result.sessions.forEach { session ->
            assertEquals(listOf(0, 1), session.exercises.map { it.exercise.position })
            session.exercises.forEach { exercise ->
                assertEquals(evidence, exercise.exercise.evidence)
                assertEquals(listOf(0, 1), exercise.sets.map { it.position })
                assertTrue(exercise.sets.all { it.weight == null && it.weightKg == null && it.userEdited })
                assertEquals(listOf(0, 1), exercise.sets.map { it.setInGroup })
            }
        }
    }
}
