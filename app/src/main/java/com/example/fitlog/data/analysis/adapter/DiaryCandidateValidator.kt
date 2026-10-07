package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.CandidateOrigin
import com.example.fitlog.data.analysis.CandidateValue
import com.example.fitlog.data.analysis.DiaryAnalysis
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.EvidenceQuote
import com.example.fitlog.data.analysis.ExpandedSet
import com.example.fitlog.data.analysis.DiaryCandidate
import com.example.fitlog.data.analysis.ExerciseCandidate
import com.example.fitlog.data.analysis.SetGroupCandidate
import com.example.fitlog.data.analysis.ValidatedExercise
import com.example.fitlog.data.analysis.ValidatedSession
import com.example.fitlog.data.analysis.ValidationCode
import com.example.fitlog.data.analysis.ValidationIssue
import com.example.fitlog.data.analysis.WeightBasis
import com.example.fitlog.data.analysis.WeightUnit
import com.example.fitlog.data.hash.normalizeLineEndings

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Validates candidates only. It never saves, confirms, or reads a different source snapshot.
 *
 * Checks basic data validity and expands model-provided groups, without interpreting training text.
 */
internal class DiaryCandidateValidator {
    fun validate(input: DiaryParseInput, decoded: DiaryCandidate): DiaryAnalysis {
        val issues = mutableListOf<ValidationIssue>()
        val sessions = decoded.sessions.mapIndexed { sessionIndex, session ->
            val path = "$.sessions[$sessionIndex]"
            val date = session.date?.let {
                try {
                    if (!it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
                        throw DateTimeParseException("Invalid ISO date shape", it, 0)
                    }
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    issues.review("$path.date", ValidationCode.INVALID_DATE)
                    null
                }
            }
            val exercises = session.exercises.mapIndexedNotNull { exerciseIndex, exercise ->
                validateExercise(input, exercise, "$path.exercises[$exerciseIndex]", issues)
            }
            ValidatedSession(path, date, session.notes, exercises)
        }
        return DiaryAnalysis(input.parseKey, sessions, decoded.issues, issues.toList())
    }

    private fun validateExercise(
        input: DiaryParseInput,
        candidate: ExerciseCandidate,
        path: String,
        issues: MutableList<ValidationIssue>,
    ): ValidatedExercise? {
        if (candidate.rawName.isBlank()) {
            issues.error("$path.rawName", ValidationCode.EMPTY_NAME)
            return null
        }
        val evidence = checkExcerpt(input, candidate.evidence, "$path.evidence", issues)
        val sets = mutableListOf<ExpandedSet>()
        candidate.groups.forEachIndexed { groupIndex, group ->
            val groupPath = "$path.groups[$groupIndex]"
            if (!validGroup(group, groupPath, issues)) return@forEachIndexed
            val weight = field(group.weight, group, "weight")
            val unit = field(group.unit.takeUnless { it == WeightUnit.UNKNOWN }, group, "unit")
            val basis = field(group.basis.takeUnless { it == WeightBasis.UNKNOWN }, group, "basis")
            val repetitions: List<Int?> = when {
                group.repsList != null -> group.repsList
                group.count != null -> List(group.count) { group.reps }
                else -> emptyList()
            }
            if (sets.size + repetitions.size > MAX_SETS_PER_EXERCISE) {
                issues.error(groupPath, ValidationCode.TOO_MANY_SETS)
                return@forEachIndexed
            }
            val countOrigin = if ("count" in group.inferredFields ||
                (group.count == null && "reps" in group.inferredFields)) CandidateOrigin.INFERRED
                else CandidateOrigin.EXPLICIT
            repetitions.forEachIndexed { setIndex, reps ->
                val kg = when (unit.value) {
                    WeightUnit.KG -> weight.value
                    WeightUnit.LB -> weight.value?.times(0.45359237)
                    else -> null
                }
                sets += ExpandedSet(
                    groupIndex, setIndex, weight, unit, basis, field(reps, group, "reps"), countOrigin, kg,
                )
            }
        }
        return ValidatedExercise(path, candidate, evidence, sets.toList())
    }

    private fun validGroup(
        group: SetGroupCandidate,
        path: String,
        issues: MutableList<ValidationIssue>,
    ): Boolean {
        var valid = true
        fun reject(field: String, code: ValidationCode) {
            issues.error("$path.$field", code)
            valid = false
        }
        if (group.weight != null && (!group.weight.isFinite() || group.weight < 0)) {
            reject("weight", ValidationCode.INVALID_WEIGHT)
        }
        if (group.count != null && group.count <= 0) reject("count", ValidationCode.INVALID_COUNT)
        if (group.count != null && group.count > MAX_SETS_PER_EXERCISE) {
            reject("count", ValidationCode.TOO_MANY_SETS)
        }
        if (group.reps != null && group.reps <= 0) reject("reps", ValidationCode.INVALID_REPS)
        group.repsList?.let {
            if (it.isEmpty() || it.any { reps -> reps <= 0 }) reject("repsList", ValidationCode.INVALID_REPS)
            if (it.size > MAX_SETS_PER_EXERCISE) reject("repsList", ValidationCode.TOO_MANY_SETS)
            if (group.reps != null || (group.count != null && group.count != it.size)) {
                reject("repsList", ValidationCode.INCONSISTENT_REPS)
            }
        }
        return valid
    }

    private fun checkExcerpt(
        input: DiaryParseInput,
        evidence: EvidenceQuote,
        path: String,
        issues: MutableList<ValidationIssue>,
    ): EvidenceQuote {
        val normalized = evidence.copy(quote = normalizeLineEndings(evidence.quote))
        if (normalized.quote.isBlank()) return normalized
        if (evidence.segmentId != DiaryParseInput.SEGMENT_ID) {
            issues.review("$path.segmentId", ValidationCode.UNKNOWN_SEGMENT)
            return normalized
        }
        val quote = normalized.quote
        val start = input.text.indexOf(quote)
        if (start < 0) {
            issues.review("$path.quote", ValidationCode.EVIDENCE_NOT_FOUND)
        } else if (input.text.indexOf(quote, start + 1) >= 0) {
            issues.review("$path.quote", ValidationCode.AMBIGUOUS_EVIDENCE)
        }
        return normalized
    }

    private fun <T> field(value: T?, group: SetGroupCandidate, name: String): CandidateValue<T> {
        val inferred = value != null && name in group.inferredFields
        val origin = when {
            value == null -> CandidateOrigin.MISSING
            inferred -> CandidateOrigin.INFERRED
            else -> CandidateOrigin.EXPLICIT
        }
        return CandidateValue(value, origin, inferred = inferred)
    }

    companion object {
        const val VERSION = "diary-basic-v2"
        const val MAX_SETS_PER_EXERCISE = 1_000
    }
}
