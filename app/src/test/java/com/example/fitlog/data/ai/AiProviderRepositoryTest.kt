package com.example.fitlog.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiProviderRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val first = AiProviderConnection("one", "DeepSeek", "https://api.deepseek.com", listOf("model-a", "model-b"))
    private val second = AiProviderConnection("two", "Gateway", "https://gateway.example/proxy/v1", listOf("model-a"))

    @Test fun savesMultipleModelsAndSwitchesOnlySelectionWithoutReencrypting() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        var encryptions = 0
        val repo = AiProviderRepository(store, { encryptions++; encrypt(it) }, ::decrypt)
        repo.save(first, "private-key", AiModelSelection(first.id, "model-a"))
        val snapshot = repo.readActive()
        repo.selectModel(AiModelSelection(first.id, "model-b"))
        assertEquals(first, repo.read().providers.single())
        assertEquals("model-b", repo.readActive().modelId)
        assertEquals("model-a", snapshot.modelId)
        assertEquals(1, encryptions)
        assertFalse(store.data.first()[stringPreferencesKey("configuration")]!!.contains("private-key"))
        assertFalse(snapshot.toString().contains("private-key"))
    }

    @Test fun identicalModelIdsOnDifferentConnectionsKeepTheirOwnCredentials() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        val repo = AiProviderRepository(store, ::encrypt, ::decrypt)
        repo.save(first, "key-one", AiModelSelection(first.id, "model-a"))
        repo.save(second, "key-two", AiModelSelection(second.id, "model-a"))
        assertEquals("key-two", repo.readActive().apiKey)
        repo.selectModel(AiModelSelection(first.id, "model-b"))
        assertEquals("key-one", repo.readActive().apiKey)
        assertEquals(listOf(first, second), repo.read().providers)
    }

    @Test fun failedEncryptionAndInvalidSelectionCannotReplaceSavedConfiguration() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        var fail = false
        val repo = AiProviderRepository(store, { if (fail) error("cipher unavailable") else encrypt(it) }, ::decrypt)
        repo.save(first, "old-key", AiModelSelection(first.id, "model-a"))
        fail = true
        expect(AiConfigurationFailure.STORAGE) { repo.save(second, "new-key", AiModelSelection(second.id, "model-a")) }
        expect(AiConfigurationFailure.INVALID_CONFIGURATION) { repo.selectModel(AiModelSelection(first.id, "missing")) }
        assertEquals(listOf(first), repo.read().providers)
        assertEquals("old-key", repo.readActive().apiKey)
    }

    @Test fun unreadableKeyIsExplicitAndMetadataRemainsAvailableForRepair() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        val writer = AiProviderRepository(store, ::encrypt, ::decrypt)
        writer.save(first, "key", AiModelSelection(first.id, "model-a"))
        val reader = AiProviderRepository(store, ::encrypt, { error("missing keystore key") })
        expect(AiConfigurationFailure.KEY_UNREADABLE) { reader.readActive() }
        expect(AiConfigurationFailure.KEY_UNREADABLE) { reader.readKey(first.id) }
        assertEquals(listOf(first), reader.read().providers)
        writer.save(first, "replacement", AiModelSelection(first.id, "model-b"))
        assertEquals("replacement", writer.readActive().apiKey)
    }

    @Test fun failedDataStoreTransactionKeepsTheEntirePreviousConfiguration() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        var failWrite = false
        val failingStore = object : DataStore<Preferences> {
            override val data = store.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                store.updateData { previous ->
                    val replacement = transform(previous)
                    if (failWrite) throw IOException("Write unavailable")
                    replacement
                }
        }
        val repo = AiProviderRepository(failingStore, ::encrypt, ::decrypt)
        repo.save(first, "old-key", AiModelSelection(first.id, "model-a"))
        val persisted = store.data.first()[stringPreferencesKey("configuration")]
        failWrite = true
        expect(AiConfigurationFailure.STORAGE) {
            repo.save(first.copy(name = "Changed", modelIds = listOf("new-model")), "new-key",
                AiModelSelection(first.id, "new-model"))
        }
        assertEquals(persisted, store.data.first()[stringPreferencesKey("configuration")])
        assertEquals(AiSettings(listOf(first), AiModelSelection(first.id, "model-a")), repo.read())
        assertEquals("old-key", repo.readActive().apiKey)
    }

    @Test fun deletingActiveConnectionDoesNotSelectAnotherOrLoseItsModels() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        val repo = AiProviderRepository(store, ::encrypt, ::decrypt)
        repo.save(first, "one", AiModelSelection(first.id, "model-a"))
        repo.save(second, "two", AiModelSelection(second.id, "model-a"))
        repo.delete(second.id)
        assertEquals(AiSettings(listOf(first), null), repo.read())
        expect(AiConfigurationFailure.NOT_CONFIGURED) { repo.readActive() }
        assertEquals("one", repo.readKey(first.id))
    }

    @Test fun corruptConfigurationIsReportedWithoutResettingStorage() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "config.preferences_pb") })
        val key = stringPreferencesKey("configuration")
        store.edit { it[key] = "broken-json" }
        val repo = AiProviderRepository(store, ::encrypt, ::decrypt)
        expect(AiConfigurationFailure.STORAGE) { repo.read() }
        assertEquals("broken-json", store.data.first()[key])
    }

    private suspend fun expect(reason: AiConfigurationFailure, action: suspend () -> Unit) {
        try { action(); fail("Expected $reason") } catch (e: AiConfigurationException) { assertEquals(reason, e.reason) }
    }
    private fun encrypt(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
    private fun decrypt(text: String) = String(Base64.getDecoder().decode(text))
}
