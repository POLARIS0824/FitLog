package com.example.fitlog.data.analysis



internal interface DiaryAnalysisSession {
    suspend fun read(source: AnalysisSource): DiaryParseInput
    suspend fun execute(input: DiaryParseInput): StoredDiaryParse
}
