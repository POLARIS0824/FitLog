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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.LocalDate
import com.example.fitlog.data.vault.DiaryCreationUnavailable

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VaultFlowControllerTest {
    @Test fun managementImportReturnsToLog() = runTest {
        val flow = controller()
        flow.openRoute(FitLogRoute.VaultManagement)
        val setup = FitLogRoute.VaultSetup()
        flow.openRoute(setup)
        flow.onSetupCompleted(setup)
        assertEquals(listOf(FitLogRoute.Log), stack)
    }

    @Test fun recoveryCanOpenEditorAndReturnToCenter() = runTest {
        val flow = controller()
        flow.openRoute(FitLogRoute.RecoveryCenter)
        val editor = FitLogRoute.Editor("old-vault", directory = "old-directory", recoveryId = "draft:id")
        flow.openRoute(editor)
        assertEquals(editor, stack.last())
        flow.back()
        assertEquals(FitLogRoute.RecoveryCenter, stack.last())
    }
    private val uri = Uri.parse("content://test/tree/vault")
    private val stack = mutableListOf<NavKey>(FitLogRoute.Today)
    private val errors = mutableListOf<Int>()

    private fun TestScope.controller(
        config: suspend () -> VaultConfigState = { VaultConfigState.NotConfigured },
        access: suspend (Uri) -> VaultAccessStatus = { VaultAccessStatus.CanCreateFiles },
    ) = VaultFlowController(stack, this, config, access, errors::add, { vault, date -> FitLogRoute.Editor(vault, date = date.toString()) })

    @Test
    fun firstAdd_entersVaultSetup_andOnCompleteReplacesWithEditor_andBackReturnsToOrigin() = runTest {
        var configured = false
        val flow = controller(config = { if (configured) VaultConfigState.Configured(uri) else VaultConfigState.NotConfigured })
        flow.openTodayLog()
        advanceUntilIdle()
        val route = stack.last() as FitLogRoute.VaultSetup
        assertTrue(route.createAfterSetup)
        assertEquals(listOf(FitLogRoute.Today, route), stack)

        configured = true
        flow.onSetupCompleted(route)
        advanceUntilIdle()
        assertEquals(2, stack.size)
        assertEquals(uri.toString(), (stack.last() as FitLogRoute.Editor).vault)

        flow.back()
        assertEquals(listOf(FitLogRoute.Today), stack)
    }

    @Test
    fun importFolder_entersVaultSetup_andOnCompleteSwitchesTopLevelToLog() = runTest {
        val flow = controller()
        flow.importFolder()
        val route = stack.last() as FitLogRoute.VaultSetup
        assertFalse(route.createAfterSetup)
        assertEquals(listOf(FitLogRoute.Today, route), stack)

        flow.onSetupCompleted(route)
        assertEquals(listOf(FitLogRoute.Log), stack)
    }

    @Test
    fun staleSetupCompletion_doesNotAffectNewDestination() = runTest {
        val flow = controller()
        flow.importFolder()
        val oldRoute = stack.last() as FitLogRoute.VaultSetup

        flow.back()
        flow.importFolder()
        val newRoute = stack.last() as FitLogRoute.VaultSetup

        // Calling completion for oldRoute should be ignored
        flow.onSetupCompleted(oldRoute)
        assertEquals(newRoute, stack.last())
        assertEquals(listOf(FitLogRoute.Today, newRoute), stack)
    }

    @Test
    fun repeatedAdd_onlyChecksOnce_andOpensOneEditor() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        var checks = 0
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { checks++; result.await() },
        )
        flow.openTodayLog()
        flow.openTodayLog()
        runCurrent()
        flow.openTodayLog()
        assertEquals(1, checks)
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        flow.openTodayLog()
        flow.importFolder()
        advanceUntilIdle()
        assertEquals(2, stack.size)
        assertEquals(uri.toString(), (stack.last() as FitLogRoute.Editor).vault)
    }

    @Test
    fun importDuringAddCheck_rejectsLateEditorNavigation() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { withContext(NonCancellable) { result.await() } },
        )
        flow.openTodayLog()
        runCurrent()
        flow.importFolder()
        val route = stack.last()
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        assertEquals(route, stack.last())
        assertEquals(2, stack.size)
        assertFalse(flow.busy)
    }

    @Test
    fun tabSwitchDuringAddCheck_rejectsLateNavigationEvenAfterReturning() = runTest {
        val result = CompletableDeferred<VaultAccessStatus>()
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { withContext(NonCancellable) { result.await() } },
        )
        flow.openTodayLog()
        runCurrent()
        flow.navigateTo(FitLogRoute.Log)
        flow.navigateTo(FitLogRoute.Today)
        result.complete(VaultAccessStatus.CanCreateFiles)
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
    }

    @Test
    fun configReadFailure_doesNotPretendVaultIsUnconfigured() = runTest {
        val flow = controller(config = { VaultConfigState.Failed(IOException()) })
        flow.openTodayLog()
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
        assertEquals(listOf(R.string.vault_error_load_config_failed), errors)
        assertFalse(flow.busy)
    }

    @Test
    fun reauthorization_preservesAddPurpose() = runTest {
        val flow = controller(
            config = { VaultConfigState.Configured(uri) },
            access = { VaultAccessStatus.NeedsReauthorization },
        )
        flow.openTodayLog()
        advanceUntilIdle()
        assertTrue((stack.last() as FitLogRoute.VaultSetup).createAfterSetup)
        assertEquals(listOf(R.string.vault_error_needs_reauthorization), errors)
    }

    @Test
    fun setupRouteSerialization_preservesPurposeAndIdentity() {
        val route = FitLogRoute.VaultSetup(createAfterSetup = true)
        val restored = Json.decodeFromString<FitLogRoute.VaultSetup>(Json.encodeToString(route))
        assertEquals(route, restored)
    }

    @Test fun firstConnectionKeepsTheDateCapturedBeforeMidnight() = runTest {
        var date = LocalDate.of(2026, 9, 30)
        var configured = false
        val flow = VaultFlowController(stack, this,
            { if (configured) VaultConfigState.Configured(uri) else VaultConfigState.NotConfigured },
            { VaultAccessStatus.CanCreateFiles }, errors::add,
            { vault, captured -> FitLogRoute.Editor(vault, date = captured.toString()) }, { date })
        flow.openTodayLog()
        advanceUntilIdle()
        val setup = stack.last() as FitLogRoute.VaultSetup
        date = date.plusDays(1)
        configured = true
        flow.onSetupCompleted(setup)
        advanceUntilIdle()
        assertEquals("2026-09-30", (stack.last() as FitLogRoute.Editor).date)
    }

    @Test fun lateTodayResolutionCannotNavigateAfterOpeningAnotherNote() = runTest {
        val pending = CompletableDeferred<FitLogRoute.Editor>()
        val flow = VaultFlowController(stack, this, { VaultConfigState.Configured(uri) },
            { VaultAccessStatus.CanCreateFiles }, errors::add,
            { _, _ -> withContext(NonCancellable) { pending.await() } })
        flow.openTodayLog()
        runCurrent()
        val other = FitLogRoute.Editor(uri.toString(), "other")
        flow.openRoute(other)
        flow.back()
        pending.complete(FitLogRoute.Editor(uri.toString(), "today"))
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Today), stack)
        assertFalse(flow.busy)
    }

    @Test fun readOnlyExistingTodayIsOpenedWithoutCreationPermission() = runTest {
        val existing = FitLogRoute.Editor(uri.toString(), "existing")
        val flow = VaultFlowController(stack, this, { VaultConfigState.Configured(uri) },
            { VaultAccessStatus.ReadOnly }, errors::add, { _, _ -> existing })
        flow.openTodayLog()
        advanceUntilIdle()
        assertEquals(existing, stack.last())
        assertTrue(errors.isEmpty())
    }

    @Test fun connectedReadOnlyMissingTodayReturnsToLogWithFeedback() = runTest {
        val setup = FitLogRoute.VaultSetup(createAfterSetup = true)
        stack.add(setup)
        val flow = VaultFlowController(stack, this, { VaultConfigState.Configured(uri) },
            { VaultAccessStatus.ReadOnly }, errors::add, { _, _ -> throw DiaryCreationUnavailable() })
        flow.onSetupCompleted(setup)
        advanceUntilIdle()
        assertEquals(listOf(FitLogRoute.Log), stack)
        assertEquals(listOf(R.string.vault_error_read_only), errors)
    }
}
