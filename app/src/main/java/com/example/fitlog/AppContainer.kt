package com.example.fitlog

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.ai.AiSettingsViewModel
import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.ai.aiDataStore
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.analysis.adapter.ConfiguredDiaryAnalysisRepository
import com.example.fitlog.data.ai.AiOperationsRepository
import com.example.fitlog.data.index.*
import com.example.fitlog.data.insight.DataStoreExerciseAliasStore
import com.example.fitlog.data.insight.InsightPreferencesStore
import com.example.fitlog.data.llm.createAiHttpClient
import com.example.fitlog.data.llm.openAiChatClientFactory
import com.example.fitlog.data.settings.AppearancePreferencesStore
import com.example.fitlog.data.settings.appearanceDataStore
import com.example.fitlog.data.vault.*
import com.example.fitlog.diary.DiaryDetailViewModel
import com.example.fitlog.log.DiaryAnalysisController
import com.example.fitlog.domain.SaveNativeRecord
import com.example.fitlog.domain.confirmCurrentDiary
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.insight.InsightViewModel
import com.example.fitlog.log.*
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.records.NativeRecordViewModel
import com.example.fitlog.settings.AppearanceViewModel
import com.example.fitlog.vault.VaultManagementViewModel
import com.example.fitlog.vault.VaultSetupViewModel
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
    private val analysisDatabase by lazy { DiaryAnalysisDatabase.open(applicationContext) }
    private val analysis by lazy { DiaryAnalysisRepository(analysisDatabase) }
    private val records by lazy { TrainingRecordRepository(analysisDatabase) }
    private val saveRecord by lazy { SaveNativeRecord(records) }
    private val sourceIndex: SourceIndex by lazy {
        SourceIndexRepository(documents, RoomSourceIndexStore(SourceIndexDatabase.open(applicationContext)),
            scope = applicationScope, resolveVaultUri = vaultPreferences::getVaultUri)
    }
    private val providers = AiProviderRepository(applicationContext.aiDataStore)
    private val aiClient by lazy { createAiHttpClient() }
    private val chats by lazy { openAiChatClientFactory(aiClient) }
    private val aliases = DataStoreExerciseAliasStore(applicationContext)
    private val insightPreferences = InsightPreferencesStore(applicationContext)
    private val appearanceStore = AppearancePreferencesStore(applicationContext.appearanceDataStore)

    fun appearance() = AppearanceViewModel(appearanceStore)

    fun analysisRuntime() = AnalysisRuntimeViewModel(vaultPreferences, sourceIndex) { scope ->
        lateinit var controller: DiaryAnalysisController
        controller = DiaryAnalysisController(scope,
            prepare = { ConfiguredDiaryAnalysisRepository(providers, documents, analysis::parse, chats).prepare { source, snapshot ->
                try { sourceIndex.recordRead(source.key.vaultId, source.key.relPath, snapshot) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    controller.reportIndexWarning()
                }
            } }, readSummary = analysis::readSummary)
        controller
    }

    fun log(savedState: SavedStateHandle, controller: DiaryAnalysisController) =
        LogViewModel(savedState, vaultPreferences.vaultConfig, sourceIndex, logSettings, analysis, controller, records)

    fun insight(savedState: SavedStateHandle, range: String = InsightRange.NINETY.name, nameKey: String? = null) =
        InsightViewModel(vaultPreferences.vaultConfig, sourceIndex::observe, records, aliases, savedState,
            refreshIndex = { sourceIndex.refresh(it) }, initialRange = range, preferences = insightPreferences, nameKey = nameKey)

    fun batchReview(route: FitLogRoute.BatchReview, savedState: SavedStateHandle): BatchReviewViewModel {
        lateinit var vm: BatchReviewViewModel
        vm = BatchReviewViewModel(route, savedState, sourceIndex.observe(route.vaultId), analysis, vaultPreferences.vaultConfig,
            confirm = { source, request -> confirmCurrentDiary(request, source.uri, documents, analysis) { snapshot ->
                try { sourceIndex.recordRead(route.vaultId, source.path, snapshot) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    vm.reportIndexWarning()
                }
            } }, refreshIndex = { sourceIndex.refresh(route.vaultId) })
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
        AiOperationsRepository(chats))

    fun nativeRecord(recordId: String?, savedState: SavedStateHandle) =
        NativeRecordViewModel(recordId, records, saveRecord, savedState)

    fun vaultSetup(requestId: String) = VaultSetupViewModel.Factory(requestId, vaultPreferences, vaultRepository)
    fun vaultManagement() = VaultManagementViewModel(vaultPreferences.vaultConfig,
        vaultRepository::inspectFolder, vaultPreferences::clearVaultUri)
}
