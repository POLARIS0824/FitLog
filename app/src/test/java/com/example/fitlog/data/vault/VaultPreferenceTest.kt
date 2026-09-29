package com.example.fitlog.data.vault

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VaultPreferencesTest {

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