package com.example.fitlog.data.analysis

import com.example.fitlog.data.analysis.adapter.DiaryModelResponse
import com.example.fitlog.data.analysis.adapter.DiaryModelSource
import com.example.fitlog.data.analysis.adapter.JsonDiaryParser
import com.example.fitlog.data.analysis.adapter.diaryCandidateJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

internal fun fixture(name: String): String = requireNotNull(
    DiaryParser::class.java.getResourceAsStream("/analysis/$name"),
).bufferedReader(Charsets.UTF_8).use { it.readText() }

internal fun fullInput(text: String): DiaryParseInput = DiaryParseInput.fromSnapshot(
    SourceKey("fixture-vault", "daily/fixture.md"), text, "fixture-v1",
)

internal fun candidateJson(vararg exercises: ExerciseCandidate): String =
    diaryCandidateJson.encodeToString(
        DiaryCandidate(DIARY_SCHEMA_VERSION, listOf(SessionCandidate(exercises = exercises.toList()))),
    )

internal fun exercise(
    quote: String,
    vararg groups: SetGroupCandidate,
    name: String = "fixture exercise",
    segmentId: String = DiaryParseInput.SEGMENT_ID,
): ExerciseCandidate = ExerciseCandidate(name, EvidenceQuote(segmentId, quote), groups.toList())

internal fun parseFixture(input: DiaryParseInput, rawJson: String): DiaryParseResult = runBlocking {
    FixtureDiaryParser(rawJson).parse(input)
}

internal fun analyzeFixture(input: DiaryParseInput, rawJson: String): DiaryAnalysis =
    when (val result = parseFixture(input, rawJson)) {
        is DiaryParseResult.Success -> result.analysis
        is DiaryParseResult.Failure -> throw AssertionError("Expected fixture success: ${result.reason}")
    }

/** Fake model source used through the typed public interface, never an NLP implementation. */
internal class FixtureDiaryParser(rawJson: String) : DiaryParser {
    var received: DiaryParseInput? = null
        private set
    private val delegate: DiaryParser = JsonDiaryParser(DiaryModelSource { DiaryModelResponse.Json(rawJson) })
    override suspend fun parse(input: DiaryParseInput): DiaryParseResult {
        received = input
        return delegate.parse(input)
    }
}
