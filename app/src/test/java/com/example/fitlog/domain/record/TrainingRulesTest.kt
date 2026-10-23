@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.domain.record

import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.model.record.ReviewedSession
import com.example.fitlog.model.record.ReviewedSet
import com.example.fitlog.testing.FakeRecords
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TrainingRulesTest {
    @Test fun incompleteNativeInputIsPersistableButInvalidNumbersAreRejected() = runTest {
        val records = FakeRecords()
        val save = SaveNativeRecord(records)
        val id = UUID.randomUUID().toString()
        save(id, LocalDate.of(2026, 10, 22), listOf(ReviewedSession(listOf(ReviewedExercise("")))), RecordKind.TRAINING)
        assertEquals(1, records.rows.value.size)
        var rejected = false
        try {
            save(id, LocalDate.of(2026, 10, 22), listOf(ReviewedSession(listOf(ReviewedExercise("squat",
                listOf(ReviewedSet(weight = Double.NaN)))))), RecordKind.TRAINING)
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

}
