package com.example.fitlog.diary

import com.example.fitlog.R
import com.example.fitlog.data.analysis.*

internal fun basisLabel(basis: WeightBasis?) = when (basis) {
    WeightBasis.PER_SIDE -> R.string.detail_basis_per_side
    WeightBasis.TOTAL -> R.string.detail_basis_total
    WeightBasis.BODYWEIGHT -> R.string.detail_basis_bodyweight
    WeightBasis.ADDED -> R.string.detail_basis_added
    WeightBasis.ASSISTED -> R.string.detail_basis_assisted
    WeightBasis.UNKNOWN -> R.string.detail_basis_unknown_short
    null -> R.string.detail_basis_unknown_short
}

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

internal fun validationLabel(code: ValidationCode) = when (code) {
    ValidationCode.UNKNOWN_SEGMENT -> R.string.detail_issue_unknown_segment
    ValidationCode.EVIDENCE_NOT_FOUND -> R.string.detail_issue_evidence_missing
    ValidationCode.AMBIGUOUS_EVIDENCE -> R.string.detail_issue_evidence_ambiguous
    ValidationCode.EMPTY_NAME -> R.string.detail_issue_name
    ValidationCode.GROUP_TEXT_NOT_FOUND -> R.string.detail_issue_group_text
    ValidationCode.INVALID_WEIGHT -> R.string.detail_issue_weight
    ValidationCode.INVALID_COUNT -> R.string.detail_issue_count
    ValidationCode.INVALID_REPS -> R.string.detail_issue_reps
    ValidationCode.INCONSISTENT_REPS -> R.string.detail_issue_reps_inconsistent
    ValidationCode.TOO_MANY_SETS -> R.string.detail_issue_too_many_sets
    ValidationCode.INVALID_DATE -> R.string.detail_issue_date
    ValidationCode.DATE_CONFLICT -> R.string.detail_issue_date_conflict
    ValidationCode.MISSING_DATE -> R.string.detail_issue_date_missing
    ValidationCode.MISSING_WEIGHT -> R.string.detail_issue_weight_missing
    ValidationCode.MISSING_UNIT -> R.string.detail_issue_unit_missing
    ValidationCode.MISSING_BASIS -> R.string.detail_issue_basis_missing
    ValidationCode.MISSING_REPS -> R.string.detail_issue_reps_missing
    ValidationCode.MISSING_COUNT -> R.string.detail_issue_count_missing
    ValidationCode.INFERRED_VALUE -> R.string.detail_issue_inferred
    ValidationCode.UNKNOWN_VALUE -> R.string.detail_issue_unknown
}
