package com.example.fitlog.data.vault

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class TodayLogResolverTest {
    private val vault = "content://test/tree/root"
    private val vaultId = "00000000-0000-4000-8000-000000000001"
    private val date = LocalDate.of(2026, 9, 30)

    @Test fun existingReadOnlyFileUsesUuidSettingsAndConfiguredSafDirectory() = runTest {
        val config = DiarySettings(listOf("daily", "training"), DiaryDateFormat.Compact)
        val existing = MarkdownFile("content://test/document/today", "20260930.md", "unused", false)
        val files = TestTodayFiles(existing = existing, creationAllowed = false)
        var settingsKey: String? = null
        val settings = object : DiarySettingsStore {
            override suspend fun read(vault: String): DiarySettings {
                settingsKey = vault
                return config
            }
            override suspend fun save(vault: String, settings: DiarySettings) = error("Unexpected settings write")
        }

        val resolved = TodayLogResolver(settings, files, files).resolve(vault, date, vaultId)

        assertEquals(vaultId, settingsKey)
        assertEquals(vault to config.directoryPath, files.resolvedLocation)
        assertEquals(files.directory to "20260930.md", files.lookup)
        assertEquals(
            ResolvedTodayLog(vault, vaultId, existing.uri, date, files.directory, "20260930.md", "daily/training/20260930.md"),
            resolved,
        )
        assertEquals(0, files.creationChecks)
    }

    @Test fun missingFileUsesSuppliedDateAndEachFormatWithoutCreatingIt() = runTest {
        val capturedDate = LocalDate.of(2021, 1, 1)
        val names = mapOf(
            DiaryDateFormat.Dashed to "2021-01-01.md",
            DiaryDateFormat.Compact to "20210101.md",
            DiaryDateFormat.Chinese to "2021年01月01日.md",
        )
        for (path in listOf(emptyList(), listOf("daily"))) {
            for ((format, expectedName) in names) {
                val files = TestTodayFiles()
                val resolved = resolver(DiarySettings(path, format), files).resolve(vault, capturedDate)

                assertNull(resolved.document)
                assertEquals(capturedDate, resolved.date)
                assertEquals(vault, resolved.vaultId)
                assertEquals(expectedName, resolved.fileName)
                assertEquals((path + expectedName).joinToString("/"), resolved.displayPath)
                assertEquals(vault to path, files.resolvedLocation)
                assertEquals(files.directory to expectedName, files.lookup)
                assertEquals(1, files.creationChecks)
            }
        }
    }

    @Test fun missingReadOnlyFileReturnsCreationUnavailable() = runTest {
        val files = TestTodayFiles(creationAllowed = false)

        try {
            resolver(DiarySettings(listOf("daily")), files).resolve(vault, date)
            throw AssertionError("Expected DiaryCreationUnavailable")
        } catch (_: DiaryCreationUnavailable) {
            assertEquals(files.directory to "2026-09-30.md", files.lookup)
            assertEquals(1, files.creationChecks)
        }
    }

    @Test fun unavailableConfiguredDirectoryDoesNotFallBackToRoot() = runTest {
        val failure = IOException("Directory unavailable")
        val files = TestTodayFiles(directoryFailure = failure)

        try {
            resolver(DiarySettings(listOf("deleted")), files).resolve(vault, date)
            throw AssertionError("Expected directory resolution failure")
        } catch (error: IOException) {
            assertSame(failure, error)
            assertEquals(vault to listOf("deleted"), files.resolvedLocation)
            assertNull(files.lookup)
            assertEquals(0, files.creationChecks)
        }
    }

    private fun resolver(config: DiarySettings, files: TestTodayFiles) = TodayLogResolver(
        object : DiarySettingsStore {
            override suspend fun read(vault: String) = config
            override suspend fun save(vault: String, settings: DiarySettings) = error("Unexpected settings write")
        },
        files,
        files,
    )
}

private class TestTodayFiles(
    private val existing: MarkdownFile? = null,
    private val creationAllowed: Boolean = true,
    private val directoryFailure: IOException? = null,
) : DiaryDirectories, MarkdownDocuments {
    val directory = "content://test/document/configured-directory"
    var resolvedLocation: Pair<String, List<String>>? = null
    var lookup: Pair<String, String>? = null
    var creationChecks = 0

    override suspend fun resolveDirectory(vault: String, path: List<String>): String {
        resolvedLocation = vault to path
        directoryFailure?.let { throw it }
        return directory
    }

    override suspend fun find(vault: String, name: String): MarkdownFile? {
        lookup = vault to name
        return existing
    }

    override suspend fun canCreate(directory: String): Boolean {
        assertEquals(this.directory, directory)
        creationChecks++
        return creationAllowed
    }

    override suspend fun directories(directory: String): List<DiaryDirectory> = error("Unexpected enumeration")
    override suspend fun scan(vault: String): MarkdownScan = error("Unexpected scan")
    override suspend fun read(uri: String): MarkdownSnapshot = error("Unexpected body read")
    override suspend fun create(vault: String, name: String): MarkdownFile = error("Resolution must not create files")
    override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot =
        error("Resolution must not write files")
}
