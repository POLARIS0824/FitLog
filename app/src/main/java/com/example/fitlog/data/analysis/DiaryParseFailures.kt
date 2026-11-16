package com.example.fitlog.data.analysis

import com.example.fitlog.data.ai.transport.AiRequestFailure
import com.example.fitlog.model.analysis.DiaryParseFailure

/**
 * Protocol failures are reported in [AiRequestFailure] first and kept here because the stored
 * attempt records, the batch policy and the existing UI wording all speak this vocabulary.
 * Renaming a value would change every persisted `failureCode`.
 */
internal fun AiRequestFailure.toDiaryFailure(): DiaryParseFailure = when (this) {
    AiRequestFailure.TIMEOUT -> DiaryParseFailure.TIMEOUT
    AiRequestFailure.NETWORK_ERROR -> DiaryParseFailure.NETWORK_ERROR
    AiRequestFailure.AUTHENTICATION_ERROR -> DiaryParseFailure.AUTHENTICATION_ERROR
    AiRequestFailure.PERMISSION_DENIED -> DiaryParseFailure.PERMISSION_DENIED
    AiRequestFailure.RATE_LIMITED -> DiaryParseFailure.RATE_LIMITED
    AiRequestFailure.REQUEST_REJECTED -> DiaryParseFailure.REQUEST_REJECTED
    AiRequestFailure.SERVICE_UNAVAILABLE -> DiaryParseFailure.SERVICE_UNAVAILABLE
    AiRequestFailure.EMPTY_RESPONSE -> DiaryParseFailure.EMPTY_RESPONSE
    AiRequestFailure.INVALID_RESPONSE -> DiaryParseFailure.INVALID_RESPONSE
    AiRequestFailure.TRUNCATED_RESPONSE -> DiaryParseFailure.TRUNCATED_RESPONSE
    AiRequestFailure.MODEL_REFUSAL -> DiaryParseFailure.MODEL_REFUSAL
}

/** `null` for failures only diary JSON decoding can produce; they are not protocol failures. */
internal fun DiaryParseFailure.toAiRequestFailure(): AiRequestFailure? = when (this) {
    DiaryParseFailure.MALFORMED_JSON,
    DiaryParseFailure.INVALID_TOP_LEVEL,
    DiaryParseFailure.UNSUPPORTED_SCHEMA -> null
    DiaryParseFailure.TIMEOUT -> AiRequestFailure.TIMEOUT
    DiaryParseFailure.NETWORK_ERROR -> AiRequestFailure.NETWORK_ERROR
    DiaryParseFailure.AUTHENTICATION_ERROR -> AiRequestFailure.AUTHENTICATION_ERROR
    DiaryParseFailure.PERMISSION_DENIED -> AiRequestFailure.PERMISSION_DENIED
    DiaryParseFailure.RATE_LIMITED -> AiRequestFailure.RATE_LIMITED
    DiaryParseFailure.REQUEST_REJECTED -> AiRequestFailure.REQUEST_REJECTED
    DiaryParseFailure.SERVICE_UNAVAILABLE -> AiRequestFailure.SERVICE_UNAVAILABLE
    DiaryParseFailure.EMPTY_RESPONSE -> AiRequestFailure.EMPTY_RESPONSE
    DiaryParseFailure.INVALID_RESPONSE -> AiRequestFailure.INVALID_RESPONSE
    DiaryParseFailure.TRUNCATED_RESPONSE -> AiRequestFailure.TRUNCATED_RESPONSE
    DiaryParseFailure.MODEL_REFUSAL -> AiRequestFailure.MODEL_REFUSAL
}
