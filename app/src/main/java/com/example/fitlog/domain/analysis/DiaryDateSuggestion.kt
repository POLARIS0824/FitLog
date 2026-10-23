package com.example.fitlog.domain.analysis

import com.example.fitlog.model.analysis.DiaryAnalysis
import com.example.fitlog.model.analysis.ValidationCode
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Suggestions require an explicit final date in the confirmation request. */
data class DiaryDateSuggestion(val suggested: LocalDate?, val modelDates: List<LocalDate>, val requiresReview: Boolean)

fun suggestDiaryDate(sourceKey: SourceKey, analysis: DiaryAnalysis): DiaryDateSuggestion {
    require(sourceKey == analysis.parseKey.sourceKey)
    val fileDate = diaryDateFromFileName(sourceKey.relPath.substringAfterLast('/'))
    val dates = analysis.sessions.mapNotNull { it.date }.distinct()
    return DiaryDateSuggestion(
        fileDate ?: dates.singleOrNull(), dates,
        dates.size > 1 || (fileDate != null && dates.any { it != fileDate }) ||
            (fileDate == null && dates.size != 1) || analysis.issues.any { it.code == ValidationCode.INVALID_DATE },
    )
}

fun diaryDateFromFileName(fileName: String): LocalDate? {
    val suffix = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (suffix !in setOf("md", "markdown")) return null
    val stem = fileName.substringBeforeLast('.')
    val pattern = when {
        stem.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) -> "uuuu-MM-dd"
        stem.matches(Regex("[0-9]{8}")) -> "uuuuMMdd"
        stem.matches(Regex("[0-9]{4}年[0-9]{2}月[0-9]{2}日")) -> "uuuu年MM月dd日"
        else -> return null
    }
    return try {
        LocalDate.parse(stem, DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT))
    } catch (_: java.time.format.DateTimeParseException) { null }
}
