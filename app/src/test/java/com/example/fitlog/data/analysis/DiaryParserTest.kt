package com.example.fitlog.data.analysis

import com.example.fitlog.data.analysis.adapter.DiaryModelResponse
import com.example.fitlog.data.analysis.adapter.DiaryModelSource
import com.example.fitlog.data.analysis.adapter.JsonDiaryParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DiaryParserTest {
    @Test fun expectedRequestFailuresStayFailuresAndOnlyNormalizedTextReachesSource() = runTest {
        val input = fullInput("\uFEFFprivate\r\ntraining\rnotes")
        listOf(DiaryParseFailure.MODEL_REFUSAL, DiaryParseFailure.TIMEOUT, DiaryParseFailure.NETWORK_ERROR).forEach { reason ->
            var received: String? = null
            val parser: DiaryParser = JsonDiaryParser(DiaryModelSource {
                received = it
                DiaryModelResponse.Failure(reason)
            })
            assertEquals(DiaryParseResult.Failure(reason), parser.parse(input))
            assertEquals(input.text, received)
        }
    }

    @Test fun cancellationIsNeverConvertedToAnOutcome() = runTest {
        val cancellation = CancellationException("test cancellation")
        val parser: DiaryParser = JsonDiaryParser(DiaryModelSource { throw cancellation })
        try { parser.parse(fullInput("raw")); fail() } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
    }

    @Test fun programmingErrorsAreNotHiddenAsFailuresOrEmptySuccesses() = runTest {
        val defect = IllegalStateException("test defect")
        val parser: DiaryParser = JsonDiaryParser(DiaryModelSource { throw defect })
        try { parser.parse(fullInput("raw")); fail() } catch (caught: IllegalStateException) {
            assertSame(defect, caught)
        }
    }

    @Test fun localErrorsRemainSuccessfulCandidateExtractionWithHealthySiblings() = runTest {
        val parser: DiaryParser = FixtureDiaryParser(candidateJson(
            exercise("good 40kg 1x8", SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1)),
            exercise("bad -2kg 1x8", SetGroupCandidate("-2kg 1x8", -2.0, WeightUnit.KG, reps = 8, count = 1)),
        ))
        val input = fullInput("good 40kg 1x8\nbad -2kg 1x8")
        val result = parser.parse(input)
        assertTrue(result is DiaryParseResult.Success)
        val analysis = (result as DiaryParseResult.Success).analysis
        assertEquals(input.parseKey, analysis.parseKey)
        assertTrue(analysis.hasErrors)
        assertEquals(1, analysis.sessions.single().exercises.sumOf { it.sets.size })
        assertTrue(analysis.issues.any { it.code == ValidationCode.INVALID_WEIGHT })
    }
}
