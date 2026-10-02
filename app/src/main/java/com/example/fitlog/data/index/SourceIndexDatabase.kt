package com.example.fitlog.data.index

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fitlog.data.vault.MarkdownFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Entity(tableName = "sources", primaryKeys = ["vault", "uri"])
data class IndexedSource(
    @ColumnInfo(name = "vault") val vaultId: String,
    val uri: String,
    val name: String,
    val path: String,
    val directory: String?,
    val writable: Boolean,
    val fingerprint: String?,
    val verifiedAt: Long?,
    val status: String = AVAILABLE,
    val lastModified: Long? = null,
    val size: Long? = null,
) {
    fun file() = MarkdownFile(uri, name, path, writable, directory, lastModified, size)
    companion object {
        const val AVAILABLE = "available"
        const val READ_FAILED = "read_failed"
        const val MISSING = "missing"
    }
}

@Entity(tableName = "scans")
data class IndexedScan(@PrimaryKey @ColumnInfo(name = "vault") val vaultId: String, val status: String, val completedAt: Long? = null,
    val metadataCheckedAt: Long? = null, val fullVerifiedAt: Long? = null) {
    companion object {
        const val SCANNING = "scanning"
        const val COMPLETE = "complete"
        const val PARTIAL = "partial"
        const val FAILED = "failed"
        const val INTERRUPTED = "interrupted"
    }
}

data class SourceIndexSnapshot(val sources: List<IndexedSource>, val scan: IndexedScan?,
    val refreshing: Boolean = false, val refreshFailed: Boolean = false)

interface SourceIndexStore {
    fun observe(vault: String): Flow<SourceIndexSnapshot>
    suspend fun sources(vault: String): List<IndexedSource>
    suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan? = null)
    suspend fun migrateVault(legacyUri: String, vaultId: String)
}

@Dao
interface SourceIndexDao {
    @Query("SELECT * FROM sources WHERE vault = :vault")
    fun observeSources(vault: String): Flow<List<IndexedSource>>
    @Query("SELECT * FROM scans WHERE vault = :vault")
    fun observeScan(vault: String): Flow<IndexedScan?>
    @Query("SELECT * FROM sources WHERE vault = :vault")
    suspend fun sources(vault: String): List<IndexedSource>
    @Query("SELECT * FROM scans WHERE vault = :vault")
    suspend fun scan(vault: String): IndexedScan?
    @Query("DELETE FROM sources WHERE vault = :vault")
    suspend fun deleteSources(vault: String)
    @Query("DELETE FROM scans WHERE vault = :vault")
    suspend fun deleteScan(vault: String)
    @Upsert suspend fun putSources(sources: List<IndexedSource>)
    @Upsert suspend fun putScan(scan: IndexedScan)
}

@Database(entities = [IndexedSource::class, IndexedScan::class], version = 2, exportSchema = true)
abstract class SourceIndexDatabase : RoomDatabase() {
    abstract fun index(): SourceIndexDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN lastModified INTEGER")
                db.execSQL("ALTER TABLE sources ADD COLUMN size INTEGER")
                db.execSQL("ALTER TABLE scans ADD COLUMN metadataCheckedAt INTEGER")
                db.execSQL("ALTER TABLE scans ADD COLUMN fullVerifiedAt INTEGER")
            }
        }
        @Volatile private var instance: SourceIndexDatabase? = null
        fun get(context: Context): SourceIndexDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SourceIndexDatabase::class.java, "source-index.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

class RoomSourceIndexStore(private val database: SourceIndexDatabase) : SourceIndexStore {
    private val dao = database.index()
    override fun observe(vault: String) = combine(dao.observeSources(vault), dao.observeScan(vault), ::SourceIndexSnapshot)
    override suspend fun sources(vault: String) = dao.sources(vault)
    override suspend fun migrateVault(legacyUri: String, vaultId: String) = database.withTransaction {
        if (legacyUri != vaultId) {
            val existing = dao.sources(vaultId).mapTo(mutableSetOf()) { it.uri }
            dao.putSources(dao.sources(legacyUri).filter { it.uri !in existing }.map { it.copy(vaultId = vaultId) })
            if (dao.scan(vaultId) == null) dao.scan(legacyUri)?.let { dao.putScan(it.copy(vaultId = vaultId)) }
            dao.deleteSources(legacyUri)
            dao.deleteScan(legacyUri)
        }
    }
    override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) = database.withTransaction {
        dao.putSources(sources)
        if (scan != null) dao.putScan(scan)
    }
}
