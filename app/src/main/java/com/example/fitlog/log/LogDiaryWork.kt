package com.example.fitlog.log

import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.IndexedSource
import java.time.LocalDate

enum class LogDiaryStatus(val label: Int) {
    UNPARSED(R.string.log_status_unparsed), RUNNING(R.string.log_status_running), FAILED(R.string.log_status_failed),
    PENDING(R.string.log_status_pending), REVIEW(R.string.log_status_review), CONFIRMED(R.string.log_status_confirmed),
    STALE(R.string.log_status_stale), READ_FAILED(R.string.index_source_unreadable), MISSING(R.string.index_source_missing),
    UNVERIFIABLE(R.string.detail_unverifiable),
}

enum class LogStatusFilter(val label: Int) {
    ALL(R.string.log_filter_all), ANALYSIS(R.string.log_status_unparsed), CONFIRMATION(R.string.log_status_pending),
    UPDATE(R.string.log_status_stale), FAILED(R.string.log_status_failed), CONFIRMED(R.string.log_status_confirmed);
    fun matches(status: LogDiaryStatus) = when (this) {
        ALL -> true
        ANALYSIS -> status == LogDiaryStatus.UNPARSED
        CONFIRMATION -> status in setOf(LogDiaryStatus.PENDING, LogDiaryStatus.REVIEW)
        UPDATE -> status == LogDiaryStatus.STALE
        FAILED -> status in setOf(LogDiaryStatus.FAILED, LogDiaryStatus.READ_FAILED, LogDiaryStatus.UNVERIFIABLE)
        CONFIRMED -> status == LogDiaryStatus.CONFIRMED
    }
}

enum class LogAnalysisRange(val label: Int, val days: Long? = null) {
    THREE_DAYS(R.string.log_range_three_days, 3), WEEK(R.string.log_range_week, 7), MONTH(R.string.log_range_month, 30),
    INCREMENTAL(R.string.log_range_incremental), ALL(R.string.log_range_all),
}

internal fun indexedVersion(source: IndexedSource): DiaryContentVersion? =
    source.contentHash?.let { hash -> source.hashVersion?.let { DiaryContentVersion(hash, it) } }

internal fun diaryWorkStatus(source: IndexedSource, summary: DiaryAnalysisSummary?, running: Boolean = false,
    unavailable: Boolean = false, readFailed: Boolean = false, operationFailed: Boolean = false,
    changedDuringAnalysis: Boolean = false): LogDiaryStatus {
    if (source.status == IndexedSource.MISSING) return LogDiaryStatus.MISSING
    if (source.status == IndexedSource.READ_FAILED || readFailed) return LogDiaryStatus.READ_FAILED
    if (running) return LogDiaryStatus.RUNNING
    if (changedDuringAnalysis) return LogDiaryStatus.STALE
    if (unavailable) return LogDiaryStatus.UNVERIFIABLE
    if (operationFailed) return LogDiaryStatus.FAILED
    if (summary?.latestAttempt == null && summary?.confirmed == null) return LogDiaryStatus.UNPARSED
    val version = indexedVersion(source) ?: return LogDiaryStatus.UNVERIFIABLE
    val key = summary.candidate?.analysis?.parseKey ?: summary.latestAttempt?.parseKey
    if (key != null) {
        when (resultFreshness(DiaryContentVersion(key.contentHash, key.hashVersion), version)) {
            DiaryResultFreshness.UNVERIFIABLE -> return LogDiaryStatus.UNVERIFIABLE
            DiaryResultFreshness.NEEDS_UPDATE -> return LogDiaryStatus.STALE
            DiaryResultFreshness.CURRENT -> Unit
        }
    }
    if (summary.latestAttempt?.status == ParseRunStatus.FAILED) return LogDiaryStatus.FAILED
    if (summary.candidateReadFailed) return LogDiaryStatus.UNVERIFIABLE
    if (summary.confirmed?.parseRunId == summary.candidate?.attempt?.id && summary.confirmed != null) {
        return if (confirmationFreshness(summary.confirmed, version) == ConfirmationFreshness.CONFIRMED)
            LogDiaryStatus.CONFIRMED else LogDiaryStatus.STALE
    }
    if (summary.candidate != null) {
        val assessment = assessBatchReview(summary.copy(confirmed = null), version, available = true)
        return if (assessment.eligibility == BatchReviewEligibility.READY) LogDiaryStatus.PENDING else LogDiaryStatus.REVIEW
    }
    return LogDiaryStatus.UNVERIFIABLE
}

internal fun matchAnalysisSources(sources: List<IndexedSource>, summaries: Map<String, DiaryAnalysisSummary>,
    range: LogAnalysisRange, today: LocalDate, includeUnavailable: Boolean = false): List<IndexedSource> = sources.filter { source ->
    if (!includeUnavailable && source.status != IndexedSource.AVAILABLE) return@filter false
    val days = range.days
    when {
        days != null -> diaryDateFromFileName(source.name)?.let {
            !it.isBefore(today.minusDays(days - 1)) && !it.isAfter(today)
        } == true
        range == LogAnalysisRange.INCREMENTAL -> needsDiaryAnalysis(summaries[source.path], indexedVersion(source))
        else -> true
    }
}.sortedBy { it.path }
