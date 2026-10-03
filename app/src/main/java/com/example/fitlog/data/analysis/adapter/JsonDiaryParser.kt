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
    /** Only model answer text may be retained, never an HTTP error body or credentials. */
    data class Failure(val reason: DiaryParseFailure, val rawText: String? = null) : DiaryModelResponse
}

/**
 * In a single module this package boundary is a convention, not compiler-enforced isolation.
 *
 * 请求 DiaryModelSource，收到 JSON 后交给 DiaryCandidateCodec，
 * 再交给 DiaryCandidateValidator，最终返回 DiaryParseResult，同时保留原始响应供持久化
 */
internal class JsonDiaryParser(private val source: DiaryModelSource) : DiaryParser, RecordingDiaryParser {
    override suspend fun parse(input: DiaryParseInput): DiaryParseResult = execute(input).result

    override suspend fun execute(input: DiaryParseInput): DiaryParseExecution = when (val response = source.request(input.text)) {
        is DiaryModelResponse.Failure -> DiaryParseExecution(
            DiaryParseResult.Failure(response.reason), response.rawText,
        )
        is DiaryModelResponse.Json -> {
            val result = when (val decoded = DiaryCandidateCodec.decode(response.text)) {
                is DecodedDiaryResult.Failure -> DiaryParseResult.Failure(decoded.reason)
                is DecodedDiaryResult.Success -> DiaryParseResult.Success(
                    DiaryCandidateValidator().validate(input, decoded.diary),
                )
            }
            DiaryParseExecution(result, response.text)
        }
    }
}
