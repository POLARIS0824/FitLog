package com.example.fitlog.domain.analysis

import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import kotlinx.coroutines.flow.Flow

interface DiaryVaultAnalysisReader {
    fun observeSummaries(vaultId: String): Flow<List<DiaryAnalysisSummary>>
}
