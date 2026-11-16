package com.example.fitlog.domain.analysis

import com.example.fitlog.model.analysis.AnalysisSource
import com.example.fitlog.model.analysis.DiaryAnalysisRun
import kotlinx.coroutines.flow.StateFlow

/** Explicit user operations; the application owns execution and cancellation lifetime. */
interface DiaryAnalysisRunner {
    val state: StateFlow<DiaryAnalysisRun>
    fun startBatch(sources: List<AnalysisSource>, reanalyze: Boolean): Boolean
    fun cancel()
}

class DiaryAnalysisBusyException : IllegalStateException("An explicit analysis is already running")
