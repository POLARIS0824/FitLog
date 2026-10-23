@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.domain.analysis

import com.example.fitlog.data.analysis.DiaryConfirmations
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.from
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.vault.MarkdownScan
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.model.analysis.ConfirmationFailure
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.analysis.DiaryConfirmation
import com.example.fitlog.model.analysis.DiaryConfirmationResult
import com.example.fitlog.model.analysis.DiaryContentVersion
import com.example.fitlog.model.analysis.DiaryParseRecords
import com.example.fitlog.model.analysis.StoredDiaryCandidate
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ConfirmationBoundaryTest {
    private val key = SourceKey("00000000-0000-0000-0000-000000000001", "2026-10-22.md")
    private class Confirmations : DiaryConfirmations {
        var written: DiaryConfirmation? = null
        override fun observeParses(sourceKey: SourceKey) = flowOf(DiaryParseRecords())
        override fun observeConfirmed(sourceKey: SourceKey) = flowOf<TrainingRecordDetails?>(null)
        override fun observeSummaries(vaultId: String) = flowOf(emptyList<DiaryAnalysisSummary>())
        override suspend fun readCandidate(sourceKey: SourceKey, parseRunId: String): StoredDiaryCandidate? = null
        override suspend fun readSummary(sourceKey: SourceKey) = DiaryAnalysisSummary(sourceKey)
        override suspend fun confirm(request: DiaryConfirmation): DiaryConfirmationResult {
            written = request
            return DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED)
        }
    }
    private fun documents(text: String) = object : MarkdownDocuments {
        override suspend fun scan(vault: String): MarkdownScan = error("unused")
        override suspend fun read(uri: String) = MarkdownSnapshot(MarkdownFile(uri, "2026-10-22.md", "2026-10-22.md", false), text, "fingerprint", false)
    }
    private fun request(text: String) = DiaryConfirmation(key, "run", LocalDate.of(2026, 10, 22), emptyList(),
        editConfirmed = true, expectedContentVersion = DiaryContentVersion.from(DiaryParseInput.fromSnapshot(key, text, "test")))

    @Test fun changedSourceCannotReachTheConfirmationWriter() = runTest {
        val repository = Confirmations()
        val result = confirmCurrentDiary(request("old"), "document", documents("edited"), repository,
            computationDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.SOURCE_CHANGED), result)
        assertNull(repository.written)
    }

    @Test fun verifiedEmptyReviewDerivesRestKindBeforePassingToPersistence() = runTest {
        val repository = Confirmations()
        val result = confirmCurrentDiary(request("rest"), "document", documents("rest"), repository,
            computationDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(RecordKind.REST, repository.written?.kind)
        assertEquals(DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED), result)
    }
}
