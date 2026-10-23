package com.example.fitlog.data.index

import android.content.Context
import androidx.room.*
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.model.source.SourceIndexSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface SourceIndexStore {
    fun observe(vaultId: String): Flow<SourceIndexSnapshot>
    suspend fun sources(vaultId: String): List<IndexedSource>
    suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan? = null)
}

@Dao
internal interface SourceIndexDao {
    @Query("SELECT * FROM sources WHERE vaultId = :vaultId")
    fun observeSources(vaultId: String): Flow<List<IndexedSourceRow>>
    @Query("SELECT * FROM scans WHERE vaultId = :vaultId")
    fun observeScan(vaultId: String): Flow<IndexedScanRow?>
    @Query("SELECT * FROM sources WHERE vaultId = :vaultId")
    suspend fun sources(vaultId: String): List<IndexedSourceRow>
    @Upsert suspend fun putSources(sources: List<IndexedSourceRow>)
    @Upsert suspend fun putScan(scan: IndexedScanRow)
}

@Database(entities = [IndexedSourceRow::class, IndexedScanRow::class], version = 6, exportSchema = true)
internal abstract class SourceIndexDatabase : RoomDatabase() {
    abstract fun index(): SourceIndexDao
    companion object {
        internal fun open(context: Context, name: String = "source-index.db"): SourceIndexDatabase =
            Room.databaseBuilder(context.applicationContext, SourceIndexDatabase::class.java, name)
                .fallbackToDestructiveMigration(dropAllTables = true).build()

    }
}

class RoomSourceIndexStore internal constructor(private val database: SourceIndexDatabase) : SourceIndexStore {
    private val dao = database.index()
    override fun observe(vaultId: String) = combine(dao.observeSources(vaultId), dao.observeScan(vaultId)) { sources, scan ->
        SourceIndexSnapshot(sources.map { it.toSource() }, scan?.toScan())
    }
    override suspend fun sources(vaultId: String) = dao.sources(vaultId).map { it.toSource() }
    override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) = database.withTransaction {
        dao.putSources(sources.map { it.toRow() })
        if (scan != null) dao.putScan(scan.toRow())
    }
}
