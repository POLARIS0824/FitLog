@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.app.analysis

import com.example.fitlog.data.analysis.DiaryAnalysisSession
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.StoredDiaryParse
import com.example.fitlog.data.index.SourceIndex
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.data.vault.VaultSettings
import com.example.fitlog.domain.analysis.AnalyzeDiary
import com.example.fitlog.model.analysis.AnalysisSource
import com.example.fitlog.model.analysis.DiaryAnalysisSummary
import com.example.fitlog.model.analysis.DiaryParseFailure
import com.example.fitlog.model.analysis.DiaryParseResult
import com.example.fitlog.model.source.SourceIndexSnapshot
import com.example.fitlog.model.source.SourceKey
import com.example.fitlog.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

class AnalysisRuntimeTest {
    @get:Rule val main = MainDispatcherRule()
    private val source = AnalysisSource(SourceKey("00000000-0000-0000-0000-000000000001", "2026-10-22.md"), "document")
    private fun summary(key: SourceKey) = DiaryAnalysisSummary(key)

    @Test fun configurationChangesKeepAnActiveAnalysisButBackgroundingCancelsIt() = runTest(main.dispatcher) {
        val settings = object : VaultSettings {
            override val vaultConfig = flowOf(VaultConfigState.NotConfigured)
            override suspend fun getVaultConfig() = VaultConfigState.NotConfigured
            override suspend fun setVaultUri(uri: android.net.Uri): Result<Unit> = error("not used")
            override suspend fun clearVaultUri(): Result<Unit> = error("not used")
            override suspend fun getVaultUri(vaultId: String): android.net.Uri = error("not used")
        }
        val index = object : SourceIndex {
            override fun activate(vaultId: String?) = Unit
            override fun observe(vaultId: String): Flow<SourceIndexSnapshot> = error("not used")
            override fun refresh(vaultId: String): Job = error("not used")
            override suspend fun recordRead(vaultId: String, path: String, snapshot: MarkdownSnapshot) = Unit
        }
        val vm = main.own(AnalysisRuntimeViewModel(settings, index) { scope ->
            DiaryAnalysisController(scope, prepare = { awaitCancellation() }, readSummary = ::summary)
        })
        runCurrent()
        vm.controller.activate(source.key.vaultId)
        assertTrue(vm.controller.startBatch(listOf(source), false))
        runCurrent()
        vm.onStopped(changingConfiguration = true)
        runCurrent()
        assertTrue(vm.controller.state.value.active)
        vm.onStopped(changingConfiguration = false)
        runCurrent()
        assertFalse(vm.controller.state.value.active)
        assertTrue(vm.controller.state.value.stopped)
    }

    @Test fun providerAuthenticationFailureStopsBatchButMalformedAnswerDoesNot() = runTest(main.dispatcher) {
        suspend fun analyze(reason: DiaryParseFailure) = AnalyzeDiary(::summary)(object : DiaryAnalysisSession {
            override suspend fun read(source: AnalysisSource) = DiaryParseInput.fromSnapshot(source.key, "squat", "test")
            override suspend fun execute(input: DiaryParseInput) = StoredDiaryParse("attempt", DiaryParseResult.Failure(reason))
        }, source, true)
        assertTrue(analyze(DiaryParseFailure.AUTHENTICATION_ERROR).stopBatch)
        assertFalse(analyze(DiaryParseFailure.MALFORMED_JSON).stopBatch)
    }

    @Test fun analysisRejectsConcurrentRequestsAndReleasesItsSlotAfterCancellation() = runTest(main.dispatcher) {
        val controller = DiaryAnalysisController(backgroundScope, prepare = { awaitCancellation() }, readSummary = ::summary)
        controller.activate(source.key.vaultId)
        assertTrue(controller.startBatch(listOf(source), false))
        assertFalse(controller.startBatch(listOf(source), false))
        runCurrent()
        controller.cancel()
        runCurrent()
        assertTrue(controller.startBatch(listOf(source), false))
        controller.cancel()
    }
}
