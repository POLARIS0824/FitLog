package com.example.fitlog.data.analysis

import org.junit.Assert.*
import org.junit.Test

class DiaryCandidateCodecTest {
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
        assertTrue(result.issues.any { it.code == ValidationCode.MISSING_UNIT })
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

    @Test fun decodedSemanticErrorsAndMissingValuesRemainReviewable() {
        val analysis = analyzeFixture(fullInput("raw"), """{"schemaVersion":1,"sessions":[{"exercises":[
            {"rawName":"good","evidence":{"segmentId":"diary","quote":"raw"}},
            {"rawName":"missing","evidence":{"segmentId":"diary","quote":"absent"}}
        ]}]}""")
        assertTrue(analysis.hasErrors)
        assertEquals("good", analysis.sessions.single().exercises.single().candidate.rawName)
        assertTrue(analysis.issues.any { it.code == ValidationCode.MISSING_COUNT })
    }
}
