package com.example.fitlog.data.analysis

import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.vault.MarkdownSnapshot
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DiaryAnalysisContractTest {
    @Test fun documentSnapshotRestoresOnlyTheRemovedBomBeforeHashing() {
        val raw = "\uFEFF\uFEFFtraining\r\nnotes\r"
        val file = MarkdownFile("content://file", "note.md", "note.md", false, null)
        val snapshot = MarkdownSnapshot(file, raw.removePrefix("\uFEFF"), "byte fingerprint", true)
        val direct = fullInput(raw)
        val fromFile = DiaryParseInput.fromSnapshot(direct.parseKey.sourceKey, snapshot, "fixture-v1")
        assertEquals(direct.parseKey, fromFile.parseKey)
        assertEquals("\uFEFFtraining\nnotes\n", fromFile.text)
        assertEquals(direct.text, fromFile.text)
        val withoutBom = snapshot.copy(text = "training\r\nnotes", bom = false)
        assertEquals(fullInput(withoutBom.text).parseKey,
            DiaryParseInput.fromSnapshot(direct.parseKey.sourceKey, withoutBom, "fixture-v1").parseKey)
    }

    @Test fun candidateStorageRoundTripPreservesIssuesIndicesEvidenceAndInheritedInference() {
        val quote = "bench 40kg 1x8 + 1x6"
        val input = fullInput("private personal note\n$quote")
        val candidate = analyzeFixture(input, candidateJson(exercise(quote,
            SetGroupCandidate("40kg 1x8", 40.0, WeightUnit.KG, WeightBasis.PER_SIDE,
                reps = 8, count = 1, inferredFields = listOf("weight")),
            SetGroupCandidate("1x6", reps = 6, count = 1))))
        val restored = DiaryAnalysisCodec.decodeCandidate(DiaryAnalysisCodec.encodeCandidate(candidate))
        assertEquals(candidate, restored)
        val inherited = restored.sessions.single().exercises.single().sets.last().weight
        assertEquals(CandidateOrigin.INHERITED, inherited.origin)
        assertTrue(inherited.inferred)
        assertEquals(0, inherited.inheritedFromGroup)
        assertFalse(DiaryAnalysisCodec.encodeCandidate(candidate).contains("private personal note"))
    }

    @Test fun sampleAndPartialResultsRemainTypedAfterStorageRoundTrip() {
        val sample = analyzeFixture(fullInput(fixture("user-sample.md")), fixture("user-sample.expected.json"))
        assertEquals(sample, DiaryAnalysisCodec.decodeCandidate(DiaryAnalysisCodec.encodeCandidate(sample)))
        val partial = analyzeFixture(fullInput("good\nbad"),
            """{"schemaVersion":1,"sessions":[{"exercises":[{"rawName":"good","evidence":{"segmentId":"diary","quote":"good"}},{"rawName":false}]}]}""")
        assertTrue(partial.hasErrors)
        assertEquals(partial, DiaryAnalysisCodec.decodeCandidate(DiaryAnalysisCodec.encodeCandidate(partial)))
    }

    @Test fun unsupportedStorageVersionFailsInsteadOfInventingEmptyAnalysis() {
        val candidate = analyzeFixture(fullInput(""), """{"schemaVersion":1,"sessions":[]}""")
        val json = DiaryAnalysisCodec.encodeCandidate(candidate).replace("\"formatVersion\":1", "\"formatVersion\":999")
        try { DiaryAnalysisCodec.decodeCandidate(json); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun allFilenameFormatsUseStrictWholeNameCalendarDates() {
        val expected = LocalDate.of(2024, 2, 29)
        listOf("2024-02-29.md", "20240229.markdown", "2024年02月29日.MD").forEach {
            assertEquals(expected, diaryDateFromFileName(it))
        }
        listOf("2026-02-29.md", "2026-13-40.md", "note-2024-02-29.md", "2024-02-29.txt",
            "2024-2-29.md", "20240230.md", "2024年02月30日.md").forEach {
            assertNull(it, diaryDateFromFileName(it))
        }
    }

    @Test fun filenameWinsAsSuggestionButConflictsNeedExplicitReview() {
        val input = DiaryParseInput.fromSnapshot(fullInput("").parseKey.sourceKey.copy(relPath = "2026-10-02.md"), "", "v1")
        val model = analyzeFixture(input,
            """{"schemaVersion":1,"sessions":[{"date":"2026-10-01","exercises":[]}]}""")
        val suggestion = suggestDiaryDate(input.parseKey.sourceKey, model)
        assertEquals(LocalDate.of(2026, 10, 2), suggestion.suggested)
        assertTrue(suggestion.requiresReview)
    }

    @Test fun absentFilenameDateUsesUniqueModelDateAndNeverToday() {
        val input = fullInput("")
        fun suggest(sessions: String) = suggestDiaryDate(input.parseKey.sourceKey,
            analyzeFixture(input, """{"schemaVersion":1,"sessions":$sessions}"""))
        assertEquals(LocalDate.of(2026, 10, 1), suggest("""[{"date":"2026-10-01","exercises":[]}]""").suggested)
        assertNull(suggest("[]").suggested)
        assertTrue(suggest("[]").requiresReview)
        val conflict = suggest("""[{"date":"2026-10-01","exercises":[]},{"date":"2026-10-02","exercises":[]}]""")
        assertNull(conflict.suggested)
        assertTrue(conflict.requiresReview)
        assertTrue(suggest("""[{"date":"2026-02-30","exercises":[]}]""").requiresReview)
    }
}
