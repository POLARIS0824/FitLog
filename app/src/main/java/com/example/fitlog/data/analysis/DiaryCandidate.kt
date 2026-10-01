package com.example.fitlog.data.analysis

import kotlinx.serialization.Serializable

const val DIARY_SCHEMA_VERSION = 1

/** All values are candidates, even when a model claims that they are explicit. */
@Serializable
data class DiaryCandidate(
    val schemaVersion: Int,
    val sessions: List<SessionCandidate>,
    val issues: List<CandidateIssue> = emptyList(),
)

@Serializable
data class SessionCandidate(
    /** ISO local date or null. Never substitute today's date. */
    val date: String? = null,
    val notes: String? = null,
    val exercises: List<ExerciseCandidate>,
)

@Serializable
data class ExerciseCandidate(
    val rawName: String,
    val evidence: EvidenceQuote,
    val groups: List<SetGroupCandidate> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class EvidenceQuote(val segmentId: String, val quote: String)

@Serializable
data class SetGroupCandidate(
    /** Exact substring of the exercise evidence, e.g. "40kg 2×7" or "1×4". */
    val rawText: String,
    val weight: Double? = null,
    val unit: WeightUnit = WeightUnit.UNKNOWN,
    val basis: WeightBasis = WeightBasis.UNKNOWN,
    val reps: Int? = null,
    val count: Int? = null,
    val repsList: List<Int>? = null,
    /** Open field names: weight, unit, basis, reps, count. Unknown names remain visible. */
    val inferredFields: List<String> = emptyList(),
)

@Serializable
enum class WeightUnit { UNKNOWN, KG, LB }

@Serializable
enum class WeightBasis { UNKNOWN, PER_SIDE, TOTAL, BODYWEIGHT, ADDED, ASSISTED }

@Serializable
data class CandidateIssue(val path: String, val question: String)

enum class IssueSeverity { REVIEW, ERROR }

enum class ValidationCode {
    INVALID_FIELD,
    UNKNOWN_SEGMENT, EVIDENCE_NOT_FOUND, AMBIGUOUS_EVIDENCE, EMPTY_NAME,
    GROUP_TEXT_NOT_FOUND, INVALID_WEIGHT, INVALID_COUNT, INVALID_REPS,
    INCONSISTENT_REPS, TOO_MANY_SETS, INVALID_DATE, DATE_CONFLICT,
    MISSING_DATE, MISSING_WEIGHT, MISSING_UNIT, MISSING_BASIS, MISSING_REPS, MISSING_COUNT,
    INFERRED_VALUE, UNKNOWN_VALUE,
}

/** UI can translate codes using resources; diagnostics never contain fixed UI wording. */
data class ValidationIssue(val path: String, val code: ValidationCode, val severity: IssueSeverity)

enum class CandidateOrigin { EXPLICIT, INHERITED, INFERRED, MISSING }

data class CandidateValue<T>(
    val value: T?,
    val origin: CandidateOrigin,
    val inheritedFromGroup: Int? = null,
    /** Also retained when an inferred value is inherited. */
    val inferred: Boolean = false,
)

/** UTF-16 range in normalized input, start inclusive/end exclusive; null when ambiguous. */
data class LocatedEvidence(
    val segmentId: String,
    val quote: String,
    val normalizedStart: Int?,
    val normalizedEndExclusive: Int?,
)

data class ExpandedSet(
    val groupIndex: Int,
    val setInGroup: Int,
    val weight: CandidateValue<Double>,
    val unit: CandidateValue<WeightUnit>,
    val basis: CandidateValue<WeightBasis>,
    val reps: CandidateValue<Int>,
    val countOrigin: CandidateOrigin,
    /** Unit conversion only, without doubling per-side weights or inventing body weight. */
    val weightKg: Double?,
)

data class ValidatedExercise(
    val path: String,
    val candidate: ExerciseCandidate,
    val evidence: LocatedEvidence,
    val sets: List<ExpandedSet>,
)

data class ValidatedSession(
    val path: String,
    /** Model-reported candidate only; authoritative diary date is resolved separately. */
    val date: java.time.LocalDate?,
    val notes: String?,
    val exercises: List<ValidatedExercise>,
)

data class DiaryAnalysis(
    val parseKey: DiaryParseKey,
    val sessions: List<ValidatedSession>,
    val modelIssues: List<CandidateIssue>,
    val issues: List<ValidationIssue>,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.ERROR }
}
