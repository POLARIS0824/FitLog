package com.example.fitlog.data.analysis

import org.junit.Assert.*
import org.junit.Test

class DiaryCandidateCodecTest {
    @Test fun explicitBodyweightMissingWeightAddedAndAssistedLoadsRemainDifferent() {
        val analysis = analyzeFixture(fullInput("自重引体向上 1x8\n俯卧撑 1x8\n负重引体 10kg 1x6\n辅助引体 20kg 1x8"),
            """{"schemaVersion":1,"sessions":[{"exercises":[
                {"rawName":"自重引体向上","groups":[{"basis":"BODYWEIGHT","count":1,"reps":8}]},
                {"rawName":"俯卧撑","groups":[{"count":1,"reps":8}]},
                {"rawName":"负重引体","groups":[{"basis":"ADDED","weight":10,"unit":"KG","count":1,"reps":6}]},
                {"rawName":"辅助引体","groups":[{"basis":"ASSISTED","weight":20,"unit":"KG","count":1,"reps":8}]}
            ]}]}""")
        val sets = analysis.sessions.single().exercises.map { it.sets.single() }
        assertNull(sets[0].weight.value); assertNull(sets[0].weightKg)
        assertEquals(WeightBasis.BODYWEIGHT, sets[0].basis.value)
        assertNull(sets[1].weight.value); assertTrue(sets[1].basis.value in listOf(null, WeightBasis.UNKNOWN))
        assertEquals(WeightBasis.ADDED, sets[2].basis.value); assertEquals(10.0, sets[2].weight.value!!, 0.0)
        assertEquals(WeightBasis.ASSISTED, sets[3].basis.value); assertEquals(20.0, sets[3].weight.value!!, 0.0)
    }

    @Test fun unknownFieldsAreIgnoredAndUnknownValuesStayExplicitlyUnknown() {
        val raw = """{"schemaVersion":1,"futureRoot":true,"sessions":[{"exercises":[{
            "rawName":"exercise","futureExercise":{"value":3},
            "evidence":{"segmentId":"diary","quote":"raw"},
            "groups":[{"rawText":"raw","weight":20,"unit":"UNKNOWN","basis":"UNKNOWN","count":1,"reps":8}]
        }]}]}"""
        val result = analyzeFixture(fullInput("raw"), raw)
        assertFalse(result.hasErrors)
        val exercise = result.sessions.single().exercises.single()
        assertNull(exercise.sets.single().weightKg)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun malformedChildrenFailTheWholeResponseRatherThanReturningHealthySiblings() {
        val invalid = listOf(
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"}},{"rawName":null}]}]}""",
            """{"schemaVersion":1,"sessions":[{"date":{},"exercises":[]},{"exercises":[]}]}""",
            """{"schemaVersion":1,"sessions":[{"exercises":"bad"}]}""",
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"},"groups":"bad"}]}]}""",
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"},"groups":[{"rawText":"raw","count":"bad"},{"rawText":"raw","count":1,"reps":8}]}]}]}""",
            """{"schemaVersion":1,"sessions":[],"issues":[{"path":"x"},{"path":"x","question":"review"}]}""",
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"},"groups":null}]}]}""",
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"},"groups":[{"rawText":"raw","unit":"STONE"}]}]}]}""",
        )
        invalid.forEach { assertEquals(it, DiaryParseResult.Failure(DiaryParseFailure.INVALID_RESPONSE), parseFixture(fullInput("raw"), it)) }
    }

    @Test fun malformedJsonRootAndSchemaAreFailuresRatherThanEmptySuccesses() {
        val invalid = listOf(
            "{" to DiaryParseFailure.MALFORMED_JSON,
            "[]" to DiaryParseFailure.INVALID_TOP_LEVEL,
            "null" to DiaryParseFailure.INVALID_TOP_LEVEL,
            """{"schemaVersion":2,"sessions":[]}""" to DiaryParseFailure.UNSUPPORTED_SCHEMA,
            """{"schemaVersion":"1","sessions":[]}""" to DiaryParseFailure.INVALID_TOP_LEVEL,
            """{"sessions":[]}""" to DiaryParseFailure.INVALID_TOP_LEVEL,
            """{"schemaVersion":1}""" to DiaryParseFailure.INVALID_TOP_LEVEL,
            """{"schemaVersion":1,"sessions":null}""" to DiaryParseFailure.INVALID_TOP_LEVEL,
            """{"schemaVersion":1,"sessions":[],"issues":null}""" to DiaryParseFailure.INVALID_TOP_LEVEL,
            """{"schemaVersion":1,"sessions":[],"issues":{}}""" to DiaryParseFailure.INVALID_TOP_LEVEL,
        )
        invalid.forEach { (raw, reason) ->
            assertEquals(DiaryParseResult.Failure(reason), parseFixture(fullInput("raw"), raw))
        }
        val empty = parseFixture(fullInput(""), """{"schemaVersion":1,"sessions":[]}""")
        assertTrue(empty is DiaryParseResult.Success)
        assertTrue((empty as DiaryParseResult.Success).analysis.sessions.isEmpty())
        assertFalse(empty.analysis.hasErrors)
    }

    @Test fun missingGroupsAndMismatchedExcerptsRemainReviewableCandidates() {
        val analysis = analyzeFixture(fullInput("raw"), """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"}},
            {"rawName":"missing","evidence":{"segmentId":"diary","quote":"absent"}}
        ]}]}""")
        assertFalse(analysis.hasErrors)
        assertEquals(listOf("good", "missing"), analysis.sessions.single().exercises.map { it.candidate.rawName })
        assertTrue(analysis.sessions.single().exercises.all { it.sets.isEmpty() })
        assertEquals(1, analysis.issues.size)
        assertEquals(ValidationCode.EVIDENCE_NOT_FOUND, analysis.issues.single().code)
    }

    @Test fun auxiliaryTextsMayBeOmittedWithoutWarningsOrInventedValues() {
        val analysis = analyzeFixture(fullInput("自重引体向上 2x6"), """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"自重引体向上","groups":[{"basis":"BODYWEIGHT","count":2,"reps":6}]},
            {"rawName":"练腹","evidence":{"quote":""},"groups":[]}
        ]}]}""")
        assertTrue(analysis.issues.isEmpty())
        val exercises = analysis.sessions.single().exercises
        assertTrue(exercises.all { it.evidence == EvidenceQuote("diary", "") })
        assertEquals("", exercises.first().candidate.groups.single().rawText)
        assertEquals(2, exercises.first().sets.size)
        assertTrue(exercises.first().sets.all { it.weight.value == null && it.unit.value == null })
        assertTrue(exercises.last().sets.isEmpty())
    }
}
