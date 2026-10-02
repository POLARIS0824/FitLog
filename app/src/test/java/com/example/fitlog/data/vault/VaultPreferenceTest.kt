package com.example.fitlog.data.vault

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.LocalDate
import com.example.fitlog.log.LogSortOrder

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultPreferencesTest {

    @Test fun legacyUriGetsOnePersistedUuidAndKeepsDiarySettings() = testScope.runTest {
        val store = createTestDataStore()
        val uri = "content://test/tree/old"
        val expected = DiarySettings(listOf("daily"), DiaryDateFormat.Compact)
        val prefs = VaultPreferences(store)
        prefs.diary.save(uri, expected)
        store.edit { it[stringPreferencesKey("vault_uri")] = uri }

        val states = List(3) { async { prefs.getVaultConfig() as VaultConfigState.Configured } }.awaitAll()
        val id = states.first().vaultId
        assertEquals(id, requireVaultId(id))
        assertTrue(states.all { it.vaultId == id && it.uri.toString() == uri })
        assertEquals(id, store.data.first()[stringPreferencesKey("vault_id")])
        assertEquals(expected, prefs.diary.read(id))
        assertEquals(id, (VaultPreferences(store).getVaultConfig() as VaultConfigState.Configured).vaultId)
        assertEquals(uri, prefs.getVaultUri(id))
    }

    @Test fun switchingAndDisconnectingRetainUuidAndSettingsForEachKnownUri() = testScope.runTest {
        val prefs = VaultPreferences(createTestDataStore())
        val a = Uri.parse("content://test/tree/a")
        val b = Uri.parse("content://test/tree/b")
        prefs.setVaultUri(a).getOrThrow()
        val first = prefs.getVaultConfig() as VaultConfigState.Configured
        val settings = DiarySettings(listOf("training"), DiaryDateFormat.Chinese)
        prefs.diary.save(first.vaultId, settings)
        prefs.setVaultUri(b).getOrThrow()
        val second = prefs.getVaultConfig() as VaultConfigState.Configured
        assertTrue(first.vaultId != second.vaultId)
        prefs.clearVaultUri().getOrThrow()
        assertEquals(VaultConfigState.NotConfigured, prefs.getVaultConfig())
        assertEquals(a.toString(), prefs.getVaultUri(first.vaultId))
        assertEquals(b.toString(), prefs.getVaultUri(second.vaultId))
        prefs.setVaultUri(a).getOrThrow()
        assertEquals(first, prefs.getVaultConfig())
        assertEquals(settings, prefs.diary.read(first.vaultId))
    }

    @Test fun recoveryIdentityRegistrationDoesNotSwitchCurrentConnection() = testScope.runTest {
        val prefs = VaultPreferences(createTestDataStore())
        prefs.setVaultUri(Uri.parse("content://test/tree/current")).getOrThrow()
        val current = prefs.getVaultConfig()
        val ids = List(3) { async { prefs.getVaultId("content://test/tree/recovery") } }.awaitAll()
        assertEquals(1, ids.toSet().size)
        assertEquals("content://test/tree/recovery", prefs.getVaultUri(ids.first()))
        assertEquals(current, prefs.getVaultConfig())
    }

    @Test fun invalidPersistedIdentityIsReportedInsteadOfSilentlyReplaced() = testScope.runTest {
        val store = createTestDataStore()
        store.edit {
            it[stringPreferencesKey("vault_uri")] = "content://test/tree/a"
            it[stringPreferencesKey("vault_id")] = "content://test/tree/a"
        }
        val prefs = VaultPreferences(store)
        assertTrue(prefs.getVaultConfig() is VaultConfigState.Failed)
        assertEquals("content://test/tree/a", store.data.first()[stringPreferencesKey("vault_id")])
    }

    @Test fun legacyMigrationWriteFailureDoesNotPublishAnUnpersistedUuid() = testScope.runTest {
        val legacy = androidx.datastore.preferences.core.preferencesOf(
            stringPreferencesKey("vault_uri") to "content://test/tree/a")
        val failure = IOException("Migration write failed")
        val store = object : DataStore<Preferences> {
            override val data = flow { emit(legacy) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
        }
        val state = VaultPreferences(store).getVaultConfig() as VaultConfigState.Failed
        assertEquals(failure, state.cause)
        assertEquals("content://test/tree/a", store.data.first()[stringPreferencesKey("vault_uri")])
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createTestDataStore(file: File = File(tempFolder.root, "vault_test_${System.nanoTime()}.preferences_pb")): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { file }
        )
    }

    @Test fun diarySettingsAreIsolatedByVaultAndSurviveRepositoryRecreation() = testScope.runTest {
        val dataStore = createTestDataStore()
        val first = VaultPreferences(dataStore)
        val settings = DiarySettings(listOf("daily", "training"), DiaryDateFormat.Chinese)
        first.diary.save("vault-a", settings)
        first.log.saveSort(LogSortOrder.Ascending)
        val restored = VaultPreferences(dataStore)
        assertEquals(settings, restored.diary.read("vault-a"))
        assertEquals(DiarySettings(), restored.diary.read("vault-b"))
        assertEquals(LogSortOrder.Ascending, restored.log.readSort())
    }

    @Test fun dateFormatsUseCalendarYearAndAlwaysReturnMarkdownNames() {
        val date = LocalDate.of(2021, 1, 1)
        assertEquals("2021-01-01.md", DiaryDateFormat.Dashed.fileName(date))
        assertEquals("20210101.md", DiaryDateFormat.Compact.fileName(date))
        assertEquals("2021年01月01日.md", DiaryDateFormat.Chinese.fileName(date))
    }

    @Test
    fun initialState_emitsLoadingThenNotConfigured() = testScope.runTest {
        val dataStore = createTestDataStore()
        val preferences = VaultPreferences(dataStore)

        val emissions = preferences.vaultConfig.take(2).toList()

        assertEquals(VaultConfigState.Loading, emissions[0])
        assertEquals(VaultConfigState.NotConfigured, emissions[1])
    }

    @Test
    fun saveAndRead_uriMatchesPersistedValue() = testScope.runTest {
        val dataStore = createTestDataStore()
        val preferences = VaultPreferences(dataStore)
        val expectedUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AFitLogVault")

        val saveResult = preferences.setVaultUri(expectedUri)
        assertTrue("Expected success but got: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val state = preferences.getVaultConfig()
        assertTrue(state is VaultConfigState.Configured)
        assertEquals(expectedUri, (state as VaultConfigState.Configured).uri)
    }

    @Test
    fun readFailure_emitsFailedAndDoesNotDisguiseAsNotConfigured() = testScope.runTest {
        val ioException = IOException("Simulated disk read corruption")
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw ioException }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw ioException
            }
        }
        val preferences = VaultPreferences(failingDataStore)

        val emissions = preferences.vaultConfig.take(2).toList()

        assertEquals(VaultConfigState.Loading, emissions[0])
        assertTrue("State must be Failed, not NotConfigured", emissions[1] is VaultConfigState.Failed)
        assertEquals(ioException, (emissions[1] as VaultConfigState.Failed).cause)
    }

    @Test
    fun saveFailure_returnsResultFailureAndDoesNotReportSuccess() = testScope.runTest {
        val ioException = IOException("Simulated disk write error")
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { emit(emptyPreferences()) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw ioException
            }
        }
        val preferences = VaultPreferences(failingDataStore)
        val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AFitLogVault")

        val result = preferences.setVaultUri(testUri)

        assertTrue("Save failure must return Result.failure", result.isFailure)
        assertEquals(ioException, result.exceptionOrNull())
    }

    @Test
    fun clearVaultUri_resetsConfigToNotConfiguredWithoutTouchingFiles() = testScope.runTest {
        val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AFitLogVault")
        var currentPreferences: Preferences = emptyPreferences()
        val inMemoryDataStore = object : DataStore<Preferences> {
            val flow = kotlinx.coroutines.flow.MutableStateFlow<Preferences>(emptyPreferences())
            override val data: Flow<Preferences> = flow
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                val updated = transform(currentPreferences)
                currentPreferences = updated
                flow.value = updated
                return updated
            }
        }
        val preferences = VaultPreferences(inMemoryDataStore)

        preferences.setVaultUri(testUri)
        val stateBefore = preferences.getVaultConfig()
        assertTrue(stateBefore is VaultConfigState.Configured)

        val clearResult = preferences.clearVaultUri()
        assertTrue("Clear failed: ${clearResult.exceptionOrNull()}", clearResult.isSuccess)

        val stateAfter = preferences.getVaultConfig()
        assertEquals(VaultConfigState.NotConfigured, stateAfter)
    }
}
