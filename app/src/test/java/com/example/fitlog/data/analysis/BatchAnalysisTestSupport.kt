package com.example.fitlog.data.analysis

import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.data.analysis.adapter.diaryCandidateJson
import kotlinx.serialization.encodeToString

internal const val BATCH_VAULT = "00000000-0000-4000-8000-000000000001"

internal fun batchCandidate(path: String = "2026-10-09.md", id: String = path, text: String = "squat",
    group: SetGroupCandidate = SetGroupCandidate("squat", 40.0, WeightUnit.KG, WeightBasis.TOTAL, reps = 8, count = 1),
    date: String? = diaryDateFromFileName(path.substringAfterLast('/'))?.toString()): StoredDiaryCandidate {
    val input = DiaryParseInput.fromSnapshot(SourceKey(BATCH_VAULT, path), text, "test")
    val json = diaryCandidateJson.encodeToString(DiaryCandidate(1,
        listOf(SessionCandidate(date = date, exercises = listOf(exercise("squat", group))))))
    val analysis = analyzeFixture(input, json)
    return StoredDiaryCandidate(DiaryParseAttempt(id, input.parseKey, ParseRunStatus.SUCCEEDED, 1, 2, null), analysis)
}

internal fun batchSource(candidate: StoredDiaryCandidate): IndexedSource {
    val key = candidate.analysis.parseKey
    return IndexedSource(key.sourceKey.vaultId, "content://diary/${candidate.attempt.id}",
        key.sourceKey.relPath.substringAfterLast('/'), key.sourceKey.relPath, "content://vault", false,
        contentHash = key.contentHash, hashVersion = key.hashVersion)
}

internal fun batchSummary(candidate: StoredDiaryCandidate) = DiaryAnalysisSummary(candidate.analysis.parseKey.sourceKey,
    candidate.attempt, candidate)
