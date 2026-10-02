package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.DiaryParseResult

/** Raw responses are available to persistence, never attached to business analysis values. */
internal data class DiaryParseExecution(val result: DiaryParseResult, val rawModelJson: String? = null)

internal fun interface RecordingDiaryParser {
    suspend fun execute(input: DiaryParseInput): DiaryParseExecution
}
