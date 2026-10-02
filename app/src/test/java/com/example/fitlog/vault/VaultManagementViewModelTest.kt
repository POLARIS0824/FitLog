package com.example.fitlog.vault

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    @Test fun cancelAndFailedClearKeepConnectionAndOnlySuccessfulClearDisconnects() = runTest(dispatcher) {
        val uri = Uri.parse("content://test/tree/vault")
        val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Configured(uri, "00000000-0000-4000-8000-000000000001"))
        var calls = 0; var fail = true
        val vm = VaultManagementViewModel(config, { VaultFolderInfo(it, "Vault", VaultAccessStatus.CanCreateFiles) }, {
            calls++
            if (fail) Result.failure(IOException()) else { config.value = VaultConfigState.NotConfigured; Result.success(Unit) }
        })
        store.put("manage", vm); runCurrent()
        vm.requestDisconnect(); vm.cancelDisconnect(); vm.disconnect(); runCurrent()
        assertEquals(0, calls); assertEquals(uri, vm.folder?.uri)
        vm.requestDisconnect(); vm.disconnect(); runCurrent()
        assertNotNull(vm.error); assertEquals(uri, vm.folder?.uri)
        fail = false; vm.disconnect(); runCurrent()
        assertNull(vm.folder); assertFalse(vm.confirmDisconnect); assertNull(vm.error)
    }
    @Test fun repeatedDisconnectIsSingleFlight() = runTest(dispatcher) {
        val config = MutableStateFlow<VaultConfigState>(VaultConfigState.Configured(Uri.parse("vault"), "00000000-0000-4000-8000-000000000001"))
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val vm = VaultManagementViewModel(config, { VaultFolderInfo(it, "Vault", VaultAccessStatus.ReadOnly) }, {
            calls++; gate.await(); config.value = VaultConfigState.NotConfigured; Result.success(Unit)
        })
        store.put("manage", vm); runCurrent(); vm.requestDisconnect()
        vm.disconnect(); vm.disconnect(); runCurrent()
        assertEquals(1, calls); assertTrue(vm.busy)
        gate.complete(Unit); runCurrent(); assertNull(vm.folder)
    }
}
