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

    @Test fun invalidDatesAreReviewHintsAndDoNotEraseTrainingOrNotes() {
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
            assertFalse(result.hasErrors)
            assertTrue(result.issues.any { it.code == ValidationCode.INVALID_DATE && it.severity == IssueSeverity.REVIEW })
        }
    }

    @Test fun dateConflictsAreResolvedDuringConfirmationAndMissingDatesAreNotInvented() {
        val result = analyzeFixture(fullInput("training"), diaryCandidateJson.encodeToString(DiaryCandidate(1, listOf(
            SessionCandidate("2026-09-29", exercises = emptyList()),
            SessionCandidate("2026-09-30", exercises = emptyList()),
            SessionCandidate(exercises = emptyList()),
            SessionCandidate("2026-13-40", exercises = emptyList()),
        ))))
        assertFalse(result.issues.any { it.code == ValidationCode.DATE_CONFLICT || it.code == ValidationCode.MISSING_DATE })
        assertTrue(suggestDiaryDate(result.parseKey.sourceKey, result).requiresReview)
        assertNull(result.sessions[2].date)
    }
}
