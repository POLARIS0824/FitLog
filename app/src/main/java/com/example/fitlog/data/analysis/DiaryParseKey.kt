package com.example.fitlog.data.analysis

import kotlinx.serialization.Serializable

/**
 * Whole-file candidate reuse identity. Only successful extraction may be reused under this key.
 *
 * 在 SourceKey 基础上加入 contentHash、hashVersion、extractorVersion，只有这些全部相同才代表“同一个内容用同一版解析器处理”
 */
@Serializable
data class DiaryParseKey(
    val sourceKey: SourceKey,
    val contentHash: String,
    val hashVersion: Int,
    val extractorVersion: String,
) {
    init {
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
        require(hashVersion > 0)
        require(extractorVersion.isNotBlank())
    }
}
