package com.example.fitlog.data.analysis

/** App-owned vault identity and exact relative path; never a SAF URI or normalized file name. */
data class SourceKey(val vaultId: String, val relPath: String) {
    init {
        require(vaultId.isNotBlank())
        require(relPath.isNotBlank())
        require(!relPath.startsWith('/') && '\\' !in relPath)
        require(relPath.split('/').none { it.isEmpty() || it == "." || it == ".." })
    }
}
