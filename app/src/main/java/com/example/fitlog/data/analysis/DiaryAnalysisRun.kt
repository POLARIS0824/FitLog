package com.example.fitlog.data.analysis

import com.example.fitlog.data.ai.AiConfigurationFailure

data class AnalysisSource(val key: SourceKey, val documentUri: String)
enum class AnalysisItemOutcome { SUCCEEDED, FAILED, SKIPPED }
enum class AnalysisProblem { SOURCE, SOURCE_CHANGED, CONFIGURATION, STORAGE, REQUEST }
data class AnalysisIssue(val problem: AnalysisProblem, val modelFailure: DiaryParseFailure? = null,
    val configurationFailure: AiConfigurationFailure? = null)
data class AnalysisItemResult(val source: AnalysisSource, val outcome: AnalysisItemOutcome, val issue: AnalysisIssue? = null)
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
