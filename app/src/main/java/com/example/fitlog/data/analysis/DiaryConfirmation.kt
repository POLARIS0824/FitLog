package com.example.fitlog.data.analysis

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlinx.serialization.Serializable

/** Final user-reviewed values; detailed extraction provenance stays in the candidate. */
@Serializable
data class ReviewedSet(
    val weight: Double? = null,
    val unit: WeightUnit? = null,
    val basis: WeightBasis? = null,
    val reps: Int? = null,
    val groupIndex: Int? = null,
    val setInGroup: Int? = null,
    val userEdited: Boolean = false,
)

@Serializable
data class ReviewedExercise(
    val rawName: String,
    val sets: List<ReviewedSet> = emptyList(),
    val notes: String? = null,
    val evidence: EvidenceQuote? = null,
    val sourcePath: String? = null,
)

@Serializable
data class ReviewedSession(
    val exercises: List<ReviewedExercise> = emptyList(),
    val notes: String? = null,
    val sourcePath: String? = null,
)

data class DiaryConfirmation(
    val sourceKey: SourceKey,
    val parseRunId: String,
    val date: LocalDate,
    val sessions: List<ReviewedSession>,
    val acceptedPartialResult: Boolean = false,
    val expectedConfirmedAt: Long? = null,
    val requireLatestCandidate: Boolean = false,
) {
    companion object {
        fun fromCandidate(
            parseRunId: String,
            analysis: DiaryAnalysis,
            date: LocalDate,
            acceptedPartialResult: Boolean = false,
        ) = DiaryConfirmation(
            analysis.parseKey.sourceKey, parseRunId, date,
            analysis.sessions.map { session ->
                ReviewedSession(session.exercises.map { exercise ->
                    ReviewedExercise(
                        exercise.candidate.rawName,
                        exercise.sets.map { set ->
                            ReviewedSet(set.weight.value, set.unit.value, set.basis.value,
                                set.reps.value, set.groupIndex, set.setInGroup)
                        },
                        exercise.candidate.notes,
                        exercise.evidence.takeIf { it.segmentId == DiaryParseInput.SEGMENT_ID && it.quote.isNotBlank() },
                        exercise.path,
                    )
                }, session.notes, session.path)
            }, acceptedPartialResult,
        )
    }
}

sealed interface DiaryConfirmationResult {
    data class Confirmed(val diary: ConfirmedDiaryRecord) : DiaryConfirmationResult
    data class Invalid(val reason: ConfirmationFailure) : DiaryConfirmationResult
}

enum class ConfirmationFailure {
    INVALID_PARSE_RUN, SOURCE_MISMATCH, INVALID_DATA, PARTIAL_RESULT_NOT_ACCEPTED,
    SOURCE_CHANGED, SOURCE_UNAVAILABLE, CONFIRMATION_CHANGED, CANDIDATE_CHANGED,
}

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

data class DiaryContentVersion(val contentHash: String, val hashVersion: Int) {
    init {
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
        require(hashVersion > 0)
    }
    companion object {
        fun from(input: DiaryParseInput) = DiaryContentVersion(input.parseKey.contentHash, input.parseKey.hashVersion)
    }
}

enum class ConfirmationFreshness { UNCONFIRMED, CONFIRMED, NEEDS_UPDATE, UNVERIFIABLE }

/** Caller must supply a normalized content version, never an index byte fingerprint. */
fun confirmationFreshness(confirmed: ConfirmedDiaryRow?, current: DiaryContentVersion?): ConfirmationFreshness = when {
    confirmed == null -> ConfirmationFreshness.UNCONFIRMED
    current == null || current.hashVersion != confirmed.hashVersion -> ConfirmationFreshness.UNVERIFIABLE
    current.contentHash == confirmed.contentHash -> ConfirmationFreshness.CONFIRMED
    else -> ConfirmationFreshness.NEEDS_UPDATE
}
