package com.example.fitlog.model.analysis

import com.example.fitlog.model.source.SourceKey

data class DiaryAnalysisRun(
    val vaultId: String? = null,
    val active: Boolean = false,
    val batch: Boolean = false,
    val total: Int = 0,
    val current: SourceKey? = null,
    val items: List<AnalysisItemResult> = emptyList(),
    val stopped: Boolean = false,
    val issue: AnalysisIssue? = null,
    val indexWarning: Boolean = false,
) {
    val succeeded get() = items.count { it.outcome == AnalysisItemOutcome.SUCCEEDED }
    val failed get() = items.count { it.outcome == AnalysisItemOutcome.FAILED }
    val skipped get() = items.count { it.outcome == AnalysisItemOutcome.SKIPPED }
    val remaining get() = total - items.size
}
