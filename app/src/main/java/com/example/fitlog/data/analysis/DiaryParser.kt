package com.example.fitlog.data.analysis

import com.example.fitlog.data.hash.ContentTextSnapshot
import com.example.fitlog.data.vault.MarkdownSnapshot

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

        /** The document reader has already removed the file BOM; restore it before normalization. */
        fun fromSnapshot(
            sourceKey: SourceKey,
            snapshot: MarkdownSnapshot,
            extractorVersion: String,
        ): DiaryParseInput = fromSnapshot(
            sourceKey,
            (if (snapshot.bom) "\uFEFF" else "") + snapshot.text,
            extractorVersion,
        )

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
