package com.example.fitlog.vault

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VaultFlowControllerTest {
    private val uri = Uri.parse("content://test/tree/vault")
    private val stack = mutableListOf<NavKey>(FitLogRoute.Today)
    private val errors = mutableListOf<Int>()
    private var saved = 0

    private fun TestScope.controller(
        config: suspend () -> VaultConfigState = { VaultConfigState.NotConfigured },
        access: suspend (Uri) -> VaultAccessStatus = { VaultAccessStatus.CanCreateFiles },
        save: suspend (Uri) -> Result<Unit> = { saved++; Result.success(Unit) },
    ) = VaultFlowController(stack, this, config, access, save, errors::add)

    @Test fun firstAdd_continuesToEditor_andBackReturnsToOrigin() = runTest {
        val flow = controller()
        flow.createFile()
        advanceUntilIdle()
        val route = stack.last() as FitLogRoute.VaultSetup
        assertTrue(route.createAfterSetup)
        flow.selectFolder(route, uri)
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today, FitLogRoute.Editor), stack)
        assertEquals(1, saved)
        flow.back()
        assertEquals(listOf(FitLogRoute.Today), stack)
    }

    @Test fun failedSave_staysOnSetup_andCanRetry() = runTest {
        var fail = true
        val flow = controller(save = {
            if (fail) Result.failure(IOException()) else Result.success(Unit)
        })
        flow.importFolder()
        val route = stack.last() as FitLogRoute.VaultSetup
        flow.selectFolder(route, uri)
        advanceUntilIdle()
        assertEquals(route, stack.last())
        assertEquals(R.string.vault_error_save_config_failed, flow.setupError)
        assertFalse(flow.busy)
        assertFalse(flow.saving)
        fail = false
        flow.selectFolder(route, uri)
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
        assertNull(flow.setupError)
    }

    @Test fun backDuringValidation_oldResultCannotSaveOrPopNewSetup() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        val flow = controller(access = { withContext(NonCancellable) { result.await() } })
        flow.importFolder()
        val oldRoute = stack.last() as FitLogRoute.VaultSetup
        flow.selectFolder(oldRoute, uri)
        runCurrent()
        flow.back()
        flow.importFolder()
        val newRoute = stack.last()
        assertNotEquals(oldRoute, newRoute)
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        assertEquals(newRoute, stack.last())
        assertEquals(0, saved)
        flow.selectFolder(oldRoute, uri)
        advanceUntilIdle()
        assertEquals(0, saved)
    }

    @Test fun duringCommit_backAndDuplicateSelectionCannotPopOrRepeatSave() = runTest {
        val commit = CompletableDeferred<Result<Unit>>()
        val flow = controller(save = { saved++; commit.await() })
        flow.importFolder()
        val route = stack.last() as FitLogRoute.VaultSetup
        flow.selectFolder(route, uri)
        runCurrent()
        assertTrue(flow.saving)
        flow.back()
        flow.selectFolder(route, uri)
        flow.importFolder()
        flow.navigateTo(FitLogRoute.Log)
        runCurrent()
        assertEquals(route, stack.last())
        assertEquals(1, saved)
        commit.complete(Result.success(Unit))
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
        flow.back()
        assertEquals(listOf(FitLogRoute.Today), stack)
    }

    @Test fun repeatedAdd_onlyChecksOnce_andOpensOneEditor() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        var checks = 0
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { checks++; result.await() },
        )
        flow.createFile()
        flow.createFile()
        runCurrent()
        flow.createFile()
        assertEquals(1, checks)
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        flow.createFile()
        flow.importFolder()
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today, FitLogRoute.Editor), stack)
    }

    @Test fun importDuringAddCheck_rejectsLateEditorNavigation() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { withContext(NonCancellable) { result.await() } },
        )
        flow.createFile()
        runCurrent()
        flow.importFolder()
        val route = stack.last()
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        assertEquals(route, stack.last())
        assertEquals(2, stack.size)
        assertFalse(flow.busy)
    }

    @Test fun tabSwitchDuringAddCheck_rejectsLateNavigationEvenAfterReturning() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { withContext(NonCancellable) { result.await() } },
        )
        flow.createFile()
        runCurrent()
        flow.navigateTo(FitLogRoute.Log)
        flow.navigateTo(FitLogRoute.Today)
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
    }

    @Test fun readOnlyFolder_isAcceptedForImport_butRejectedForAdd() = runTest {
        val flow = controller(access = { VaultAccessStatus.ReadOnly })
        flow.importFolder()
        flow.selectFolder(stack.last() as FitLogRoute.VaultSetup, uri)
        advanceUntilIdle()
        assertEquals(1, saved)
        flow.createFile()
        advanceUntilIdle()
        val route = stack.last() as FitLogRoute.VaultSetup
        flow.selectFolder(route, uri)
        advanceUntilIdle()
        assertEquals(1, saved)
        assertEquals(route, stack.last())
        assertEquals(R.string.vault_error_read_only, flow.setupError)
    }

    @Test fun invalidFolder_doesNotReplaceConfiguration() = runTest {
        val flow = controller(access = { VaultAccessStatus.DirectoryUnavailable })
        flow.importFolder()
        val route = stack.last() as FitLogRoute.VaultSetup
        flow.selectFolder(route, uri)
        advanceUntilIdle()
        assertEquals(0, saved)
        assertEquals(route, stack.last())
        assertEquals(R.string.vault_error_directory_unavailable, flow.setupError)
    }

    @Test fun configReadFailure_doesNotPretendVaultIsUnconfigured() = runTest {
        val flow = controller(config = { VaultConfigState.Failed(IOException()) })
        flow.createFile()
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
        assertEquals(listOf(R.string.vault_error_load_config_failed), errors)
        assertFalse(flow.busy)
    }

    @Test fun reauthorization_preservesAddPurpose() = runTest {
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { VaultAccessStatus.NeedsReauthorization },
        )
        flow.createFile()
        advanceUntilIdle()
        assertTrue((stack.last() as FitLogRoute.VaultSetup).createAfterSetup)
        assertEquals(listOf(R.string.vault_error_needs_reauthorization), errors)
    }

    @Test fun setupRouteSerialization_preservesPurposeAndIdentity() {
        val route = FitLogRoute.VaultSetup(createAfterSetup = true)
        val restored = Json.decodeFromString<FitLogRoute.VaultSetup>(Json.encodeToString(route))
        assertEquals(route, restored)
    }
}
