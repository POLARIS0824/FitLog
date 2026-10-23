package com.example.fitlog.data.analysis

import androidx.room.*
import com.example.fitlog.model.analysis.DiaryParseKey
import com.example.fitlog.model.analysis.ParseRunStatus
import com.example.fitlog.model.source.SourceKey

@Entity(tableName = "parse_run", indices = [Index(value = ["vaultId", "relPath", "contentHash", "hashVersion", "extractorVersion", "status"])])

internal data class ParseRunRow(
    @PrimaryKey val id: String,
    val vaultId: String,
    val relPath: String,
    val contentHash: String,
    val hashVersion: Int,
    val extractorVersion: String,
    val status: ParseRunStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val failureCode: String? = null,
    val rawModelJson: String? = null,
    val candidateJson: String? = null,
) {
    fun parseKey() = DiaryParseKey(SourceKey(vaultId, relPath), contentHash, hashVersion, extractorVersion)
}
