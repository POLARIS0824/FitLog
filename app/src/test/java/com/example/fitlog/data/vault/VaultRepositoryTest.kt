package com.example.fitlog.data.vault

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VaultRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AFitLogVault")

    private class FakeSafDirectoryAccessor(
        var hasReadPermission: Boolean = true,
        var hasWritePermission: Boolean = true,
        var directoryInfo: DirectoryInfo? = DirectoryInfo(isDirectory = true, supportsCreate = true),
        var queryException: Throwable? = null,
    ) : SafDirectoryAccessor {
        override fun hasPersistedReadPermission(uri: Uri): Boolean = hasReadPermission
        override fun hasPersistedWritePermission(uri: Uri): Boolean = hasWritePermission
        override fun queryDirectoryInfo(uri: Uri): DirectoryInfo? {
            queryException?.let { throw it }
            return directoryInfo
        }
    }

    @Test
    fun checkAccess_whenMissingPersistedReadPermission_returnsNeedsReauthorization() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(hasReadPermission = false)
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.NeedsReauthorization, status)
    }

    @Test
    fun checkAccess_whenQueryThrowsSecurityException_returnsNeedsReauthorization() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            queryException = SecurityException("Permission revoked by system"),
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.NeedsReauthorization, status)
    }

    @Test
    fun checkAccess_whenNormalDirectoryWithFullPermissions_returnsCanCreateFiles() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            hasWritePermission = true,
            directoryInfo = DirectoryInfo(isDirectory = true, supportsCreate = true),
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.CanCreateFiles, status)
    }

    @Test
    fun checkAccess_whenMissingWritePermission_returnsReadOnly() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            hasWritePermission = false,
            directoryInfo = DirectoryInfo(isDirectory = true, supportsCreate = true),
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.ReadOnly, status)
    }

    @Test
    fun checkAccess_whenDirectoryDoesNotSupportCreate_returnsReadOnly() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            hasWritePermission = true,
            directoryInfo = DirectoryInfo(isDirectory = true, supportsCreate = false),
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.ReadOnly, status)
    }

    @Test
    fun checkAccess_whenDirectoryNotFound_returnsDirectoryUnavailable() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            directoryInfo = null,
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.DirectoryUnavailable, status)
    }

    @Test
    fun checkAccess_whenTargetIsNotDirectory_returnsDirectoryUnavailable() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            directoryInfo = DirectoryInfo(isDirectory = false, supportsCreate = false),
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertEquals(VaultAccessStatus.DirectoryUnavailable, status)
    }

    @Test
    fun checkAccess_whenQueryThrowsUnexpectedException_returnsFailed() = testScope.runTest {
        val ioException = IOException("Underlying ContentProvider crash")
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            queryException = ioException,
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        val status = repository.checkAccess(testUri)

        assertTrue("Status must be Failed", status is VaultAccessStatus.Failed)
        assertEquals(ioException, (status as VaultAccessStatus.Failed).cause)
    }

    @Test
    fun checkAccess_whenCoroutineCancelled_rethrowsCancellationException() = testScope.runTest {
        val fakeAccessor = FakeSafDirectoryAccessor(
            hasReadPermission = true,
            queryException = CancellationException("Job was cancelled"),
        )
        val repository = VaultRepository(safAccessor = fakeAccessor, ioDispatcher = testDispatcher)

        try {
            repository.checkAccess(testUri)
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertEquals("Job was cancelled", e.message)
        }
    }
}
