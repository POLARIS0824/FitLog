package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.CandidateOrigin
import com.example.fitlog.data.analysis.CandidateValue
import com.example.fitlog.data.analysis.DiaryAnalysis
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.EvidenceQuote
import com.example.fitlog.data.analysis.ExpandedSet
import com.example.fitlog.data.analysis.LocatedEvidence
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
 * 检查候选结果是否和原输入、位置证据、重量次数等规则一致，并产生业务可接受的 DiaryAnalysis 与 issues
 */
internal class DiaryCandidateValidator {
    fun validate(input: DiaryParseInput, decoded: DecodedDiary): DiaryAnalysis {
        val issues = decoded.issues.toMutableList()
        val sessions = decoded.sessions.mapIndexedNotNull { sessionIndex, session ->
            if (session == null) return@mapIndexedNotNull null
            val path = "$.sessions[$sessionIndex]"
            val date = session.header.date?.let {
                try {
                    if (!it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
                        throw DateTimeParseException("Invalid ISO date shape", it, 0)
                    }
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    issues.error("$path.date", ValidationCode.INVALID_DATE)
                    null
                }
            }
            if (session.header.date == null) issues.review("$path.date", ValidationCode.MISSING_DATE)
            val exercises = session.exercises.mapIndexedNotNull { exerciseIndex, exercise ->
                exercise?.let { validateExercise(input, it, "$path.exercises[$exerciseIndex]", issues) }
            }
            ValidatedSession(path, date, session.header.notes, exercises)
        }
        if (sessions.mapNotNull { it.date }.distinct().size > 1) {
            sessions.filter { it.date != null }.forEach {
                issues.review("${it.path}.date", ValidationCode.DATE_CONFLICT)
            }
        }
        return DiaryAnalysis(input.parseKey, sessions, decoded.modelIssues, issues.toList())
    }

    private fun validateExercise(
        input: DiaryParseInput,
        decoded: DecodedExercise,
        path: String,
        issues: MutableList<ValidationIssue>,
    ): ValidatedExercise? {
        val candidate = decoded.header.copy(groups = decoded.groups.filterNotNull())
        if (candidate.rawName.isBlank()) {
            issues.error("$path.rawName", ValidationCode.EMPTY_NAME)
            return null
        }
        val evidence = locate(input, candidate.evidence, "$path.evidence", issues) ?: return null
        val sets = mutableListOf<ExpandedSet>()
        var carried: CarriedWeight? = null
        var evidenceCursor = 0
        if (decoded.groups.isEmpty()) issues.review("$path.groups", ValidationCode.MISSING_COUNT)
        decoded.groups.forEachIndexed { groupIndex, group ->
            val groupPath = "$path.groups[$groupIndex]"
            if (group == null) {
                // A rejected block might have changed the weight. Do not guess past it.
                carried = null
                return@forEachIndexed
            }
            val groupText = normalizeLineEndings(group.rawText)
            val rawOffset = if (groupText.isBlank()) -1 else
                evidence.quote.indexOf(groupText, evidenceCursor)
            if (rawOffset < 0) {
                issues.error("$groupPath.rawText", ValidationCode.GROUP_TEXT_NOT_FOUND)
                carried = null
                return@forEachIndexed
            }
            evidenceCursor = rawOffset + groupText.length
            if (!validGroup(group, groupPath, issues)) {
                carried = null
                return@forEachIndexed
            }
            group.inferredFields.forEach {
                if (it !in knownFields) issues.review("$groupPath.inferredFields", ValidationCode.UNKNOWN_VALUE)
            }
            if (group.weight != null) {
                carried = CarriedWeight(
                    groupIndex,
                    field(group.weight, group, "weight"),
                    field(group.unit.takeUnless { it == WeightUnit.UNKNOWN }, group, "unit"),
                    field(group.basis.takeUnless { it == WeightBasis.UNKNOWN }, group, "basis"),
                )
            }
            val weight: CandidateValue<Double> = when {
                group.weight != null -> carried!!.weight
                carried != null -> carried.weight.inherit(carried.groupIndex)
                else -> CandidateValue(null, CandidateOrigin.MISSING)
            }
            val unit: CandidateValue<WeightUnit> = when {
                group.weight != null -> carried!!.unit
                carried != null -> carried.unit.inherit(carried.groupIndex)
                else -> CandidateValue(null, CandidateOrigin.MISSING)
            }
            val basis: CandidateValue<WeightBasis> = when {
                group.basis != WeightBasis.UNKNOWN -> field(group.basis, group, "basis")
                group.weight == null && carried != null -> carried.basis.inherit(carried.groupIndex)
                else -> carried?.basis ?: CandidateValue(null, CandidateOrigin.MISSING)
            }
            if (weight.value == null) issues.review("$groupPath.weight", ValidationCode.MISSING_WEIGHT)
            if (weight.value != null && unit.value == null) issues.review("$groupPath.unit", ValidationCode.MISSING_UNIT)
            if (basis.value == null) issues.review("$groupPath.basis", ValidationCode.MISSING_BASIS)
            listOf("weight" to weight.inferred, "unit" to unit.inferred, "basis" to basis.inferred)
                .filter { it.second }.forEach { issues.review("$groupPath.${it.first}", ValidationCode.INFERRED_VALUE) }
            if ("count" in group.inferredFields) issues.review("$groupPath.count", ValidationCode.INFERRED_VALUE)
            if ("reps" in group.inferredFields) issues.review("$groupPath.reps", ValidationCode.INFERRED_VALUE)
            val repetitions: List<Int?> = when {
                group.repsList != null -> group.repsList
                group.count != null -> {
                    if (group.reps == null) issues.review("$groupPath.reps", ValidationCode.MISSING_REPS)
                    List(group.count) { group.reps }
                }
                else -> {
                    issues.review("$groupPath.count", ValidationCode.MISSING_COUNT)
                    emptyList()
                }
            }
            if (sets.size + repetitions.size > MAX_SETS_PER_EXERCISE) {
                issues.error(groupPath, ValidationCode.TOO_MANY_SETS)
                carried = null
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
        if (group.weight == null && group.unit != WeightUnit.UNKNOWN) {
            reject("unit", ValidationCode.INVALID_WEIGHT)
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

    private fun locate(
        input: DiaryParseInput,
        evidence: EvidenceQuote,
        path: String,
        issues: MutableList<ValidationIssue>,
    ): LocatedEvidence? {
        if (evidence.segmentId != DiaryParseInput.SEGMENT_ID) {
            issues.error("$path.segmentId", ValidationCode.UNKNOWN_SEGMENT)
            return null
        }
        val quote = normalizeLineEndings(evidence.quote)
        val start = if (quote.isBlank()) -1 else input.text.indexOf(quote)
        if (start < 0) {
            issues.error("$path.quote", ValidationCode.EVIDENCE_NOT_FOUND)
            return null
        }
        if (input.text.indexOf(quote, start + 1) >= 0) {
            issues.review("$path.quote", ValidationCode.AMBIGUOUS_EVIDENCE)
            return LocatedEvidence(evidence.segmentId, quote, null, null)
        }
        return LocatedEvidence(evidence.segmentId, quote, start, start + quote.length)
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

    private fun <T> CandidateValue<T>.inherit(groupIndex: Int): CandidateValue<T> =
        if (value == null) this else copy(origin = CandidateOrigin.INHERITED, inheritedFromGroup = groupIndex)

    private data class CarriedWeight(
        val groupIndex: Int,
        val weight: CandidateValue<Double>,
        val unit: CandidateValue<WeightUnit>,
        val basis: CandidateValue<WeightBasis>,
    )

    private companion object {
        const val MAX_SETS_PER_EXERCISE = 1_000
        val knownFields = setOf("weight", "unit", "basis", "reps", "count")
    }
}
