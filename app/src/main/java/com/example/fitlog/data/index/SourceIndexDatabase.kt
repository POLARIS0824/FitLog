package com.example.fitlog.data.index

import android.content.Context
import androidx.room.*
import com.example.fitlog.data.vault.MarkdownFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Entity(tableName = "sources", primaryKeys = ["vault", "uri"])
data class IndexedSource(
    val vault: String,
    val uri: String,
    val name: String,
    val path: String,
    val directory: String?,
    val writable: Boolean,
    val fingerprint: String?,
    val verifiedAt: Long?,
    val status: String = AVAILABLE,
) {
    fun file() = MarkdownFile(uri, name, path, writable, directory)
    companion object {
        const val AVAILABLE = "available"
        const val READ_FAILED = "read_failed"
        const val MISSING = "missing"
    }
}

@Entity(tableName = "scans")
data class IndexedScan(@PrimaryKey val vault: String, val status: String, val completedAt: Long? = null) {
    companion object {
        const val SCANNING = "scanning"
        const val COMPLETE = "complete"
        const val PARTIAL = "partial"
        const val FAILED = "failed"
        const val INTERRUPTED = "interrupted"
    }
}

data class SourceIndexSnapshot(val sources: List<IndexedSource>, val scan: IndexedScan?)

interface SourceIndexStore {
    fun observe(vault: String): Flow<SourceIndexSnapshot>
    suspend fun sources(vault: String): List<IndexedSource>
    suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan? = null)
}

@Dao
interface SourceIndexDao {
    @Query("SELECT * FROM sources WHERE vault = :vault")
    fun observeSources(vault: String): Flow<List<IndexedSource>>
    @Query("SELECT * FROM scans WHERE vault = :vault")
    fun observeScan(vault: String): Flow<IndexedScan?>
    @Query("SELECT * FROM sources WHERE vault = :vault")
    suspend fun sources(vault: String): List<IndexedSource>
    @Upsert suspend fun putSources(sources: List<IndexedSource>)
    @Upsert suspend fun putScan(scan: IndexedScan)
}

@Database(entities = [IndexedSource::class, IndexedScan::class], version = 1, exportSchema = true)
abstract class SourceIndexDatabase : RoomDatabase() {
    abstract fun index(): SourceIndexDao
    companion object {
        @Volatile private var instance: SourceIndexDatabase? = null
        fun get(context: Context): SourceIndexDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SourceIndexDatabase::class.java, "source-index.db")
                .build().also { instance = it }
        }
    }
}

class RoomSourceIndexStore(private val database: SourceIndexDatabase) : SourceIndexStore {
    private val dao = database.index()
    override fun observe(vault: String) = combine(dao.observeSources(vault), dao.observeScan(vault), ::SourceIndexSnapshot)
    override suspend fun sources(vault: String) = dao.sources(vault)
    override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) = database.withTransaction {
        dao.putSources(sources)
        if (scan != null) dao.putScan(scan)
    }
}
