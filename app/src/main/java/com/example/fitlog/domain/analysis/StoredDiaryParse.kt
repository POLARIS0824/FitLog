package com.example.fitlog.domain.analysis

import java.io.IOException
import com.example.fitlog.model.analysis.DiaryParseResult

class DiaryAnalysisStorageException(cause: Exception) : IOException("Diary analysis persistence failed", cause)
data class StoredDiaryParse(val parseRunId: String, val result: DiaryParseResult)
