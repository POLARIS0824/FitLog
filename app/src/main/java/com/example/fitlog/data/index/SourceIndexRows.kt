package com.example.fitlog.data.index

import androidx.room.*
import com.example.fitlog.model.source.requireVaultId

@Entity(tableName = "sources", primaryKeys = ["vaultId", "uri"])
internal data class IndexedSourceRow(
    val vaultId: String,
    val uri: String,
    val name: String,
    val path: String,
    val directory: String?,
    val writable: Boolean,
    val status: String = AVAILABLE,
    val contentHash: String? = null,
    val hashVersion: Int? = null,
    /** Logical order of the indexed observation; older or repeated reads cannot replace it. */
    val observedAt: Long = 0L,
) {
    init { requireVaultId(vaultId) }
    companion object {
        const val AVAILABLE = "available"
        const val READ_FAILED = "read_failed"
        const val MISSING = "missing"
    }
}


@Entity(tableName = "scans")
internal data class IndexedScanRow(@PrimaryKey val vaultId: String, val status: String, val completedAt: Long? = null) {
    init { requireVaultId(vaultId) }
    companion object {
        const val COMPLETE = "complete"
        const val PARTIAL = "partial"
        const val FAILED = "failed"
    }
}
