package com.example.fitlog.data.index

import android.content.Context
import androidx.room.*
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.vault.requireVaultId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Entity(tableName = "sources", primaryKeys = ["vaultId", "uri"])
data class IndexedSource(
    val vaultId: String,
    val uri: String,
    val name: String,
    val path: String,
    val directory: String?,
    val writable: Boolean,
    val status: String = AVAILABLE,
    val contentHash: String? = null,
    val hashVersion: Int? = null,
) {
    init { requireVaultId(vaultId) }
    fun file() = MarkdownFile(uri, name, path, writable, directory)
    companion object {
        const val AVAILABLE = "available"
        const val READ_FAILED = "read_failed"
        const val MISSING = "missing"
    }
}

@Entity(tableName = "scans")
data class IndexedScan(@PrimaryKey val vaultId: String, val status: String, val completedAt: Long? = null) {
    init { requireVaultId(vaultId) }
    companion object {
        const val COMPLETE = "complete"
        const val PARTIAL = "partial"
        const val FAILED = "failed"
    }
}

data class SourceIndexSnapshot(val sources: List<IndexedSource>, val scan: IndexedScan?,
    val refreshing: Boolean = false, val refreshFailed: Boolean = false)

interface SourceIndexStore {
    fun observe(vaultId: String): Flow<SourceIndexSnapshot>
    suspend fun sources(vaultId: String): List<IndexedSource>
    suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan? = null)
}

@Dao
interface SourceIndexDao {
    @Query("SELECT * FROM sources WHERE vaultId = :vaultId")
    fun observeSources(vaultId: String): Flow<List<IndexedSource>>
    @Query("SELECT * FROM scans WHERE vaultId = :vaultId")
    fun observeScan(vaultId: String): Flow<IndexedScan?>
    @Query("SELECT * FROM sources WHERE vaultId = :vaultId")
    suspend fun sources(vaultId: String): List<IndexedSource>
    @Upsert suspend fun putSources(sources: List<IndexedSource>)
    @Upsert suspend fun putScan(scan: IndexedScan)
}

@Database(entities = [IndexedSource::class, IndexedScan::class], version = 5, exportSchema = true)
abstract class SourceIndexDatabase : RoomDatabase() {
    abstract fun index(): SourceIndexDao
    companion object {
        internal fun open(context: Context, name: String = "source-index.db"): SourceIndexDatabase =
            Room.databaseBuilder(context.applicationContext, SourceIndexDatabase::class.java, name)
                .fallbackToDestructiveMigration(dropAllTables = true).build()

        @Volatile private var instance: SourceIndexDatabase? = null
        fun get(context: Context): SourceIndexDatabase = instance ?: synchronized(this) {
            instance ?: open(context).also { instance = it }
        }
    }
}

class RoomSourceIndexStore(private val database: SourceIndexDatabase) : SourceIndexStore {
    private val dao = database.index()
    override fun observe(vaultId: String) = combine(dao.observeSources(vaultId), dao.observeScan(vaultId), ::SourceIndexSnapshot)
    override suspend fun sources(vaultId: String) = dao.sources(vaultId)
    override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) = database.withTransaction {
        dao.putSources(sources)
        if (scan != null) dao.putScan(scan)
    }
}
