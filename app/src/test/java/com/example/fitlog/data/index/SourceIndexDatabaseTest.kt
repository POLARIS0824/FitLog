package com.example.fitlog.data.index

import androidx.room.Room
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
    @Test fun migrationPreservesVersionOneSourcesAndRequiresMetadataRevalidation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${UUID.randomUUID()}.db"
        val old = context.openOrCreateDatabase(name, 0, null)
        old.execSQL("CREATE TABLE sources (vault TEXT NOT NULL, uri TEXT NOT NULL, name TEXT NOT NULL, path TEXT NOT NULL, directory TEXT, writable INTEGER NOT NULL, fingerprint TEXT, verifiedAt INTEGER, status TEXT NOT NULL, PRIMARY KEY(vault, uri))")
        old.execSQL("CREATE TABLE scans (vault TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, completedAt INTEGER)")
        old.execSQL("INSERT INTO sources VALUES ('vault', 'uri', 'note.md', 'daily/note.md', 'daily', 1, 'hash', 42, 'available')")
        old.execSQL("INSERT INTO scans VALUES ('vault', 'complete', 42)")
        old.version = 1
        old.close()
        val migrated = Room.databaseBuilder(context, SourceIndexDatabase::class.java, name)
            .addMigrations(SourceIndexDatabase.MIGRATION_1_2).build()
        try {
            val row = migrated.index().sources("vault").single()
            assertEquals("hash", row.fingerprint)
            assertEquals("daily/note.md", row.path)
            assertEquals(42L, row.verifiedAt)
            assertNull(row.lastModified); assertNull(row.size)
            val snapshot = RoomSourceIndexStore(migrated).observe("vault").first()
            assertEquals(42L, snapshot.scan?.completedAt)
            assertNull(snapshot.scan?.metadataCheckedAt); assertNull(snapshot.scan?.fullVerifiedAt)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

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
