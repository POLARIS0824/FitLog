package com.example.fitlog.data.index

import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceIndexDatabaseTest {
    @Test fun schemaChangeDestructivelyRebuildsTheIndexDatabase() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "rebuild-${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        val previous = context.openOrCreateDatabase(name, 0, null)
        previous.execSQL("CREATE TABLE retired_index (body TEXT NOT NULL)")
        previous.execSQL("INSERT INTO retired_index VALUES ('discardable')")
        previous.version = 3
        previous.close()
        val db = SourceIndexDatabase.open(context, name)
        try {
            val store = RoomSourceIndexStore(db)
            assertTrue(store.sources(id).isEmpty())
            assertNull(store.observe(id).first().scan)
            db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name = 'retired_index'").use {
                assertEquals(0, it.count)
            }
            val row = IndexedSource(id, "uri", "note.md", "note.md", "content://test/tree/a", true)
            store.commit(listOf(row), IndexedScan(id, IndexedScan.COMPLETE, 1))
            assertEquals(row, store.sources(id).single())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun uniquenessTransactionRollbackAndReopenPersistence() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = SourceIndexDatabase.open(context, name)
        val row = IndexedSource("00000000-0000-4000-8000-000000000001", "uri", "note.md", "note.md", "vault", true)
        val db = open()
        try {
            val store = RoomSourceIndexStore(db)
            store.commit(listOf(row), IndexedScan("00000000-0000-4000-8000-000000000001", IndexedScan.COMPLETE, 1))
            store.commit(listOf(row.copy(path = "second.md")))
            assertEquals(1, store.sources("00000000-0000-4000-8000-000000000001").size)
            try {
                db.withTransaction { db.index().putSources(listOf(row.copy(path = "rollback.md"))); throw IOException() }
            } catch (_: IOException) { }
            assertEquals("second.md", store.sources("00000000-0000-4000-8000-000000000001").single().path)
        } finally { db.close() }
        val reopened = open()
        try { assertEquals("second.md", reopened.index().sources("00000000-0000-4000-8000-000000000001").single().path) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}
