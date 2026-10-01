package com.example.fitlog.data.analysis

import com.example.fitlog.data.hash.ContentTextSnapshot

/** Business code consumes typed outcomes; only Success is eligible for candidate reuse. */
fun interface DiaryParser {
    suspend fun parse(input: DiaryParseInput): DiaryParseResult
}

/** One whole-file snapshot. Text, evidence offsets and digest share one normalization pass. */
class DiaryParseInput private constructor(
    val parseKey: DiaryParseKey,
    val text: String,
) {
    companion object {
        const val SEGMENT_ID = "diary"

        fun fromSnapshot(
            sourceKey: SourceKey,
            rawText: String,
            extractorVersion: String,
        ): DiaryParseInput {
            val snapshot = ContentTextSnapshot.fromRawText(rawText)
            return DiaryParseInput(
                DiaryParseKey(sourceKey, snapshot.hash, snapshot.hashVersion, extractorVersion),
                snapshot.text,
            )
        }
    }
}
