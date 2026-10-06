package com.example.fitlog.data.analysis

import com.example.fitlog.data.hash.contentTextHash
import org.junit.Assert.*
import org.junit.Test

class DiaryParseInputTest {
    @Test fun inputNormalizesWholeFileOnlyOnceIncludingTwoLeadingBoms() {
        val raw = "\uFEFF\uFEFFprivate\r\ntraining\rnotes"
        val input = fullInput(raw)
        val again = fullInput(raw)
        assertEquals("\uFEFFprivate\ntraining\nnotes", input.text)
        assertEquals(contentTextHash(raw), input.parseKey.contentHash)
        assertEquals(input.text, again.text)
        assertEquals(input.parseKey, again.parseKey)
        assertNotEquals(contentTextHash(input.text), input.parseKey.contentHash)
    }

    @Test fun modelResultUsesTheInputKeyAndEmptyFilesAreAllowed() {
        val input = fullInput("")
        val outcome = parseFixture(input, """{"schemaVersion":1,"sessions":[]}""")
        assertTrue(outcome is DiaryParseResult.Success)
        val result = (outcome as DiaryParseResult.Success).analysis
        assertEquals(input.parseKey, result.parseKey)
        assertTrue(result.sessions.isEmpty())
        assertFalse(result.hasErrors)
    }

    @Test fun evidenceMatchesNormalizedTextWithBomNewlinesAndEmoji() {
        val raw = "\uFEFFprivate\ud83d\ude42\r\n- bench: 40kg 1x8\r\nnotes"
        val input = fullInput(raw)
        val quote = "- bench: 40kg 1x8"
        val result = analyzeFixture(input, candidateJson(exercise(quote,
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1))))
        val evidence = result.sessions.single().exercises.single().evidence
        assertEquals(quote, evidence.quote)
        assertTrue(input.text.contains(evidence.quote))
    }

    @Test fun multilineQuotesAndGroupTextMatchAfterOnlyNewlineNormalization() {
        val input = fullInput("header\r\nbench:\r40kg\r\n1x8\rnotes")
        val quote = "bench:\r\n40kg\r1x8"
        val group = "40kg\r\n1x8"
        val result = analyzeFixture(input, candidateJson(exercise(quote,
            SetGroupCandidate(group, 40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1))))
        assertFalse(result.hasErrors)
        val exercise = result.sessions.single().exercises.single()
        assertEquals("bench:\n40kg\n1x8", exercise.evidence.quote)
        assertEquals(group, exercise.candidate.groups.single().rawText)
        assertEquals(1, exercise.sets.size)
        assertTrue(input.text.contains(exercise.evidence.quote))
    }

    @Test fun repeatedExerciseEvidenceRetainsBothCandidatesWithoutChoosingLocations() {
        val result = analyzeFixture(fullInput("bench 40kg 1x8\nbench 40kg 1x8"), candidateJson(
            exercise("bench 40kg 1x8", SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1)),
            exercise("bench 40kg 1x8", SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1)),
        ))
        assertFalse(result.hasErrors)
        assertEquals(2, result.sessions.single().exercises.size)
        assertEquals(2, result.sessions.single().exercises.sumOf { it.sets.size })
        assertTrue(result.sessions.single().exercises.all { it.evidence.quote == "bench 40kg 1x8" })
        assertEquals(2, result.issues.count { it.code == ValidationCode.AMBIGUOUS_EVIDENCE && it.severity == IssueSeverity.REVIEW })
    }

    @Test fun evidenceMatchingDoesNotTrimWhitespaceOrFoldUnicodeOrStripAnExcerptBom() {
        listOf("bench" to " bench", "\u00e9" to "e\u0301", "bench" to "\uFEFFbench").forEach { (text, quote) ->
            val result = analyzeFixture(fullInput(text), candidateJson(exercise(quote)))
            assertTrue(result.hasErrors)
            assertTrue(result.issues.any { it.code == ValidationCode.EVIDENCE_NOT_FOUND })
        }
    }
}
