package com.example.fitlog.data.index

import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
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
    @Test fun uniquenessTransactionRollbackAndReopenPersistence() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, SourceIndexDatabase::class.java, name).build()
        val row = IndexedSource("vault", "uri", "note.md", "note.md", "vault", true, "first", 1)
        val db = open()
        try {
            val store = RoomSourceIndexStore(db)
            store.commit(listOf(row), IndexedScan("vault", IndexedScan.COMPLETE, 1))
            store.commit(listOf(row.copy(fingerprint = "second")))
            assertEquals(1, store.sources("vault").size)
            try {
                db.withTransaction { db.index().putSources(listOf(row.copy(fingerprint = "rollback"))); throw IOException() }
            } catch (_: IOException) { }
            assertEquals("second", store.sources("vault").single().fingerprint)
        } finally { db.close() }
        val reopened = open()
        try { assertEquals("second", reopened.index().sources("vault").single().fingerprint) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}
