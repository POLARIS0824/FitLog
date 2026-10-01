package com.example.fitlog.data.analysis

import org.junit.Assert.*
import org.junit.Test

class DiaryCandidateCodecTest {

    @Test fun unknownFieldsAreIgnoredAndEnumLabelsDoNotEraseEntry() {
        val raw = """{"schemaVersion":1,"futureRoot":true,"sessions":[{"exercises":[{
            "rawName":"动作","futureExercise":{"value":3},
            "evidence":{"segmentId":"diary","quote":"raw"},
            "groups":[{"rawText":"raw","weight":20,"unit":"STONE","basis":"FUTURE","count":1,"reps":8}]
        }]}]}"""
        val result = analyzeFixture(fullInput("raw"), raw)
        assertFalse(result.hasErrors)
        val exercise = result.sessions.single().exercises.single()
        assertEquals(WeightUnit.UNKNOWN, exercise.candidate.groups.single().unit)
        assertEquals(WeightBasis.UNKNOWN, exercise.candidate.groups.single().basis)
        assertNull(exercise.sets.single().weightKg)
        assertTrue(result.issues.any { it.code == ValidationCode.MISSING_UNIT })
    }

    @Test fun malformedGroupDoesNotEraseSiblingsOrTheirOriginalIndices() {
        val raw = """{"schemaVersion":1,"sessions":[{"exercises":[{
            "rawName":"动作","evidence":{"segmentId":"diary","quote":"40kg 1x8 + bad + 1x6"},
            "groups":[
                {"rawText":"40kg 1x8","weight":40,"unit":"KG","count":1,"reps":8},
                {"rawText":"bad","count":"bad"},
                {"rawText":"1x6","count":1,"reps":6}
            ]
        }]}]}"""
        val result = analyzeFixture(fullInput("40kg 1x8 + bad + 1x6"), raw)
        assertTrue(result.hasErrors)
        val sets = result.sessions.single().exercises.single().sets
        assertEquals(listOf(0, 2), sets.map { it.groupIndex })
        assertEquals(listOf(40.0, null), sets.map { it.weight.value })
        assertTrue(result.issues.any {
            it.path == "$.sessions[0].exercises[0].groups[1]" && it.code == ValidationCode.INVALID_FIELD
        })
    }

    @Test fun malformedSessionAndExerciseDoNotRenumberHealthySiblings() {
        val raw = """{"schemaVersion":1,"sessions":[
            {"date":{},"exercises":[]},
            {"exercises":[
                {"rawName":null,"evidence":{"segmentId":"diary","quote":"raw"}},
                {"rawName":"动作","evidence":{"segmentId":"diary","quote":"raw"}}
            ]}
        ]}"""
        val result = analyzeFixture(fullInput("raw"), raw)
        assertTrue(result.hasErrors)
        assertEquals("$.sessions[1]", result.sessions.single().path)
        assertEquals("$.sessions[1].exercises[1]", result.sessions.single().exercises.single().path)
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

    @Test fun malformedIssuesAndChildArraysAreReportedWithoutErasingGoodExercise() {
        val raw = """{"schemaVersion":1,"issues":[{"path":"x"},{"path":"x","question":"核对"}],
            "sessions":[{"exercises":"bad"},{"exercises":[
                {"rawName":"动作","evidence":{"segmentId":"diary","quote":"raw"},"groups":"bad"}
            ]}]}"""
        val result = analyzeFixture(fullInput("raw"), raw)
        assertTrue(result.hasErrors)
        assertEquals(2, result.sessions.size)
        assertEquals(1, result.sessions[1].exercises.size)
        assertEquals(listOf(CandidateIssue("x", "核对")), result.modelIssues)
        assertTrue(result.issues.any { it.path == "$.issues[0]" })
        assertTrue(result.issues.any { it.path == "$.sessions[0].exercises" })
        assertTrue(result.issues.any { it.path == "$.sessions[1].exercises[0].groups" })
    }
}
