package com.example.fitlog.data.analysis

import com.example.fitlog.data.vault.requireVaultId
import kotlinx.serialization.Serializable

/** App-owned vault identity and exact relative path; never a SAF URI or normalized file name. */
@Serializable
data class SourceKey(val vaultId: String, val relPath: String) {
    init {
        requireVaultId(vaultId)
        require(relPath.isNotBlank())
        require(!relPath.startsWith('/') && '\\' !in relPath)
        require(relPath.split('/').none { it.isEmpty() || it == "." || it == ".." })
    }
}
