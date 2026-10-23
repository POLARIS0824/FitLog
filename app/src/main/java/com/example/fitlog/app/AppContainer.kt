package com.example.fitlog.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.R
import com.example.fitlog.app.analysis.AnalysisRuntimeViewModel
import com.example.fitlog.app.analysis.DiaryAnalysisController
import com.example.fitlog.app.analysis.IndexWarning
import com.example.fitlog.app.navigation.FitLogRoute
import com.example.fitlog.data.ai.AiProviderDiagnostics
import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.ai.aiDataStore
import com.example.fitlog.data.ai.transport.createAiHttpClient
import com.example.fitlog.data.ai.transport.openAiChatClientFactory
import com.example.fitlog.data.analysis.AnalysisSource
import com.example.fitlog.data.analysis.DiaryAnalysisRepository
import com.example.fitlog.data.analysis.adapter.DiaryAnalysisSessionFactory
import com.example.fitlog.data.index.RoomSourceIndexStore
import com.example.fitlog.data.index.SourceIndex
import com.example.fitlog.data.index.SourceIndexDatabase
import com.example.fitlog.data.index.SourceIndexRepository
import com.example.fitlog.data.local.FitLogDatabase
import com.example.fitlog.data.record.TrainingRecordRepository
import com.example.fitlog.data.settings.AppearancePreferencesStore
import com.example.fitlog.data.settings.DataStoreExerciseAliasStore
import com.example.fitlog.data.settings.InsightPreferencesStore
import com.example.fitlog.data.settings.LogPreferences
import com.example.fitlog.data.settings.appearanceDataStore
import com.example.fitlog.data.vault.MarkdownDocumentRepository
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.data.vault.vaultDataStore
import com.example.fitlog.domain.analysis.confirmCurrentDiary
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.record.SaveNativeRecord
import com.example.fitlog.feature.diary.DiaryDetailViewModel
import com.example.fitlog.feature.insight.InsightViewModel
import com.example.fitlog.feature.log.BatchReviewViewModel
import com.example.fitlog.feature.log.LogViewModel
import com.example.fitlog.feature.record.NativeRecordViewModel
import com.example.fitlog.feature.settings.AiSettingsViewModel
import com.example.fitlog.feature.settings.AppearanceViewModel
import com.example.fitlog.feature.vault.VaultManagementViewModel
import com.example.fitlog.feature.vault.VaultSetupViewModel
import com.example.fitlog.model.source.SourceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application is the sole owner of platform resources and the dependency graph. */
internal class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vaultPreferences = VaultPreferences(applicationContext.vaultDataStore)
    private val logSettings = LogPreferences(applicationContext.vaultDataStore)
    private val vaultRepository = VaultRepository(applicationContext)
    private val documents = MarkdownDocumentRepository(applicationContext)
    private val analysisDatabase by lazy { FitLogDatabase.open(applicationContext) }
    private val analysis by lazy { DiaryAnalysisRepository(analysisDatabase) }
    private val records by lazy { TrainingRecordRepository(analysisDatabase) }
    private val saveRecord by lazy { SaveNativeRecord(records) }
    private val sourceIndex: SourceIndex by lazy {
        SourceIndexRepository(documents, RoomSourceIndexStore(SourceIndexDatabase.open(applicationContext)),
            scope = applicationScope, resolveVaultUri = { vaultPreferences.getVaultUri(it).toString() })
    }
    private val providers = AiProviderRepository(applicationContext.aiDataStore)
    private val aiClient by lazy { createAiHttpClient() }
    private val chats by lazy { openAiChatClientFactory(aiClient) }
    private val aliases = DataStoreExerciseAliasStore(applicationContext)
    private val insightPreferences = InsightPreferencesStore(applicationContext)
    private val appearanceStore = AppearancePreferencesStore(applicationContext.appearanceDataStore)

    fun appearance() = AppearanceViewModel(appearanceStore)

    fun analysisRuntime() = AnalysisRuntimeViewModel(vaultPreferences, sourceIndex) { scope ->
        val warning = IndexWarning()
        DiaryAnalysisController(scope,
            prepare = { DiaryAnalysisSessionFactory(providers, documents, analysis::parse, chats).prepare { source, snapshot ->
                try { sourceIndex.recordRead(source.key.vaultId, source.key.relPath, snapshot) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    warning.report()
                }
            } }, readSummary = analysis::readSummary, indexWarning = warning)
    }

    fun log(savedState: SavedStateHandle, controller: DiaryAnalysisController) =
        LogViewModel(savedState, vaultPreferences.vaultConfig, sourceIndex, logSettings, analysis, controller, records)

    fun insight(savedState: SavedStateHandle, range: String = InsightRange.NINETY.name, nameKey: String? = null) =
        InsightViewModel(vaultPreferences.vaultConfig, sourceIndex::observe, records, aliases, savedState,
            refreshIndex = { sourceIndex.refresh(it) }, initialRange = range, preferences = insightPreferences, nameKey = nameKey)

    fun batchReview(route: FitLogRoute.BatchReview, savedState: SavedStateHandle): BatchReviewViewModel {
        // The warning callback needs the finished VM; a late-bound lambda avoids a self-reference.
        var onIndexWarning: () -> Unit = {}
        val vm = BatchReviewViewModel(route, savedState, sourceIndex.observe(route.vaultId), analysis, vaultPreferences.vaultConfig,
            confirm = { source, request -> confirmCurrentDiary(request, source.uri, documents, analysis) { snapshot ->
                try { sourceIndex.recordRead(route.vaultId, source.path, snapshot) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onIndexWarning()
                }
            } }, refreshIndex = { sourceIndex.refresh(route.vaultId) })
        onIndexWarning = vm::reportIndexWarning
        return vm
    }

    fun diary(route: FitLogRoute.DiaryDetail, savedState: SavedStateHandle, controller: DiaryAnalysisController) =
        DiaryDetailViewModel(route, documents, analysis, savedState,
            parseDiary = { controller.runSingle(AnalysisSource(SourceKey(route.vaultId, route.relPath), route.document)) },
            confirmDiary = { request -> confirmCurrentDiary(request, route.document, documents, analysis) { snapshot ->
                try { sourceIndex.recordRead(route.vaultId, route.relPath, snapshot) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    controller.reportIndexWarning()
                }
            } }, analysisState = controller.state, cancelAnalysis = controller::cancel,
            deleteConfirmation = { records.deleteConfirmation(SourceKey(route.vaultId, route.relPath)) },
            onSourceRead = { sourceIndex.recordRead(route.vaultId, route.relPath, it) })

    fun aiSettings() = AiSettingsViewModel(providers, applicationContext.getString(R.string.ai_deepseek),
        AiProviderDiagnostics(chats))

    fun nativeRecord(recordId: String?, savedState: SavedStateHandle) =
        NativeRecordViewModel(recordId, records, saveRecord, savedState)

    fun vaultSetup(requestId: String) = VaultSetupViewModel.Factory(requestId, vaultPreferences, vaultRepository)
    fun vaultManagement() = VaultManagementViewModel(vaultPreferences.vaultConfig,
        vaultRepository::inspectFolder, vaultPreferences::clearVaultUri)
}
