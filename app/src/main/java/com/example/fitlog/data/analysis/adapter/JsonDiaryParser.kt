package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.DiaryParseFailure
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.DiaryParseResult
import com.example.fitlog.data.analysis.DiaryParser

/** Expected request failures are reported explicitly; cancellation and programming errors propagate. */
internal fun interface DiaryModelSource {
    suspend fun request(text: String): DiaryModelResponse
}

internal sealed interface DiaryModelResponse {
    data class Json(val text: String) : DiaryModelResponse
    data class Failure(val reason: DiaryParseFailure) : DiaryModelResponse
}

/** In a single module this package boundary is a convention, not compiler-enforced isolation. */
internal class JsonDiaryParser(private val source: DiaryModelSource) : DiaryParser {
    override suspend fun parse(input: DiaryParseInput): DiaryParseResult = when (val response = source.request(input.text)) {
        is DiaryModelResponse.Failure -> DiaryParseResult.Failure(response.reason)
        is DiaryModelResponse.Json -> when (val decoded = DiaryCandidateCodec.decode(response.text)) {
            is DecodedDiaryResult.Failure -> DiaryParseResult.Failure(decoded.reason)
            is DecodedDiaryResult.Success -> DiaryParseResult.Success(
                DiaryCandidateValidator().validate(input, decoded.diary),
            )
        }
    }
}
