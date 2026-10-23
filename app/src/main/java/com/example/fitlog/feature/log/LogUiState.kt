package com.example.fitlog.feature.log

import com.example.fitlog.app.analysis.DiaryAnalysisRun
import com.example.fitlog.data.analysis.AnalysisItemOutcome
import com.example.fitlog.data.analysis.AnalysisItemResult
import com.example.fitlog.data.analysis.AnalysisProblem
import com.example.fitlog.data.index.file
import com.example.fitlog.data.settings.LogSortOrder
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.model.source.SourceKey
import java.util.Locale

internal data class LogUiState(
    val nativeRecords: List<TrainingRecordDetails> = emptyList(),
    val recordsLoading: Boolean = true,
    val recordsReadFailed: Boolean = false,
    val visibleFiles: List<MarkdownFile> = emptyList(),
    val vaultId: String? = null,
    val dismissedRunItems: Set<AnalysisItemResult> = emptySet(),
    val vault: String? = null,
    val files: List<MarkdownFile> = emptyList(),
    val query: String = "",
    val sort: LogSortOrder = LogSortOrder.Descending,
    val sortBusy: Boolean = false,
    val sortError: Int? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val partial: Boolean = false,
    val error: Int? = null,
    val scanStatus: String? = null,
    val showMissing: Boolean = false,
    val sources: List<IndexedSource> = emptyList(),
    val summaries: Map<String, DiaryAnalysisSummary> = emptyMap(),
    val summariesLoading: Boolean = false,
    val summariesReadFailed: Boolean = false,
    val run: DiaryAnalysisRun = DiaryAnalysisRun(),
    val pendingCount: Int = 0,
    val statuses: Map<String, LogDiaryStatus> = emptyMap(),
    val statusFilter: LogStatusFilter = LogStatusFilter.ALL,
) {
    /** One fact, one field: the run owns whether analysis is active. */
    val analysisBusy: Boolean get() = run.active
}

internal fun LogUiState.withDerivedValues(): LogUiState {
    val statuses = kotlin.run {
        sources.associate { source -> source.path to diaryWorkStatus(source, summaries[source.path],
            running = run.active && run.current == SourceKey(source.vaultId, source.path),
            unavailable = summariesReadFailed || summariesLoading,
            readFailed = run.items.any { it !in dismissedRunItems && it.source.key.relPath == source.path && it.issue?.problem == AnalysisProblem.SOURCE },
            operationFailed = run.items.any { it !in dismissedRunItems && it.source.key.relPath == source.path && it.outcome == AnalysisItemOutcome.FAILED },
            changedDuringAnalysis = run.items.any { it !in dismissedRunItems && it.source.key.relPath == source.path && it.issue?.problem == AnalysisProblem.SOURCE_CHANGED }) }
    }
    val pendingCount = kotlin.run { sources.count { source ->
        val summary = summaries[source.path]
        summary != null && (summary.candidateReadFailed ||
            (summary.candidate != null && summary.confirmed?.parseRunId != summary.candidate.attempt.id))
    } }
    val visibleFiles = kotlin.run {
        filterAndSortFiles(sources.filter { (showMissing || it.status != IndexedSource.MISSING) &&
            statusFilter.matches(statuses[it.path] ?: LogDiaryStatus.UNPARSED) }.map { it.file() }, query, sort)
    }
    return copy(statuses = statuses, pendingCount = pendingCount, visibleFiles = visibleFiles)
}

internal fun filterAndSortFiles(files: List<MarkdownFile>, query: String, order: LogSortOrder): List<MarkdownFile> {
    val term = query.trim()
    val comparator = compareBy<MarkdownFile> { it.name.lowercase(Locale.ROOT) }
        .thenBy { it.path.lowercase(Locale.ROOT) }.thenBy { it.path }.thenBy { it.uri }
    return files.filter { it.name.contains(term, ignoreCase = true) || it.path.contains(term, ignoreCase = true) }
        .sortedWith(if (order == LogSortOrder.Ascending) comparator else comparator.reversed())
}
