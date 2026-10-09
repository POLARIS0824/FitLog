package com.example.fitlog.log

import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.IndexedSource
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LogDiaryWorkTest {
    @Test fun rangesUseInclusiveLocalDatesAndAllIncludesUnknownAndFutureDates() {
        val today = LocalDate.of(2026, 10, 9)
        val paths = listOf("2026-10-10.md", "2026-10-09.md", "2026-10-07.md", "2026-10-06.md",
            "2026-10-03.md", "2026-10-02.md", "2026-09-10.md", "2026-09-09.md", "unknown.md")
        val sources = paths.map { batchSource(batchCandidate(it)) }
        fun count(range: LogAnalysisRange) = matchAnalysisSources(sources, emptyMap(), range, today).size
        assertEquals(2, count(LogAnalysisRange.THREE_DAYS))
        assertEquals(4, count(LogAnalysisRange.WEEK))
        assertEquals(6, count(LogAnalysisRange.MONTH))
        assertEquals(9, count(LogAnalysisRange.ALL))
        assertEquals(1, matchAnalysisSources(sources, emptyMap(), LogAnalysisRange.THREE_DAYS, today.plusDays(3)).size)
    }

    @Test fun incrementalIncludesFailuresAndChangedSourcesButNotCurrentCandidates() {
        val a = batchCandidate("2026-10-09.md")
        val b = batchCandidate("2026-10-08.md")
        val c = batchCandidate("2026-10-07.md")
        val failed = batchSummary(b).copy(latestAttempt = b.attempt.copy(status = ParseRunStatus.FAILED))
        val sources = listOf(batchSource(a), batchSource(b), batchSource(c).copy(contentHash = "b".repeat(64)),
            batchSource(batchCandidate("undated.md")), batchSource(batchCandidate("missing.md")).copy(status = IndexedSource.MISSING))
        val summaries = listOf(batchSummary(a), failed, batchSummary(c)).associateBy { it.sourceKey.relPath }
        assertEquals(listOf("2026-10-07.md", "2026-10-08.md", "undated.md"),
            matchAnalysisSources(sources, summaries, LogAnalysisRange.INCREMENTAL, LocalDate.of(2026, 10, 9)).map { it.path })
    }

    @Test fun latestFailureDoesNotEraseAUsableCandidateOrConfirmationAndSourceFailuresWin() {
        val candidate = batchCandidate()
        val summary = batchSummary(candidate)
        assertEquals(LogDiaryStatus.PENDING, diaryWorkStatus(batchSource(candidate), summary))
        val failed = summary.copy(latestAttempt = candidate.attempt.copy(status = ParseRunStatus.FAILED))
        assertNotNull(failed.candidate)
        assertEquals(LogDiaryStatus.FAILED, diaryWorkStatus(batchSource(candidate), failed))
        assertEquals(BatchReviewBlock.ATTEMPT, assessBatchReview(failed, indexedVersion(batchSource(candidate)), true).block)
        assertEquals(LogDiaryStatus.STALE, diaryWorkStatus(batchSource(candidate).copy(contentHash = "b".repeat(64)), summary))
        assertEquals(LogDiaryStatus.RUNNING, diaryWorkStatus(batchSource(candidate), summary, running = true))
        assertEquals(LogDiaryStatus.READ_FAILED, diaryWorkStatus(batchSource(candidate).copy(status = IndexedSource.READ_FAILED), summary, running = true))
        assertEquals(LogDiaryStatus.UNVERIFIABLE, diaryWorkStatus(batchSource(candidate).copy(hashVersion = 2), summary))
        assertEquals(LogDiaryStatus.STALE, diaryWorkStatus(batchSource(candidate), summary, changedDuringAnalysis = true))
    }

    @Test fun inferredMissingAndEmptyResultsAreNeverAutomaticallyReady() {
        val inferred = batchCandidate(group = SetGroupCandidate("squat", 40.0, WeightUnit.KG, WeightBasis.TOTAL,
            reps = 8, count = 1, inferredFields = listOf("weight")))
        val missing = batchCandidate(group = SetGroupCandidate("squat", reps = 8, count = 1))
        listOf(inferred, missing).forEach {
            val assessment = assessBatchReview(batchSummary(it), indexedVersion(batchSource(it)), true)
            assertEquals(BatchReviewEligibility.WITH_NOTICES, assessment.eligibility)
            assertTrue(batchReviewNotices(it.analysis).isNotEmpty())
        }
        val empty = batchSummary(inferred).copy(candidate = inferred.copy(analysis = inferred.analysis.copy(sessions = emptyList())))
        assertEquals(BatchReviewEligibility.WITH_NOTICES, assessBatchReview(empty, indexedVersion(batchSource(inferred)), true).eligibility)
        assertNull(DiaryConfirmation.fromCandidate(missing.attempt.id, missing.analysis, LocalDate.of(2026, 10, 9))
            .sessions.single().exercises.single().sets.single().unit)
    }

    @Test fun datesPartialGroupsAndStaleResultsRequireIndividualReview() {
        val conflict = batchCandidate(date = "2026-10-08")
        val fragment = batchCandidate(group = SetGroupCandidate("squat", reps = 8))
        assertEquals(BatchReviewBlock.DATE, assessBatchReview(batchSummary(conflict), indexedVersion(batchSource(conflict)), true).block)
        assertEquals(BatchReviewBlock.PARTIAL, assessBatchReview(batchSummary(fragment), indexedVersion(batchSource(fragment)), true).block)
        assertEquals(BatchReviewBlock.STALE, assessBatchReview(batchSummary(conflict), DiaryContentVersion("b".repeat(64), 1), true).block)
    }
}
