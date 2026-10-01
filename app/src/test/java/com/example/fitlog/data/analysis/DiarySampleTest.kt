package com.example.fitlog.data.analysis

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DiarySampleTest {

    @Test fun userExcerptExpandsFourteenSetsAndKeepsUnspecifiedAbdominalActivity() = runTest {
        val input = fullInput(fixture("user-sample.md"))
        val parser = FixtureDiaryParser(fixture("user-sample.expected.json"))
        val outcome = parser.parse(input)
        assertTrue(outcome is DiaryParseResult.Success)
        val result = (outcome as DiaryParseResult.Success).analysis
        assertEquals(input.parseKey, result.parseKey)
        assertSame(input, parser.received)
        assertFalse(result.hasErrors)
        val session = result.sessions.single()
        assertNull(session.date)
        assertEquals(
            listOf("面拉", "哑铃平板卧推", "杠铃上斜卧推", "坐姿哑铃推肩", "练腹"),
            session.exercises.map { it.candidate.rawName },
        )
        assertEquals(14, session.exercises.sumOf { it.sets.size })
        assertTrue(session.exercises.last().sets.isEmpty())
        val incline = session.exercises[2].sets
        assertEquals(listOf(40.0, 40.0, 40.0), incline.map { it.weight.value })
        assertEquals(listOf(7, 7, 4), incline.map { it.reps.value })
        assertEquals(CandidateOrigin.INHERITED, incline.last().weight.origin)
        assertEquals(0, incline.last().weight.inheritedFromGroup)
        assertEquals(WeightBasis.TOTAL, incline.last().basis.value)
        val shoulder = session.exercises[3].sets
        assertEquals(listOf(17.5, 12.5, 12.5, 12.5), shoulder.map { it.weight.value })
        assertEquals(listOf(5, 8, 8, 8), shoulder.map { it.reps.value })
        assertTrue(shoulder.all { it.basis.value == WeightBasis.PER_SIDE })
        assertEquals(17.5, shoulder.first().weightKg!!, 0.0)
        assertEquals(1, result.modelIssues.size)
        assertFalse(session.exercises.any { it.candidate.rawName.contains("ChatGPT") })
    }

    @Test fun constructedFixtureMarksCrossDayClaimsForReviewAndKeepsSetsAndNotes() {
        val result = analyzeFixture(
            fullInput(fixture("constructed-boundaries.md")),
            fixture("constructed-boundaries.expected.json"),
        )
        assertFalse(result.hasErrors)
        assertEquals(listOf("2026-09-29", "2026-09-30"), result.sessions.map { it.date.toString() })
        assertEquals(2, result.issues.count {
            it.code == ValidationCode.DATE_CONFLICT && it.severity == IssueSeverity.REVIEW
        })
        assertEquals("状态一般", result.sessions[0].notes)
        val rows = result.sessions[0].exercises[0].sets
        assertEquals(listOf(8, 8, 6), rows.map { it.reps.value })
        assertEquals(60.0 * 0.45359237, rows[0].weightKg!!, 0.0000001)
        val bodyweight = result.sessions[1].exercises.single().sets
        assertEquals(2, bodyweight.size)
        assertTrue(bodyweight.all { it.weight.value == null && it.weightKg == null })
        assertTrue(bodyweight.all { it.basis.value == WeightBasis.BODYWEIGHT })
    }

    @Test fun notationVariantsPreserveExactQuotesAndExpandIdenticalGroups() {
        // Constructed spellings exercise the contract, not a fake claim of real NLP coverage.
        val variants = listOf("+" to "×", "＋" to "x", "➕" to "✖️")
        variants.forEach { (plus, times) ->
            val first = "40kg 2${times}7"
            val second = "1${times}4"
            val quote = "卧推：$first$plus$second"
            val response = candidateJson(exercise(quote,
                SetGroupCandidate(first, 40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 7, count = 2),
                SetGroupCandidate(second, reps = 4, count = 1),
            ))
            val result = analyzeFixture(fullInput(quote), response)
            assertFalse(result.hasErrors)
            val parsed = result.sessions.single().exercises.single()
            assertEquals(quote, parsed.evidence.quote)
            assertEquals(listOf(40.0, 40.0, 40.0), parsed.sets.map { it.weight.value })
            assertEquals(listOf(7, 7, 4), parsed.sets.map { it.reps.value })
        }
    }
}
