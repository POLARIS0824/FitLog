@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.domain.analysis

import com.example.fitlog.data.analysis.DiaryConfirmations
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.from
import com.example.fitlog.data.index.SourceIndexRepository
import com.example.fitlog.data.index.SourceIndexStore
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
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.model.source.SourceIndexSnapshot
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ConfirmationBoundaryTest {
    private val key = SourceKey("00000000-0000-0000-0000-000000000001", "2026-10-22.md")
    private class Confirmations : DiaryConfirmations {
        var written: DiaryConfirmation? = null
        var result: DiaryConfirmationResult = DiaryConfirmationResult.Invalid(ConfirmationFailure.CONFIRMATION_CHANGED)
        override fun observeParses(sourceKey: SourceKey) = flowOf(DiaryParseRecords())
        override fun observeConfirmed(sourceKey: SourceKey) = flowOf<TrainingRecordDetails?>(null)
        override fun observeSummaries(vaultId: String) = flowOf(emptyList<DiaryAnalysisSummary>())
        override suspend fun readCandidate(sourceKey: SourceKey, parseRunId: String): StoredDiaryCandidate? = null
        override suspend fun readSummary(sourceKey: SourceKey) = DiaryAnalysisSummary(sourceKey)
        override suspend fun confirm(request: DiaryConfirmation): DiaryConfirmationResult {
            written = request
            return result
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

    @Test fun confirmationAndItsIndexUpdateFinishBeforeAnUnrelatedScan() = runTest {
        val request = request("rest")
        val version = requireNotNull(request.expectedContentVersion)
        val file = MarkdownFile("content://test/diary", "2026-10-22.md", key.relPath, false)
        var rows = listOf(IndexedSource(key.vaultId, file.uri, file.name, file.path, null, false))
        val store = object : SourceIndexStore {
            override fun observe(vaultId: String) = flowOf(SourceIndexSnapshot(rows, null))
            override suspend fun sources(vaultId: String) = rows
            override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) {
                val updated = rows.associateByTo(linkedMapOf()) { it.uri }
                sources.forEach { updated[it.uri] = it }
                rows = updated.values.toList()
            }
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val documents = object : MarkdownDocuments {
            override suspend fun scan(vault: String): MarkdownScan {
                started.complete(Unit)
                release.await()
                return MarkdownScan(listOf(file), partial = false)
            }
            override suspend fun read(uri: String) = MarkdownSnapshot(file, "rest", "fingerprint", false)
        }
        val index = SourceIndexRepository(documents, store, computationDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope, resolveVaultUri = { "content://test/vault" })
        val saved = TrainingRecordDetails(TrainingRecord("00000000-0000-0000-0000-000000000003",
            key.vaultId, key.relPath, request.date.toString(), version.contentHash, version.hashVersion,
            "run", 1, false, kind = RecordKind.REST), emptyList())
        val repository = Confirmations().apply { result = DiaryConfirmationResult.Confirmed(saved) }
        val scan = index.refresh(key.vaultId)
        started.await()
        val confirmation = async {
            confirmCurrentDiary(request, file.uri, documents, repository,
                computationDispatcher = StandardTestDispatcher(testScheduler),
                recordRead = { index.recordRead(key.vaultId, file.path, it) })
        }
        runCurrent()
        try {
            assertNotNull(repository.written)
            assertTrue("Confirmation must finish before the unrelated scan", confirmation.isCompleted)
            assertEquals(DiaryConfirmationResult.Confirmed(saved), confirmation.await())
            assertEquals(version.contentHash, rows.single().contentHash)
        } finally { release.complete(Unit); scan.cancelAndJoin() }
    }
}
