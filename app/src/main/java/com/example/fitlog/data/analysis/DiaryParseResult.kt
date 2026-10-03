package com.example.fitlog.data.analysis

/** Extraction success is not confirmation, complete data, or proof of a rest day. */
sealed interface DiaryParseResult {
    data class Success(val analysis: DiaryAnalysis) : DiaryParseResult
    data class Failure(val reason: DiaryParseFailure) : DiaryParseResult
}

enum class DiaryParseFailure {
    MALFORMED_JSON,
    INVALID_TOP_LEVEL,
    UNSUPPORTED_SCHEMA,
    MODEL_REFUSAL,
    TIMEOUT,
    NETWORK_ERROR,
    AUTHENTICATION_ERROR,
    PERMISSION_DENIED,
    RATE_LIMITED,
    REQUEST_REJECTED,
    SERVICE_UNAVAILABLE,
    EMPTY_RESPONSE,
    TRUNCATED_RESPONSE,
    INVALID_RESPONSE,
}
