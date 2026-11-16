package com.example.fitlog.ui.text

import com.example.fitlog.R
import com.example.fitlog.model.analysis.ValidationCode

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
