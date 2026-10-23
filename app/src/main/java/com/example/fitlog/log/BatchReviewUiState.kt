package com.example.fitlog.log


internal data class BatchReviewUiState(
    val items: List<BatchReviewItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val readFailed: Boolean = false,
    val activeVault: Boolean = false,
    val saving: Boolean = false,
    val message: Int? = null,
    val progress: BatchConfirmationProgress = BatchConfirmationProgress(),
    val indexWarning: Boolean = false,
)
