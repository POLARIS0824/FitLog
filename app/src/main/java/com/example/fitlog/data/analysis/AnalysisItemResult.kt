package com.example.fitlog.data.analysis

import com.example.fitlog.data.ai.AiConfigurationFailure
import com.example.fitlog.model.analysis.DiaryParseFailure
import com.example.fitlog.model.source.SourceKey

data class AnalysisSource(val key: SourceKey, val documentUri: String)
enum class AnalysisItemOutcome { SUCCEEDED, FAILED, SKIPPED }
enum class AnalysisProblem { SOURCE, SOURCE_CHANGED, CONFIGURATION, STORAGE, REQUEST }
data class AnalysisIssue(val problem: AnalysisProblem, val modelFailure: DiaryParseFailure? = null,
    val configurationFailure: AiConfigurationFailure? = null)
data class AnalysisItemResult(val source: AnalysisSource, val outcome: AnalysisItemOutcome, val issue: AnalysisIssue? = null)
