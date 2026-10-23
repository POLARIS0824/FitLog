package com.example.fitlog.data.analysis

import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.analysis.DiaryConfirmation
import com.example.fitlog.model.analysis.DiaryConfirmationResult
import com.example.fitlog.model.analysis.DiaryParseRecords
import com.example.fitlog.model.analysis.StoredDiaryCandidate
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.SourceKey
import kotlinx.coroutines.flow.Flow

/**
 * Read-only business boundary. Opening a diary never starts extraction.
 *
 * 只暴露 observeParses 和 observeConfirmed，同时定义 DiaryParseRecords、StoredDiaryCandidate、freshness 等读取模型，
 * 让 DiaryDetail 不需要拿到整个可写 Repository
 */
interface DiaryAnalysisReader {
    fun observeParses(sourceKey: SourceKey): Flow<DiaryParseRecords>
    fun observeConfirmed(sourceKey: SourceKey): Flow<TrainingRecordDetails?>
    /** Reads the exact parse behind a confirmation or unfinished review; never starts extraction. */
    suspend fun readCandidate(sourceKey: SourceKey, parseRunId: String): StoredDiaryCandidate?
}

/** Read and transactional-write boundary used by the confirmation use case. */
interface DiaryConfirmations : DiaryAnalysisReader, DiaryVaultAnalysisReader {
    suspend fun readSummary(sourceKey: SourceKey): DiaryAnalysisSummary
    suspend fun confirm(request: DiaryConfirmation): DiaryConfirmationResult
}
