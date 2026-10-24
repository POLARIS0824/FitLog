package com.example.fitlog.data.analysis

import com.example.fitlog.model.analysis.AnalysisSource

internal interface DiaryAnalysisSession {
    suspend fun read(source: AnalysisSource): DiaryParseInput
    suspend fun execute(input: DiaryParseInput): StoredDiaryParse
}
