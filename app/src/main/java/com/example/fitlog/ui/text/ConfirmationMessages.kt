package com.example.fitlog.ui.text

import com.example.fitlog.R
import com.example.fitlog.data.analysis.ConfirmationFailure

/** Confirmation conflicts are shared by the detail page and batch review, so they live outside both. */
internal fun confirmationFailureMessage(reason: ConfirmationFailure) = when (reason) {
    ConfirmationFailure.SOURCE_CHANGED -> R.string.batch_review_source_changed
    ConfirmationFailure.SOURCE_UNAVAILABLE -> R.string.detail_source_failed
    ConfirmationFailure.CONFIRMATION_CHANGED -> R.string.detail_review_changed
    ConfirmationFailure.CANDIDATE_CHANGED -> R.string.batch_review_candidate_changed
    else -> R.string.detail_review_save_failed
}
