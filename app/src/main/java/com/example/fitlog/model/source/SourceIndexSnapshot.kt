package com.example.fitlog.model.source



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

data class IndexedScan(val vaultId: String, val status: String, val completedAt: Long? = null) {
    init { requireVaultId(vaultId) }
    companion object {
        const val COMPLETE = "complete"
        const val PARTIAL = "partial"
        const val FAILED = "failed"
    }
}

data class SourceIndexSnapshot(val sources: List<IndexedSource>, val scan: IndexedScan?,
    val refreshing: Boolean = false, val refreshFailed: Boolean = false)
