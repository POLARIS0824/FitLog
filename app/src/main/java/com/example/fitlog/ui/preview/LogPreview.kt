package com.example.fitlog.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.domain.analysis.assessBatchReview
import com.example.fitlog.feature.log.BatchConfirmationProgress
import com.example.fitlog.feature.log.BatchReviewContent
import com.example.fitlog.feature.log.BatchReviewItem
import com.example.fitlog.feature.log.LogActions
import com.example.fitlog.feature.log.LogContent
import com.example.fitlog.feature.log.LogUiState
import com.example.fitlog.feature.log.indexedVersion
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun LogPreview() {
    val files = listOf(PreviewDiary.file)
    FitLogPreview {
        LogContent(LogUiState(vault = PreviewDiary.VAULT_URI,
            files = files, visibleFiles = files, scanStatus = IndexedScan.COMPLETE),
            LogActions(), { _, _ -> }, {}, {})
    }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun LogEmptyPreview() {
    FitLogPreview { LogContent(LogUiState(), LogActions(), { _, _ -> }, {}, {}) }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun BatchReviewCandidatesPreview() { FitLogPreview {
    val candidate = PreviewDiary.candidate()
    val key = candidate.analysis.parseKey
    val source = IndexedSource(key.sourceKey.vaultId, PreviewDiary.DOCUMENT, PreviewDiary.FILE_NAME,
        key.sourceKey.relPath, PreviewDiary.VAULT_URI, true, contentHash = key.contentHash, hashVersion = key.hashVersion)
    val summary = DiaryAnalysisSummary(key.sourceKey, candidate.attempt, candidate)
    val ready = BatchReviewItem(source, summary, assessBatchReview(summary, indexedVersion(source), true))
    BatchReviewContent(listOf(ready), setOf(ready.id), false, false, true, false, null,
        BatchConfirmationProgress(), false, {}, {}, {}, {}, {}, {})
} }
