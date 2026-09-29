package com.example.fitlog.vault

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import com.example.fitlog.R
import com.example.fitlog.data.vault.DirectoryInfo
import com.example.fitlog.data.vault.SafDirectoryAccessor
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VaultSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    /** Holds IO dispatch without blocking the thread driving the test scheduler. */
    private class PausingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var paused = false
        private val pending = mutableListOf<Pair<CoroutineContext, Runnable>>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (paused) pending.add(context to block) else delegate.dispatch(context, block)
        }
        fun resume() {
            paused = false
            val tasks = pending.toList()
            pending.clear()
            tasks.forEach { (context, block) -> delegate.dispatch(context, block) }
        }
    }

    private val currentVaultUri = Uri.parse("content://test/tree/current")
    private val candidateVaultUri = Uri.parse("content://test/tree/candidate")

    private open class FakeSafDirectoryAccessor(
        var hasReadPermission: Boolean = true,
        var hasWritePermission: Boolean = true,
        var directoryInfo: DirectoryInfo? = DirectoryInfo(
            isDirectory = true,
            supportsCreate = true,
            displayName = "Default Vault",
        ),
        var takePermissionResult: Result<Unit> = Result.success(Unit),
    ) : SafDirectoryAccessor {
        override fun hasPersistedReadPermission(uri: Uri): Boolean = hasReadPermission
        override fun hasPersistedWritePermission(uri: Uri): Boolean = hasWritePermission
        override fun queryDirectoryInfo(uri: Uri): DirectoryInfo? = directoryInfo
        override fun takePersistablePermission(uri: Uri): Result<Unit> = takePermissionResult
    }

    private class InMemoryDataStore(
        initialPreferences: Preferences = emptyPreferences(),
        private val throwOnUpdate: Throwable? = null,
    ) : DataStore<Preferences> {
        val flow = MutableStateFlow(initialPreferences)
        var updateCount = 0
        override val data: Flow<Preferences> = flow
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            throwOnUpdate?.let { throw it }
            updateCount++
            val updated = transform(flow.value)
            flow.value = updated
            return updated
        }
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        createAfterSetup: Boolean = false,
        requestId: String = "test-request-id",
        vaultPreferences: VaultPreferences,
        fakeAccessor: FakeSafDirectoryAccessor = FakeSafDirectoryAccessor(),
        ioDispatcher: CoroutineDispatcher = testDispatcher,
    ): Pair<VaultSetupViewModel, FakeSafDirectoryAccessor> {
        val repository = VaultRepository(fakeAccessor, ioDispatcher)
        val vm = VaultSetupViewModel(
            savedStateHandle = savedStateHandle,
            createAfterSetup = createAfterSetup,
            requestId = requestId,
            vaultPreferences = vaultPreferences,
            vaultRepository = repository,
        )
        return vm to fakeAccessor
    }

    @Test
    fun initialState_whenNotConfigured_emitsNotConfigured_andCanConfirmIsFalse() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultConfigUiState.NotConfigured, state.configState)
        assertFalse(state.canConfirm)
        assertNull(state.candidateVault)
        assertEquals(VaultOperationStage.Idle, state.stage)
    }

    @Test
    fun initialState_whenConfiguredWithFullAccess_emitsConfigured_andCanConfirmIsTrue() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)

        val (viewModel, _) = createViewModel(vaultPreferences = prefs)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.configState is VaultConfigUiState.Configured)
        val current = state.currentVault
        assertNotNull(current)
        assertEquals("Default Vault", current?.displayName)
        assertEquals(VaultAccessStatus.CanCreateFiles, current?.accessStatus)
        assertTrue(state.canConfirm)
    }

    @Test
    fun initialState_whenConfiguredReadOnly_andCreateAfterSetup_canConfirmIsFalse() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)

        val fakeAccessor = FakeSafDirectoryAccessor(hasWritePermission = false)
        val (viewModel, _) = createViewModel(
            createAfterSetup = true,
            vaultPreferences = prefs,
            fakeAccessor = fakeAccessor,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultAccessStatus.ReadOnly, state.currentVault?.accessStatus)
        assertFalse(state.canConfirm)
    }

    @Test
    fun initialState_whenConfiguredReadOnly_andImport_canConfirmIsTrue() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)

        val fakeAccessor = FakeSafDirectoryAccessor(hasWritePermission = false)
        val (viewModel, _) = createViewModel(
            createAfterSetup = false,
            vaultPreferences = prefs,
            fakeAccessor = fakeAccessor,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultAccessStatus.ReadOnly, state.currentVault?.accessStatus)
        assertTrue(state.canConfirm)
    }

    @Test
    fun initialState_whenConfigReadFailed_emitsFailed_andRetryReloads() = testScope.runTest {
        var fail = true
        val errorException = IOException("Simulated disk error")
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (fail) throw errorException
                else emit(emptyPreferences())
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return emptyPreferences()
            }
        }
        val prefs = VaultPreferences(failingDataStore)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.configState is VaultConfigUiState.Failed)
        assertEquals(R.string.vault_error_load_config_failed, state.errorResId)

        // Retry after failure resolved
        fail = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(VaultConfigUiState.NotConfigured, viewModel.uiState.value.configState)
        assertNull(viewModel.uiState.value.errorResId)
    }

    @Test
    fun folderPicked_withNull_preservesOriginalState() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs)
        advanceUntilIdle()

        viewModel.onSelectingFolder()
        assertEquals(VaultOperationStage.Selecting, viewModel.uiState.value.stage)

        viewModel.onFolderPicked(null)
        advanceUntilIdle()

        assertEquals(VaultOperationStage.Idle, viewModel.uiState.value.stage)
        assertNull(viewModel.uiState.value.candidateVault)
    }

    @Test
    fun folderPicked_withValidUri_updatesCandidate_withoutWritingDataStore() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val fakeAccessor = FakeSafDirectoryAccessor(
            directoryInfo = DirectoryInfo(isDirectory = true, supportsCreate = true, displayName = "New Obs Folder")
        )
        val (viewModel, _) = createViewModel(vaultPreferences = prefs, fakeAccessor = fakeAccessor)
        advanceUntilIdle()

        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("New Obs Folder", state.candidateVault?.displayName)
        assertEquals(VaultAccessStatus.CanCreateFiles, state.candidateVault?.accessStatus)
        assertTrue(state.canConfirm)
        assertEquals(0, dataStore.updateCount) // DataStore must NOT be written before confirm!
    }

    @Test
    fun folderPicked_readOnlyForAdd_showsError_andDisablesConfirm() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val fakeAccessor = FakeSafDirectoryAccessor(hasWritePermission = false)
        val (viewModel, _) = createViewModel(
            createAfterSetup = true,
            vaultPreferences = prefs,
            fakeAccessor = fakeAccessor,
        )
        advanceUntilIdle()

        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultAccessStatus.ReadOnly, state.candidateVault?.accessStatus)
        assertEquals(R.string.vault_error_read_only, state.errorResId)
        assertFalse(state.canConfirm)
    }

    @Test
    fun confirm_candidateSavesToDataStore_andEmitsCompletedResult() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val (viewModel, _) = createViewModel(
            createAfterSetup = true,
            requestId = "req-123",
            vaultPreferences = prefs,
        )
        advanceUntilIdle()

        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        viewModel.confirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultOperationStage.Completed, state.stage)
        assertEquals(VaultSetupCompletion(requestId = "req-123", createAfterSetup = true), state.completedResult)
        assertEquals(1, dataStore.updateCount)
    }

    @Test
    fun confirm_saveFailure_preservesCandidate_setsError_allowsRetry() = testScope.runTest {
        val dataStore = InMemoryDataStore(throwOnUpdate = IOException("Disk full"))
        val prefs = VaultPreferences(dataStore)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs)
        advanceUntilIdle()

        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        viewModel.confirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultOperationStage.Idle, state.stage)
        assertEquals(R.string.vault_error_save_config_failed, state.errorResId)
        assertNotNull(state.candidateVault) // Candidate folder is preserved!
    }

    @Test
    fun confirm_existingConfiguredVault_doesNotRewriteDataStore() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)
        val initialUpdates = dataStore.updateCount

        val (viewModel, _) = createViewModel(
            createAfterSetup = false,
            requestId = "req-456",
            vaultPreferences = prefs,
        )
        advanceUntilIdle()

        viewModel.confirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultOperationStage.Completed, state.stage)
        assertEquals(VaultSetupCompletion(requestId = "req-456", createAfterSetup = false), state.completedResult)
        assertEquals(initialUpdates, dataStore.updateCount) // No redundant DataStore write!
    }

    @Test
    fun confirm_recheckFails_blocksSaveAndShowsError() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val fakeAccessor = FakeSafDirectoryAccessor()
        val (viewModel, _) = createViewModel(vaultPreferences = prefs, fakeAccessor = fakeAccessor)
        advanceUntilIdle()

        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        // Revoke permission before user clicks confirm
        fakeAccessor.hasReadPermission = false

        viewModel.confirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultOperationStage.Idle, state.stage)
        assertEquals(R.string.vault_error_needs_reauthorization, state.errorResId)
        assertEquals(0, dataStore.updateCount)
        assertNull(state.completedResult)
    }

    @Test
    fun back_duringSaving_isBlocked() = testScope.runTest {
        val saveDeferred = CompletableDeferred<Preferences>()
        val slowDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { emit(emptyPreferences()) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return saveDeferred.await()
            }
        }
        val prefs = VaultPreferences(slowDataStore)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs)
        advanceUntilIdle()

        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        viewModel.confirm()
        runCurrent()

        assertEquals(VaultOperationStage.Saving, viewModel.uiState.value.stage)
        assertFalse("Back must be blocked during saving", viewModel.onBack())

        saveDeferred.complete(emptyPreferences())
        advanceUntilIdle()
        assertEquals(VaultOperationStage.Completed, viewModel.uiState.value.stage)
    }

    @Test
    fun back_duringChecking_cancelsCheck_andAllowsBack() = testScope.runTest {
        val io = PausingDispatcher(testDispatcher)
        val dataStore = InMemoryDataStore()
        val (viewModel, _) = createViewModel(vaultPreferences = VaultPreferences(dataStore), ioDispatcher = io)
        advanceUntilIdle()

        io.paused = true
        viewModel.onFolderPicked(candidateVaultUri)
        runCurrent()
        assertEquals(VaultOperationStage.Checking, viewModel.uiState.value.stage)
        assertTrue(viewModel.onBack())
        io.resume()
        advanceUntilIdle()

        assertEquals(VaultOperationStage.Idle, viewModel.uiState.value.stage)
        assertNull(viewModel.uiState.value.candidateVault)
        assertNull(viewModel.uiState.value.completedResult)
        assertEquals(0, dataStore.updateCount)
    }

    @Test
    fun back_duringCandidateConfirmation_doesNotSaveOrComplete() = testScope.runTest {
        val io = PausingDispatcher(testDispatcher)
        val dataStore = InMemoryDataStore()
        val (viewModel, _) = createViewModel(vaultPreferences = VaultPreferences(dataStore), ioDispatcher = io)
        advanceUntilIdle()
        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()

        io.paused = true
        viewModel.confirm()
        runCurrent()
        assertEquals(VaultOperationStage.Checking, viewModel.uiState.value.stage)
        assertTrue(viewModel.onBack())
        // Keep the ViewModel alive, as it is during a navigation exit transition.
        io.resume()
        advanceUntilIdle()

        assertEquals(0, dataStore.updateCount)
        assertNull(viewModel.uiState.value.completedResult)
        assertEquals(VaultOperationStage.Idle, viewModel.uiState.value.stage)
    }

    @Test
    fun back_duringCurrentConfirmation_doesNotComplete() = testScope.runTest {
        val io = PausingDispatcher(testDispatcher)
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs, ioDispatcher = io)
        advanceUntilIdle()
        io.paused = true
        viewModel.confirm()
        runCurrent()
        assertTrue(viewModel.onBack())
        io.resume()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.completedResult)
        assertEquals(1, dataStore.updateCount)
    }

    @Test
    fun retry_candidateAccessFailure_rechecksWithoutSaving() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val accessor = FakeSafDirectoryAccessor(directoryInfo = null)
        val (viewModel, _) = createViewModel(vaultPreferences = VaultPreferences(dataStore), fakeAccessor = accessor)
        advanceUntilIdle()
        viewModel.onFolderPicked(candidateVaultUri)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canConfirm)
        accessor.directoryInfo = DirectoryInfo(true, true, "Recovered")
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canConfirm)
        assertNull(viewModel.uiState.value.errorResId)
        assertEquals(0, dataStore.updateCount)
        assertNull(viewModel.uiState.value.completedResult)
    }

    @Test
    fun retry_currentAccessFailure_rechecksWithoutCompleting() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)
        val accessor = FakeSafDirectoryAccessor(directoryInfo = null)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs, fakeAccessor = accessor)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canConfirm)
        accessor.directoryInfo = DirectoryInfo(true, true, "Recovered")
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canConfirm)
        assertNull(viewModel.uiState.value.errorResId)
        assertEquals(1, dataStore.updateCount)
        assertNull(viewModel.uiState.value.completedResult)
    }

    @Test
    fun savedStateHandle_restoresCandidateUri() = testScope.runTest {
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val savedHandle = SavedStateHandle(mapOf("key_candidate_uri" to candidateVaultUri.toString()))
        val fakeAccessor = FakeSafDirectoryAccessor(
            directoryInfo = DirectoryInfo(isDirectory = true, supportsCreate = true, displayName = "Restored Folder")
        )

        val (viewModel, _) = createViewModel(
            savedStateHandle = savedHandle,
            vaultPreferences = prefs,
            fakeAccessor = fakeAccessor,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(VaultConfigUiState.NotConfigured, state.configState)
        assertTrue(state.canConfirm)
        assertEquals(0, dataStore.updateCount)
        assertEquals("Restored Folder", state.candidateVault?.displayName)
        assertEquals(VaultOperationStage.Idle, state.stage) // Does NOT auto-save or auto-navigate
        assertNull(state.completedResult)
    }

    @Test
    fun restoredCandidate_keepsCurrentConfiguration_andWaitsForBothChecks() = testScope.runTest {
        val io = PausingDispatcher(testDispatcher)
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        prefs.setVaultUri(currentVaultUri)
        io.paused = true
        val (viewModel, _) = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf("key_candidate_uri" to candidateVaultUri.toString())),
            vaultPreferences = prefs,
            ioDispatcher = io,
        )
        runCurrent()
        assertFalse(viewModel.uiState.value.canConfirm)
        io.resume()
        advanceUntilIdle()
        assertEquals(currentVaultUri, viewModel.uiState.value.currentVault?.uri)
        assertEquals(candidateVaultUri, viewModel.uiState.value.candidateVault?.uri)
        assertTrue(viewModel.uiState.value.canConfirm)
        assertEquals(1, dataStore.updateCount)
        assertNull(viewModel.uiState.value.completedResult)
    }

    @Test
    fun rapidSequentialFolderPicks_cancelStaleChecks() = testScope.runTest {
        val uri1 = Uri.parse("content://test/tree/folder1")
        val uri2 = Uri.parse("content://test/tree/folder2")
        val fakeAccessor = object : SafDirectoryAccessor {
            override fun hasPersistedReadPermission(uri: Uri): Boolean = true
            override fun hasPersistedWritePermission(uri: Uri): Boolean = true
            override fun queryDirectoryInfo(uri: Uri): DirectoryInfo {
                return DirectoryInfo(
                    isDirectory = true,
                    supportsCreate = true,
                    displayName = if (uri == uri1) "Folder 1" else "Folder 2",
                )
            }
        }
        val dataStore = InMemoryDataStore()
        val prefs = VaultPreferences(dataStore)
        val (viewModel, _) = createViewModel(vaultPreferences = prefs, fakeAccessor = FakeSafDirectoryAccessor())
        advanceUntilIdle()

        viewModel.onFolderPicked(uri1)
        viewModel.onFolderPicked(uri2)
        advanceUntilIdle()

        assertEquals("Default Vault", viewModel.uiState.value.candidateVault?.displayName)
        assertEquals(uri2, viewModel.uiState.value.candidateVault?.uri)
    }
}
