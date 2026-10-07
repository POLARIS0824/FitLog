package com.example.fitlog.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.R
import com.example.fitlog.data.ai.*
import com.example.fitlog.data.analysis.DiaryParseFailure
import com.example.fitlog.data.analysis.adapter.AiModelsResult
import java.io.File
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiSettingsViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun fetchAndTestUseDraftWithoutSavingAndExplicitSaveKeepsMultipleModels() = runTest(dispatcher) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        val repository = AiProviderRepository(store, ::encrypt, ::decrypt)
        var fetches = 0; var tests = 0
        val vm = AiSettingsViewModel(repository, "DeepSeek", { url, key ->
            assertEquals("https://api.deepseek.com", url); assertEquals("draft-key", key); fetches++
            AiModelsResult.Success(listOf("model-a", "model-b"))
        }, { _, model, key -> assertEquals("model-b", model); assertEquals("draft-key", key); tests++; null })
        models.put("settings", vm)
        withTimeout(5000) { while (vm.loading) yield() }
        vm.changeKey("draft-key"); vm.fetch(); runCurrent()
        assertEquals(listOf("model-a", "model-b"), vm.modelIds)
        vm.changeModel("model-b"); vm.test(); runCurrent()
        assertEquals(R.string.ai_connection_success, vm.message)
        assertTrue(repository.read().providers.isEmpty())
        vm.save(); vm.save()
        withTimeout(5000) { while (vm.saving) yield() }
        assertEquals(1, fetches); assertEquals(1, tests)
        assertEquals(listOf("model-a", "model-b"), repository.read().providers.single().modelIds)
        assertEquals("model-b", repository.readActive().modelId)
    }

    @Test fun modelSwitchUpdatesSelectionWithoutReencryptingConnection() = runTest(dispatcher) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        var encryptions = 0
        val repository = AiProviderRepository(store, { encryptions++; encrypt(it) }, ::decrypt)
        val connection = AiProviderConnection("one", "DeepSeek", "https://api.deepseek.com", listOf("a", "b"))
        repository.save(connection, "key", AiModelSelection("one", "a"))
        val vm = AiSettingsViewModel(repository, "DeepSeek", { _, _ -> error("No fetch expected") },
            { _, _, _ -> error("No request expected") })
        models.put("settings", vm)
        withTimeout(5000) { while (vm.loading) yield() }
        vm.changeModel("b"); vm.save()
        withTimeout(5000) { while (vm.saving) yield() }
        assertEquals(1, encryptions)
        assertEquals("b", repository.readActive().modelId)
        assertEquals(connection, repository.read().providers.single())
    }

    @Test fun editedFormCancelsOldFetchAndLateResponseCannotOverwriteNewModels() = runTest(dispatcher) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val vm = AiSettingsViewModel(AiProviderRepository(store, ::encrypt, ::decrypt), "DeepSeek", { _, _ ->
            fetches++
            if (fetches == 1) withContext(NonCancellable) { gate.await() }
            AiModelsResult.Success(listOf(if (fetches == 1) "old-model" else "new-model"))
        }, { _, _, _ -> null })
        models.put("settings", vm)
        withTimeout(5000) { while (vm.loading) yield() }
        vm.changeKey("key"); vm.fetch(); vm.fetch(); runCurrent()
        assertEquals(1, fetches)
        vm.changeBaseUrl("https://new.example"); vm.fetch(); runCurrent()
        assertEquals(listOf("new-model"), vm.modelIds)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("new-model"), vm.modelIds)
        assertFalse(vm.requestRunning)
    }

    @Test fun saveFailureKeepsFormAndKeyAndCanBeRetriedManually() = runTest(dispatcher) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        var broken = true
        val repository = AiProviderRepository(store, { if (broken) error("encryption failed") else encrypt(it) }, ::decrypt)
        val vm = AiSettingsViewModel(repository, "DeepSeek", { _, _ -> AiModelsResult.Failure(DiaryParseFailure.NETWORK_ERROR) },
            { _, _, _ -> null })
        models.put("settings", vm)
        withTimeout(5000) { while (vm.loading) yield() }
        vm.changeKey("unsaved-key"); vm.changeModel("manual-model"); vm.addModel(); vm.save()
        withTimeout(5000) { while (vm.saving) yield() }
        assertEquals("unsaved-key", vm.apiKey); assertEquals("manual-model", vm.selectedModel)
        assertTrue(repository.read().providers.isEmpty())
        vm.fetch(); runCurrent()
        assertEquals(listOf("manual-model"), vm.modelIds)
        broken = false; vm.save()
        withTimeout(5000) { while (vm.saving) yield() }
        assertEquals("manual-model", repository.readActive().modelId)
    }

    private fun encrypt(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
    private fun decrypt(text: String) = String(Base64.getDecoder().decode(text))
}
