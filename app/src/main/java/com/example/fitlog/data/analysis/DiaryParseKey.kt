package com.example.fitlog.data.analysis

/** Whole-file candidate reuse identity. Only successful extraction may be reused under this key. */
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
