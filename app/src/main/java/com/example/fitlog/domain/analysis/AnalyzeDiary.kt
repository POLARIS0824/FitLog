package com.example.fitlog.domain.analysis

import com.example.fitlog.data.analysis.AnalysisIssue
import com.example.fitlog.data.analysis.AnalysisItemOutcome
import com.example.fitlog.data.analysis.AnalysisItemResult
import com.example.fitlog.data.analysis.AnalysisProblem
import com.example.fitlog.data.analysis.AnalysisSource
import com.example.fitlog.data.analysis.DiaryAnalysisSession
import com.example.fitlog.data.analysis.StoredDiaryParse
import com.example.fitlog.data.analysis.from
import com.example.fitlog.data.analysis.toAiRequestFailure
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.analysis.DiaryContentVersion
import com.example.fitlog.model.analysis.DiaryParseFailure
import com.example.fitlog.model.analysis.DiaryParseResult
import com.example.fitlog.model.source.SourceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class AnalyzedDiary(val item: AnalysisItemResult, val stored: StoredDiaryParse? = null, val stopBatch: Boolean = false)

/** A stateless, single-source operation; progress and cancellation belong to the UI runtime. */
internal class AnalyzeDiary(private val readSummary: suspend (SourceKey) -> DiaryAnalysisSummary) {
    suspend operator fun invoke(session: DiaryAnalysisSession, source: AnalysisSource, reanalyze: Boolean): AnalyzedDiary {
        val input = session.read(source)
        currentCoroutineContext().ensureActive()
        if (!reanalyze && !needsDiaryAnalysis(readSummary(source.key), DiaryContentVersion.from(input)))
            return AnalyzedDiary(AnalysisItemResult(source, AnalysisItemOutcome.SKIPPED))
        val stored = session.execute(input)
        currentCoroutineContext().ensureActive()
        val failure = (stored.result as? DiaryParseResult.Failure)?.reason
        var issue = failure?.let { AnalysisIssue(AnalysisProblem.REQUEST, it) }
        try {
            if (DiaryContentVersion.from(session.read(source)) != DiaryContentVersion.from(input))
                issue = issue ?: AnalysisIssue(AnalysisProblem.SOURCE_CHANGED)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            issue = issue ?: AnalysisIssue(AnalysisProblem.SOURCE)
        }
        return AnalyzedDiary(AnalysisItemResult(source,
            if (failure == null) AnalysisItemOutcome.SUCCEEDED else AnalysisItemOutcome.FAILED, issue),
            stored, failure?.let(::stopsBatch) == true)
    }
}

internal fun stopsBatch(reason: DiaryParseFailure) = reason.toAiRequestFailure()?.isFatal == true
