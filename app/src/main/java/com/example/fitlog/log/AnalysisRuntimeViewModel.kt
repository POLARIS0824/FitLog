package com.example.fitlog.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.analysis.DiaryAnalysisController
import com.example.fitlog.data.analysis.DiaryAnalysisRepository
import com.example.fitlog.data.analysis.adapter.prepareConfiguredAnalysis
import com.example.fitlog.data.index.SourceIndexRepository
import com.example.fitlog.data.vault.MarkdownDocuments
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

/** Activity-owned runtime survives configuration changes without storing jobs or credentials. */
class AnalysisRuntimeViewModel internal constructor(providers: AiProviderRepository, documents: MarkdownDocuments,
    repository: DiaryAnalysisRepository, index: SourceIndexRepository, client: HttpClient) : ViewModel() {
    val controller: DiaryAnalysisController = DiaryAnalysisController(viewModelScope,
        prepare = { prepareConfiguredAnalysis(providers, documents, repository, client) { source, snapshot ->
            try { index.recordRead(source.key.vaultId, source.key.relPath, snapshot) } catch (e: Exception) {
                if (e is CancellationException) throw e
                controller.reportIndexWarning()
            }
        } }, readSummary = repository::readSummary)
}
