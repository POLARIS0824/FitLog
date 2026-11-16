package com.example.fitlog.feature.diary

import com.example.fitlog.R
import com.example.fitlog.model.analysis.CandidateOrigin
import com.example.fitlog.model.analysis.ConfirmationFreshness
import com.example.fitlog.model.analysis.DiaryParseAttempt
import com.example.fitlog.model.analysis.ParseRunStatus

internal fun parseStatusLabel(status: ParseRunStatus?) = when (status) {
    null -> R.string.detail_not_parsed
    ParseRunStatus.SUCCEEDED -> R.string.detail_parse_succeeded
    ParseRunStatus.FAILED -> R.string.detail_parse_failed
}

internal fun confirmationLabel(status: ConfirmationFreshness) = when (status) {
    ConfirmationFreshness.UNCONFIRMED -> R.string.detail_not_confirmed
    ConfirmationFreshness.CONFIRMED -> R.string.detail_confirmed
    ConfirmationFreshness.NEEDS_UPDATE -> R.string.detail_needs_update
    ConfirmationFreshness.UNVERIFIABLE -> R.string.detail_unverifiable
}

internal fun originLabel(origin: CandidateOrigin) = when (origin) {
    CandidateOrigin.EXPLICIT -> R.string.detail_origin_explicit
    CandidateOrigin.INHERITED -> R.string.detail_origin_inherited
    CandidateOrigin.INFERRED -> R.string.detail_origin_inferred
    CandidateOrigin.MISSING -> R.string.detail_origin_missing
}

internal fun failureLabel(attempt: DiaryParseAttempt) = when (attempt.failureCode) {
    "MALFORMED_JSON", "INVALID_TOP_LEVEL", "UNSUPPORTED_SCHEMA", "INVALID_RESPONSE" -> R.string.detail_failure_response
    "MODEL_REFUSAL" -> R.string.detail_failure_refusal
    "TIMEOUT" -> R.string.detail_failure_timeout
    "NETWORK_ERROR" -> R.string.detail_failure_network
    "AUTHENTICATION_ERROR" -> R.string.detail_failure_authentication
    "PERMISSION_DENIED" -> R.string.detail_failure_permission
    "RATE_LIMITED" -> R.string.detail_failure_rate_limit
    "REQUEST_REJECTED" -> R.string.detail_failure_request
    "SERVICE_UNAVAILABLE" -> R.string.detail_failure_service
    "EMPTY_RESPONSE" -> R.string.detail_failure_empty
    "TRUNCATED_RESPONSE" -> R.string.detail_failure_truncated
    else -> R.string.detail_parse_failed
}
