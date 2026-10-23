package com.example.fitlog.data.analysis

import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import kotlinx.coroutines.flow.Flow

interface DiaryVaultAnalysisReader {
    fun observeSummaries(vaultId: String): Flow<List<DiaryAnalysisSummary>>
}
