package com.example.fitlog.data.analysis

import com.example.fitlog.data.analysis.adapter.diaryCandidateJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class DiaryCandidateValidatorTest {

    private fun validate(text: String, vararg groups: SetGroupCandidate): DiaryAnalysis =
        analyzeFixture(fullInput(text), candidateJson(exercise(text, *groups)))

    private fun DiaryAnalysis.sets(): List<ExpandedSet> = sessions.single().exercises.single().sets
    private fun DiaryAnalysis.has(code: ValidationCode) = issues.any { it.code == code }

    @Test fun missingWeightNeverInheritsFromAnotherExerciseOrSession() {
        val text = "A 40kg 1x8\nB 2x10\nC 1x5"
        val weighted = exercise("A 40kg 1x8",
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1))
        val missing = exercise("B 2x10", SetGroupCandidate("2x10", reps = 10, count = 2))
        val nextSession = exercise("C 1x5", SetGroupCandidate("1x5", reps = 5, count = 1))
        val raw = diaryCandidateJson.encodeToString(DiaryCandidate(1, listOf(
            SessionCandidate(exercises = listOf(weighted, missing)),
            SessionCandidate(exercises = listOf(nextSession)),
        )))
        val result = analyzeFixture(fullInput(text), raw)
        assertFalse(result.hasErrors)
        assertTrue(result.sessions[0].exercises[1].sets.all { it.weight.value == null })
        assertNull(result.sessions[1].exercises.single().sets.single().weight.value)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun newWeightWithoutUnitDoesNotBorrowPreviousUnit() {
        val result = validate("40kg 1x8 + 20 1x5",
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1),
            SetGroupCandidate("20 1x5", 20.0, reps = 5, count = 1),
        )
        assertFalse(result.hasErrors)
        assertEquals(20.0, result.sets().last().weight.value!!, 0.0)
        assertNull(result.sets().last().unit.value)
        assertNull(result.sets().last().weightKg)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun inferredValuesAreRetainedButNeverCarriedIntoLaterGroups() {
        val result = validate("约40kg 1x8 + 1x5",
            SetGroupCandidate("约40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1,
                inferredFields = listOf("weight")),
            SetGroupCandidate("1x5", reps = 5, count = 1),
        )
        assertEquals(CandidateOrigin.INFERRED, result.sets().first().weight.origin)
        assertEquals(CandidateOrigin.MISSING, result.sets().last().weight.origin)
        assertNull(result.sets().last().weight.value)
        assertNull(result.sets().last().unit.value)
        assertNull(result.sets().last().basis.value)
        assertFalse(result.sets().last().weight.inferred)
        assertNull(result.sets().last().weight.inheritedFromGroup)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun knownCountWithMissingRepsRetainsNullReps() {
        val result = validate("两组", SetGroupCandidate("两组", count = 2))
        assertFalse(result.hasErrors)
        assertEquals(2, result.sets().size)
        assertTrue(result.sets().all { it.reps.value == null && it.reps.origin == CandidateOrigin.MISSING })
        assertTrue(result.issues.isEmpty())
    }

    @Test fun missingCountNeverInventsOneSet() {
        val result = validate("做了8次", SetGroupCandidate("做了8次", reps = 8))
        assertFalse(result.hasErrors)
        assertTrue(result.sets().isEmpty())
        assertEquals(1, result.sessions.single().exercises.single().candidate.groups.size)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun nonexistentExcerptDoesNotDiscardExercisesOrSets() {
        val text = "好 2x8"
        val raw = candidateJson(
            exercise("不存在的摘录", SetGroupCandidate("1x9", reps = 9, count = 1)),
            exercise(text, SetGroupCandidate("2x8", reps = 8, count = 2)),
        )
        val result = analyzeFixture(fullInput(text), raw)
        assertFalse(result.hasErrors)
        val exercises = result.sessions.single().exercises
        assertEquals(listOf("$.sessions[0].exercises[0]", "$.sessions[0].exercises[1]"), exercises.map { it.path })
        assertEquals(listOf(1, 2), exercises.map { it.sets.size })
        assertEquals("不存在的摘录", exercises[0].evidence.quote)
        assertEquals(listOf(ValidationIssue("$.sessions[0].exercises[0].evidence.quote",
            ValidationCode.EVIDENCE_NOT_FOUND, IssueSeverity.REVIEW)), result.issues)
    }

    @Test fun repeatedQuoteIsAmbiguousInsteadOfChoosingFirstOccurrence() {
        val result = analyzeFixture(fullInput("练腹\n练腹"), candidateJson(exercise("练腹")))
        assertFalse(result.hasErrors)
        assertEquals(1, result.sessions.single().exercises.size)
        assertTrue(result.issues.any {
            it.code == ValidationCode.AMBIGUOUS_EVIDENCE && it.severity == IssueSeverity.REVIEW
        })
    }

    @Test fun groupDescriptionsNeverDetermineValidityOrReorderModelGroups() {
        val result = validate("40kg 1x8 + 20kg 1x6",
            SetGroupCandidate("20kg 1x6", 20.0, WeightUnit.KG, reps = 6, count = 1),
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1),
            SetGroupCandidate("编造的组", reps = 10, count = 1),
        )
        assertFalse(result.hasErrors)
        assertEquals(listOf(20.0, 40.0, null), result.sets().map { it.weight.value })
        assertEquals(listOf(6, 8, 10), result.sets().map { it.reps.value })
        assertTrue(result.issues.isEmpty())
    }

    @Test fun invalidGroupIsIsolatedAndOtherGroupsKeepTheirOwnValues() {
        val result = validate("40kg 1x8 + -2kg 1x5 + 1x4",
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, reps = 8, count = 1),
            SetGroupCandidate("-2kg 1x5", -2.0, WeightUnit.KG, reps = 5, count = 1),
            SetGroupCandidate("1x4", reps = 4, count = 1),
        )
        assertTrue(result.hasErrors)
        assertEquals(listOf(40.0, null), result.sets().map { it.weight.value })
        assertEquals(listOf(0, 2), result.sets().map { it.groupIndex })
        assertTrue(result.has(ValidationCode.INVALID_WEIGHT))
    }

    @Test fun invalidCountsAndRepsAreRejectedWithoutAllocatingHugeResults() {
        val invalid = listOf(
            SetGroupCandidate("raw", count = 0, reps = 8) to ValidationCode.INVALID_COUNT,
            SetGroupCandidate("raw", count = -1, reps = 8) to ValidationCode.INVALID_COUNT,
            SetGroupCandidate("raw", count = Int.MAX_VALUE, reps = 8) to ValidationCode.TOO_MANY_SETS,
            SetGroupCandidate("raw", count = 1, reps = 0) to ValidationCode.INVALID_REPS,
            SetGroupCandidate("raw", repsList = listOf(8, -1)) to ValidationCode.INVALID_REPS,
            SetGroupCandidate("raw", repsList = emptyList()) to ValidationCode.INVALID_REPS,
        )
        invalid.forEach { (group, code) ->
            val result = validate("raw", group)
            assertTrue(code.toString(), result.hasErrors)
            assertTrue(result.sets().isEmpty())
            assertTrue(result.has(code))
        }
    }

    @Test fun exerciseExpansionLimitAppliesAcrossGroups() {
        val result = validate("first + second",
            SetGroupCandidate("first", count = 600, reps = 8),
            SetGroupCandidate("second", count = 600, reps = 8),
        )
        assertTrue(result.hasErrors)
        assertEquals(600, result.sets().size)
        assertTrue(result.has(ValidationCode.TOO_MANY_SETS))
    }

    @Test fun repsListMustAgreeWithCountAndCannotCompeteWithScalarReps() {
        val invalid = listOf(
            SetGroupCandidate("raw", count = 2, repsList = listOf(8, 8, 6)),
            SetGroupCandidate("raw", reps = 8, repsList = listOf(8, 8, 6)),
        )
        invalid.forEach {
            val result = validate("raw", it)
            assertTrue(result.hasErrors)
            assertTrue(result.sets().isEmpty())
            assertTrue(result.has(ValidationCode.INCONSISTENT_REPS))
        }
        assertEquals(listOf(8, 8, 6),
            validate("raw", SetGroupCandidate("raw", count = 3, repsList = listOf(8, 8, 6)))
                .sets().map { it.reps.value })
    }

    @Test fun invalidDateIsNotReplacedByCurrentDateAndSiblingSessionSurvives() {
        val raw = """{"schemaVersion":1,"sessions":[
            {"date":"2026-02-30","notes":"状态一般","exercises":[]},
            {"date":"2026-09-30","exercises":[]}
        ]}"""
        val result = analyzeFixture(fullInput("状态一般"), raw)
        assertFalse(result.hasErrors)
        assertNull(result.sessions[0].date)
        assertEquals("状态一般", result.sessions[0].notes)
        assertEquals("2026-09-30", result.sessions[1].date.toString())
        assertTrue(result.issues.any { it.code == ValidationCode.INVALID_DATE && it.severity == IssueSeverity.REVIEW })
    }

    @Test fun emptySessionsMeanNoExtractedTrainingRatherThanInventedRestDay() {
        val result = analyzeFixture(
            fullInput("购买 ChatGPT Plus $20"),
            """{"schemaVersion":1,"sessions":[]}""",
        )
        assertFalse(result.hasErrors)
        assertTrue(result.sessions.isEmpty())
    }

    @Test fun unknownInferenceMetadataIsIgnoredAndUnknownExcerptSourceIsNonBlocking() {
        val result = validate("raw",
            SetGroupCandidate("raw", count = 1, reps = 8, inferredFields = listOf("futureField")))
        assertTrue(result.issues.isEmpty())
        val wrongSegment = analyzeFixture(
            fullInput("raw"), candidateJson(exercise("raw", segmentId = "not-selected")))
        assertFalse(wrongSegment.hasErrors)
        assertEquals(1, wrongSegment.sessions.single().exercises.size)
        assertTrue(wrongSegment.has(ValidationCode.UNKNOWN_SEGMENT))
    }

    @Test fun modelProvidedContextValuesArePreservedWithoutLocalInterpretation() {
        val result = validate("40kg 1x8 + 1x6",
            SetGroupCandidate(weight = 40.0, unit = WeightUnit.KG, basis = WeightBasis.PER_SIDE, count = 1, reps = 8),
            SetGroupCandidate(weight = 40.0, unit = WeightUnit.KG, basis = WeightBasis.PER_SIDE, count = 1, reps = 6),
        )
        assertEquals(listOf(40.0, 40.0), result.sets().map { it.weightKg })
        assertTrue(result.sets().all { it.weight.origin == CandidateOrigin.EXPLICIT && it.weight.inheritedFromGroup == null })
        assertTrue(result.issues.isEmpty())
    }

    @Test fun knownUnitAndMeaningSurviveMissingWeight() {
        val result = validate("自重", SetGroupCandidate(unit = WeightUnit.KG, basis = WeightBasis.BODYWEIGHT, count = 1))
        assertFalse(result.hasErrors)
        assertNull(result.sets().single().weight.value)
        assertNull(result.sets().single().weightKg)
        assertEquals(WeightUnit.KG, result.sets().single().unit.value)
        assertEquals(WeightBasis.BODYWEIGHT, result.sets().single().basis.value)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun emptyNameAndNonFiniteWeightsAreRejectedWithoutDroppingHealthySiblings() {
        val decoded = DiaryCandidate(1, listOf(SessionCandidate(exercises = listOf(
            ExerciseCandidate(" "),
            ExerciseCandidate("bench", groups = listOf(
                SetGroupCandidate(weight = Double.NaN, count = 1),
                SetGroupCandidate(weight = Double.POSITIVE_INFINITY, count = 1),
                SetGroupCandidate(weight = 0.0, unit = WeightUnit.KG, count = 1, reps = 8),
            )),
        ))))
        val result = com.example.fitlog.data.analysis.adapter.DiaryCandidateValidator().validate(fullInput(""), decoded)
        assertTrue(result.hasErrors)
        assertEquals(1, result.sessions.single().exercises.size)
        assertEquals(0.0, result.sets().single().weightKg!!, 0.0)
        assertEquals(2, result.issues.count { it.code == ValidationCode.INVALID_WEIGHT })
        assertTrue(result.has(ValidationCode.EMPTY_NAME))
    }
}
