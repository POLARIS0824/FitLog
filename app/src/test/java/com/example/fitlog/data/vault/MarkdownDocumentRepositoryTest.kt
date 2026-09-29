package com.example.fitlog.data.vault

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownDocumentRepositoryTest {
    private lateinit var provider: TestMarkdownProvider
    private lateinit var repository: MarkdownDocumentRepository
    private val vault = "content://markdown.test/tree/root"
    @Before fun setup() {
        provider = TestMarkdownProvider()
        provider.attachInfo(RuntimeEnvironment.getApplication(), android.content.pm.ProviderInfo().apply {
            authority = "markdown.test"
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal("markdown.test", provider)
        RuntimeEnvironment.getApplication().grantUriPermission(
            RuntimeEnvironment.getApplication().packageName, Uri.parse(vault),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        repository = MarkdownDocumentRepository(RuntimeEnvironment.getApplication())
    }
    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(vault), id).toString()

    private fun todayResolver(settings: DiarySettings = DiarySettings()) = TodayLogResolver(
        object : DiarySettingsStore {
            override suspend fun read(vault: String) = settings
            override suspend fun save(vault: String, settings: DiarySettings) = error("unused")
        }, repository, repository,
    )

    @Test fun todayOpensExistingChildWithoutConfusingRootFile() = runTest {
        provider.add("root/sub", "daily", "root", null)
        provider.add("root/a", "2026-09-30.md", "root", "root".toByteArray())
        provider.add("root/sub/a", "2026-09-30.md", "root/sub", "child".toByteArray())
        val route = todayResolver(DiarySettings(listOf("daily"))).resolve(vault, LocalDate.of(2026, 9, 30))
        assertEquals(uri("root/sub/a"), route.document)
        assertEquals(uri("root/sub"), route.directory)
        assertEquals("child", repository.read(route.document!!).text)
        assertEquals(4, provider.nodes.size)
    }

    @Test fun missingTodayIsNotCreatedUntilEditorSavesAndCreateUsesChild() = runTest {
        provider.add("root/sub", "daily", "root", null)
        val route = todayResolver(DiarySettings(listOf("daily"), DiaryDateFormat.Compact))
            .resolve(vault, LocalDate.of(2026, 9, 30))
        assertNull(route.document)
        assertEquals("20260930.md", route.fileName)
        assertEquals(2, provider.nodes.size)
        val created = repository.create(route.directory, route.fileName)
        assertEquals("root/sub", provider.nodes[DocumentsContract.getDocumentId(Uri.parse(created.uri))]!!.parent)
        assertNull(repository.find(vault, route.fileName))
    }

    @Test fun existingReadOnlyTodayOpensButMissingTodayCannotBeCreated() = runTest {
        provider.add("root/sub", "daily", "root", null)
        provider.nodes["root/sub"]!!.writable = false
        provider.add("root/sub/a", "2026-09-30.md", "root/sub", "read only".toByteArray())
        provider.nodes["root/sub/a"]!!.writable = false
        val resolver = todayResolver(DiarySettings(listOf("daily")))
        val route = resolver.resolve(vault, LocalDate.of(2026, 9, 30))
        assertFalse(repository.read(route.document!!).file.writable)
        try { resolver.resolve(vault, LocalDate.of(2026, 10, 1)); fail() }
        catch (_: DiaryCreationUnavailable) { }
    }

    @Test fun directorySelectionCannotEscapeVaultOrFallBackToRoot() = runTest {
        provider.add("root/sub", "daily", "root", null)
        provider.add("root/hidden", ".obsidian", "root", null)
        assertEquals(listOf("daily"), repository.directories(vault).map { it.name })
        try { repository.resolveDirectory(vault, listOf("..")); fail() } catch (_: IllegalArgumentException) { }
        try { repository.resolveDirectory(vault, listOf("deleted")); fail() } catch (_: IOException) { }
        try { repository.resolveDirectory(vault, listOf(".obsidian")); fail() } catch (_: IOException) { }
    }

    @Test fun fileAppearingAfterTodayLookupIsNotOverwritten() = runTest {
        val route = todayResolver().resolve(vault, LocalDate.of(2026, 9, 30))
        provider.add("root/external", route.fileName, "root", "external".toByteArray())
        try { repository.create(route.directory, route.fileName); fail() } catch (_: NameCollision) { }
        assertEquals("external", provider.bytes("root/external").toString(Charsets.UTF_8))
    }

    @Test fun recursiveScanSkipsHiddenDirectoriesAndReportsPartialFailure() = runTest {
        provider.add("root/a", "A.MD", "root", "hello".toByteArray())
        provider.add("root/sub", "sub", "root", null)
        provider.add("root/sub/a", "A.MD", "root/sub", byteArrayOf())
        provider.add("root/hidden", ".obsidian", "root", null)
        provider.add("root/hidden/note", "secret.md", "root/hidden", byteArrayOf())
        provider.add("root/image", "image.png", "root", byteArrayOf())
        provider.add("root/bad", "bad", "root", null)
        provider.failDirectory = "root/bad"
        val result = repository.scan(vault)
        assertTrue(result.partial)
        assertEquals(listOf("A.MD", "sub/A.MD"), result.files.map { it.path })
        assertEquals(result.files, repository.scan(vault).files)
    }
    @Test fun utf8BomAndCrlfRoundTripAndTruncation() = runTest {
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "卧推\r\n123456".toByteArray()
        provider.add("root/a", "a.md", "root", bytes)
        val before = repository.read(uri("root/a"))
        assertTrue(before.bom)
        assertEquals("卧推\r\n123456", before.text)
        repository.write(before.file.uri, "短\r\n", before.bom, before.fingerprint)
        assertArrayEquals(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "短\r\n".toByteArray(), provider.bytes("root/a"))
    }
    @Test fun malformedUtf8CannotBeEdited() = runTest {
        provider.add("root/a", "a.md", "root", byteArrayOf(0xc3.toByte(), 0x28))
        try { repository.read(uri("root/a")); fail() } catch (_: CharacterCodingException) { }
    }
    @Test fun collisionAndProviderRenameDoNotWriteExistingContent() = runTest {
        provider.add("root/a", "a.md", "root", "keep".toByteArray())
        try { repository.create(vault, "a.md"); fail() } catch (_: NameCollision) { }
        assertEquals("keep", provider.bytes("root/a").toString(Charsets.UTF_8))
        provider.rename = true
        try { repository.create(vault, "b.md"); fail() } catch (_: UnexpectedDocumentName) { }
        assertEquals("keep", provider.bytes("root/a").toString(Charsets.UTF_8))
    }
    @Test fun changedContentAndReadOnlyPreventWrite() = runTest {
        provider.add("root/a", "a.md", "root", "original".toByteArray())
        val before = repository.read(uri("root/a"))
        provider.nodes["root/a"]!!.file!!.writeText("external")
        try { repository.write(before.file.uri, "mine", false, before.fingerprint); fail() } catch (_: DocumentConflict) { }
        val external = repository.read(before.file.uri)
        provider.nodes["root/a"]!!.writable = false
        try { repository.write(before.file.uri, "mine", false, external.fingerprint); fail() } catch (_: SecurityException) { }
        assertEquals("external", provider.bytes("root/a").toString(Charsets.UTF_8))
    }
    @Test fun readbackFailureIsNotReportedAsSuccess() = runTest {
        provider.add("root/a", "a.md", "root", "old".toByteArray())
        val before = repository.read(uri("root/a"))
        provider.failReadAfterWrite = true
        try { repository.write(before.file.uri, "new", false, before.fingerprint); fail() } catch (_: IOException) { }
    }
}

private class TestMarkdownProvider : ContentProvider() {
    data class Node(val name: String, val parent: String?, val file: File?, var writable: Boolean = true)
    val nodes = linkedMapOf("root" to Node("root", null, null))
    var failDirectory: String? = null
    var rename = false
    var failReadAfterWrite = false
    private var wrote = false
    fun add(id: String, name: String, parent: String, bytes: ByteArray?) {
        val file = bytes?.let { File.createTempFile("markdown-test", ".md").apply { writeBytes(it); deleteOnExit() } }
        nodes[id] = Node(name, parent, file)
    }
    fun bytes(id: String) = nodes.getValue(id).file!!.readBytes()
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val id = DocumentsContract.getDocumentId(uri)
        val children = uri.lastPathSegment == "children"
        if (children && id == failDirectory) throw SecurityException()
        val rows = if (children) nodes.filterValues { it.parent == id } else nodes.filterKeys { it == id }
        val columns = requireNotNull(projection)
        return MatrixCursor(columns).apply {
            rows.forEach { (key, node) ->
                addRow(columns.map<String, Any?> { column -> when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> key
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> node.name
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> if (node.file == null) DocumentsContract.Document.MIME_TYPE_DIR else "text/markdown"
                    DocumentsContract.Document.COLUMN_FLAGS -> if (node.writable) DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0
                    else -> null
                } }.toTypedArray())
            }
        }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode == "r" && wrote && failReadAfterWrite) throw IOException()
        if (mode.contains('w')) wrote = true
        return ParcelFileDescriptor.open(nodes.getValue(DocumentsContract.getDocumentId(uri)).file!!, ParcelFileDescriptor.parseMode(mode))
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val parent = extras!!.getParcelable<Uri>("uri")!!
        val id = DocumentsContract.getDocumentId(parent) + "/created-${nodes.size}"
        val name = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)!! + if (rename) " (1)" else ""
        add(id, name, DocumentsContract.getDocumentId(parent), byteArrayOf())
        return Bundle().apply { putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(parent, id)) }
    }
    override fun getType(uri: Uri) = "text/markdown"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("unused")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("unused")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("unused")
}
