package com.example.fitlog.data.analysis

import com.example.fitlog.model.record.ExerciseAliases
import com.example.fitlog.model.record.WeightBasis
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateStorageCompatibilityTest {
    @Test fun candidateVersionTwoRetainsItsStoredFieldsAndValues() {
        val stored = checkNotNull(javaClass.getResource("/analysis/candidate-v2.json")).readText()
        val analysis = DiaryAnalysisCodec.decodeCandidate(stored)
        assertEquals(LocalDate.of(2026, 10, 22), analysis.sessions.single().date)
        assertEquals(WeightBasis.TOTAL, analysis.sessions.single().exercises.single().sets.single().basis.value)
        assertEquals(Json.parseToJsonElement(stored), Json.parseToJsonElement(DiaryAnalysisCodec.encodeCandidate(analysis)))
    }

    @Test fun aliasesRetainTheirExistingJsonFormat() {
        val stored = """{"groups":[{"primaryName":"Squat","members":["squat","back squat"]}]}"""
        val aliases = Json.decodeFromString<ExerciseAliases>(stored).validate()
        assertEquals("squat", aliases.groupFor("back squat")?.primaryKey)
        assertEquals(Json.parseToJsonElement(stored), Json.parseToJsonElement(Json.encodeToString(aliases)))
    }
}
