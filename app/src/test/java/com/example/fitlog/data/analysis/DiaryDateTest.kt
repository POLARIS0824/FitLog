package com.example.fitlog.data.analysis

import com.example.fitlog.data.analysis.adapter.diaryCandidateJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class DiaryDateTest {
    @Test fun sameDayMultipleSessionsShareOneFileKeyWithoutDateConflict() {
        val input = fullInput("training")
        val result = analyzeFixture(input, diaryCandidateJson.encodeToString(DiaryCandidate(1, listOf(
            SessionCandidate("2026-09-30", exercises = emptyList()),
            SessionCandidate("2026-09-30", exercises = emptyList()),
        ))))
        assertEquals(input.parseKey, result.parseKey)
        assertEquals(2, result.sessions.size)
        assertFalse(result.issues.any { it.code == ValidationCode.DATE_CONFLICT })
    }

    @Test fun strictInvalidDatesAreLocalErrorsAndDoNotEraseOtherData() {
        listOf("2026-13-40", "2026-02-30", "2025-02-29", "2026-9-30", "2026-09-30T10:00:00").forEach { date ->
            val response = diaryCandidateJson.encodeToString(DiaryCandidate(1, listOf(
                SessionCandidate(date, "keep notes", listOf(exercise("training"))),
            )))
            val outcome = parseFixture(fullInput("training"), response)
            assertTrue(outcome is DiaryParseResult.Success)
            val result = (outcome as DiaryParseResult.Success).analysis
            assertNull(result.sessions.single().date)
            assertEquals("keep notes", result.sessions.single().notes)
            assertEquals(1, result.sessions.single().exercises.size)
            assertTrue(result.issues.any { it.code == ValidationCode.INVALID_DATE && it.severity == IssueSeverity.ERROR })
        }
    }

    @Test fun onlyDifferentValidDatesConflictAndMissingDatesAreNotInvented() {
        val result = analyzeFixture(fullInput("training"), diaryCandidateJson.encodeToString(DiaryCandidate(1, listOf(
            SessionCandidate("2026-09-29", exercises = emptyList()),
            SessionCandidate("2026-09-30", exercises = emptyList()),
            SessionCandidate(exercises = emptyList()),
            SessionCandidate("2026-13-40", exercises = emptyList()),
        ))))
        val conflicts = result.issues.filter { it.code == ValidationCode.DATE_CONFLICT }
        assertEquals(listOf("$.sessions[0].date", "$.sessions[1].date"), conflicts.map { it.path })
        assertTrue(conflicts.all { it.severity == IssueSeverity.REVIEW })
        assertNull(result.sessions[2].date)
        assertTrue(result.issues.any { it.path == "$.sessions[2].date" && it.code == ValidationCode.MISSING_DATE })
    }
}
