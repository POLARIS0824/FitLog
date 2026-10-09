package com.example.fitlog.diary

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import java.text.NumberFormat
import kotlin.math.floor
import kotlin.math.log10

internal data class DiarySetUi(val number: Int?, val weight: Double?, val unit: WeightUnit?, val basis: WeightBasis?,
    val reps: Int?, val address: DiarySetAddress?, val source: ExpandedSet? = null, val userEdited: Boolean = false,
    val assumedKg: Boolean = false)

@Composable
internal fun weightNumberLabel(weight: Double?, maximumFractionDigits: Int = 340): String {
    val locale = LocalConfiguration.current.locales[0]
    val digits = maxOf(maximumFractionDigits,
        if (weight != null && weight > 0 && weight < 1) (2 - floor(log10(weight)).toInt()).coerceAtMost(340) else 0)
    val numbers = remember(locale, digits) { NumberFormat.getNumberInstance(locale).apply {
        this.maximumFractionDigits = digits; isGroupingUsed = false
    } }
    return if (weight == null) stringResource(R.string.detail_value_unknown) else numbers.format(weight)
}

@Composable
internal fun weightLabel(weight: Double?, unit: WeightUnit?, maximumFractionDigits: Int = 340, basis: WeightBasis? = null): String {
    return if (basis == WeightBasis.BODYWEIGHT) stringResource(R.string.detail_weight_bodyweight)
    else if (weight == null) stringResource(R.string.detail_weight_unrecorded)
    else stringResource(R.string.detail_weight_short, weightNumberLabel(weight, maximumFractionDigits), stringResource(when (unit) {
        WeightUnit.KG -> R.string.detail_unit_kg_short
        WeightUnit.LB -> R.string.detail_unit_lb_short
        else -> R.string.detail_unit_unknown_short
    }))
}

/** Keep the card at one composition call site across candidate, pending and confirmed views. */
private fun LazyListScope.exerciseCardItem(key: String, name: String, notes: String?, evidence: EvidenceQuote?,
    issues: List<ValidationIssue>, canEdit: Boolean, onEdit: (DiarySetEdit) -> Unit, sets: () -> List<DiarySetUi>) {
    item(key = key) { DiaryExerciseCard(name, notes, evidence, sets(), issues, canEdit, onEdit) }
}

@Composable
private fun DiaryExerciseCard(name: String, notes: String?, evidence: EvidenceQuote?, sets: List<DiarySetUi>,
    issues: List<ValidationIssue>, canEdit: Boolean, onEdit: (DiarySetEdit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val knownSets = sets.count { it.number != null }
    val commonBasis = sets.map { it.basis ?: WeightBasis.UNKNOWN }.distinct().singleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.semantics { heading() })
        Text(stringResource(if (sets.any { it.number == null }) R.string.detail_set_summary_partial else R.string.detail_set_summary,
            knownSets, stringResource(if (commonBasis == null && sets.isNotEmpty()) R.string.detail_basis_varies else basisLabel(commonBasis))), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (sets.isEmpty()) Text(stringResource(R.string.detail_no_sets), Modifier.padding(vertical = 16.dp))
                sets.forEachIndexed { index, set ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiarySetRow(set, commonBasis, canEdit, onEdit)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val turn by animateFloatAsState(if (expanded) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sourceExpand")
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (issues.isEmpty()) stringResource(R.string.detail_sources)
                        else stringResource(R.string.detail_sources_issues, issues.size), modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.chevron_right_24px), null, modifier = Modifier.graphicsLayer { rotationZ = turn })
                }
                AnimatedVisibility(expanded,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())) {
                    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        evidence?.quote?.takeIf { it.isNotBlank() }?.let { quote ->
                            Text(stringResource(R.string.detail_model_excerpt), style = MaterialTheme.typography.labelLarge)
                            SelectionContainer { Text(quote, style = MaterialTheme.typography.bodyMedium) }
                        }
                        issues.forEach { issue -> Text(stringResource(validationLabel(issue.code)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (issue.severity == IssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        sets.forEach { set ->
                            set.source?.let { source ->
                                Text(stringResource(R.string.detail_set_provenance, set.number ?: 0,
                                    stringResource(originLabel(source.weight.origin)), stringResource(originLabel(source.reps.origin))),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.detail_interpretation_provenance,
                                    stringResource(originLabel(source.unit.origin)), stringResource(originLabel(source.basis.origin)),
                                    stringResource(originLabel(source.countOrigin))), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiarySetRow(set: DiarySetUi, commonBasis: WeightBasis?, canEdit: Boolean, onEdit: (DiarySetEdit) -> Unit) {
    val displayUnit = if (set.assumedKg) WeightUnit.KG else set.unit
    val weight = weightLabel(set.weight, displayUnit, maximumFractionDigits = 3, basis = set.basis)
    val reps = set.reps?.let { stringResource(R.string.detail_reps_short, it) } ?: stringResource(R.string.detail_value_unknown)
    val label = stringResource(if (set.number == null) R.string.detail_count_not_provided else R.string.detail_set, set.number ?: 0)
    val weightDescription = stringResource(R.string.detail_edit_value, label, stringResource(R.string.detail_weight), weightLabel(set.weight, displayUnit, basis = set.basis))
    val repsDescription = stringResource(R.string.detail_edit_value, label, stringResource(R.string.detail_reps), reps)
    val enabled = canEdit && set.address != null
    val controls: @Composable RowScope.() -> Unit = {
        Surface(onClick = { set.address?.let { onEdit(DiarySetEdit(it, requireNotNull(set.number), DiarySetField.WEIGHT, set.weight, set.unit, set.reps, set.basis)) } },
            enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = weightDescription }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Text(weight, style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(onClick = { set.address?.let { onEdit(DiarySetEdit(it, requireNotNull(set.number), DiarySetField.REPS, set.weight, set.unit, set.reps, set.basis)) } },
            enabled = enabled, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = repsDescription }) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Text(reps, style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalDensity.current.fontScale > 1.3f || set.number == null || set.weight == null || set.reps == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                EditedSetLabel(set.userEdited)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = controls)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(.8f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                EditedSetLabel(set.userEdited)
            }
            controls()
        }
        if (commonBasis == null) Text(stringResource(basisLabel(set.basis)), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (set.assumedKg && set.basis != WeightBasis.BODYWEIGHT) Text(stringResource(R.string.detail_weight_assumed_kg),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditedSetLabel(edited: Boolean) {
    val description = stringResource(R.string.detail_user_edited)
    Text(stringResource(R.string.detail_user_edited_short), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.graphicsLayer { alpha = if (edited) 1f else 0f }
            .then(if (edited) Modifier.semantics { contentDescription = description } else Modifier.clearAndSetSemantics {}))
}

private fun LazyListScope.candidateContextItems(candidate: StoredDiaryCandidate, freshness: DiaryResultFreshness?, usedForConfirmation: Boolean) {
    val analysis = candidate.analysis
    item(key = "result-heading:${candidate.attempt.id}") {
        Text(stringResource(R.string.detail_candidate_result), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (freshness != DiaryResultFreshness.CURRENT) Text(stringResource(if (freshness == DiaryResultFreshness.NEEDS_UPDATE)
            R.string.detail_needs_update else R.string.detail_unverifiable), style = MaterialTheme.typography.bodySmall)
        if (usedForConfirmation) Text(stringResource(R.string.detail_candidate_used), style = MaterialTheme.typography.bodySmall)
        if (analysis.sessions.all { it.exercises.isEmpty() }) Text(stringResource(if (analysis.hasErrors)
            R.string.detail_partial_no_sessions else R.string.detail_candidate_no_sessions))
    }
    // Exercise-specific diagnostics are inside its disclosure; global issues remain visible.
    val exercisePaths = analysis.sessions.flatMap { it.exercises }.map { it.path }
    analysis.issues.filter { issue -> exercisePaths.none { issue.path.startsWith("$it.") || issue.path == it } }.forEachIndexed { index, issue ->
        item(key = "global-issue:$index") { Text(stringResource(validationLabel(issue.code)),
            color = if (issue.severity == IssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    analysis.modelIssues.forEachIndexed { index, issue ->
        item(key = "question:$index") { Text(stringResource(R.string.detail_model_question, issue.question)) }
    }
}

internal fun LazyListScope.candidateItems(candidate: StoredDiaryCandidate, freshness: DiaryResultFreshness?, usedForConfirmation: Boolean,
    canEdit: Boolean = false, onEdit: (DiarySetEdit) -> Unit = {}) {
    val analysis = candidate.analysis
    candidateContextItems(candidate, freshness, usedForConfirmation)
    analysis.sessions.forEachIndexed { sessionIndex, session ->
        if (analysis.sessions.size > 1) item(key = "session:${candidate.attempt.id}:$sessionIndex") {
            Text(stringResource(R.string.detail_session, sessionIndex + 1, session.date?.toString() ?: stringResource(R.string.detail_date_unknown)),
                style = MaterialTheme.typography.titleMediumEmphasized)
        }
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            exerciseCardItem("exercise:${candidate.attempt.id}:$sessionIndex:$exerciseIndex",
                exercise.candidate.rawName, exercise.candidate.notes, exercise.evidence,
                analysis.issues.filter { it.path.startsWith(exercise.path) }, canEdit, onEdit) {
                val expanded = exercise.sets.mapIndexed { index, set ->
                    DiarySetUi(index + 1, set.weight.value, set.unit.value, set.basis.value,
                        set.reps.value, DiarySetAddress(sessionIndex, exerciseIndex, index), set,
                        assumedKg = set.weight.value != null && set.unit.value !in listOf(WeightUnit.KG, WeightUnit.LB))
                }
                val unknownCounts = exercise.candidate.groups.withIndex().filter { (index, group) ->
                    group.count == null && group.repsList == null && analysis.issues.none {
                        it.severity == IssueSeverity.ERROR && it.path.startsWith("${exercise.path}.groups[$index]")
                    }
                }.map { (index, group) -> index to DiarySetUi(null, group.weight, group.unit, group.basis, group.reps, null) }
                // Keep fragments with missing counts in source order; never invent individual sets.
                val rows = exercise.candidate.groups.indices.flatMap { groupIndex ->
                    expanded.filter { it.source?.groupIndex == groupIndex } + unknownCounts.filter { it.first == groupIndex }.map { it.second }
                }
                rows
            }
        }
    }
}

/** Drafts render from their own snapshot, even if a newer parse arrives after process restoration. */
internal fun LazyListScope.reviewItems(draft: DiaryReviewDraft, candidate: StoredDiaryCandidate?, canEdit: Boolean, onEdit: (DiarySetEdit) -> Unit,
    freshness: DiaryResultFreshness? = null, usedForConfirmation: Boolean = false) {
    if (draft.fromCandidate && candidate != null) candidateContextItems(candidate, freshness, usedForConfirmation)
    else if (!draft.fromCandidate) confirmedContextItems(draft.parseRunId, draft.partial, draft.sessions.isEmpty())
    else item(key = "result-heading:${draft.parseRunId}") {
        Text(stringResource(if (draft.fromCandidate) R.string.detail_candidate_result else R.string.detail_confirmed_result),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draft.partial) Text(stringResource(R.string.detail_partial_confirmation), style = MaterialTheme.typography.bodySmall)
    }
    draft.sessions.forEachIndexed { sessionIndex, session ->
        if (draft.sessions.size > 1) item(key = "session:${draft.parseRunId}:$sessionIndex") {
            Text(stringResource(R.string.detail_session, sessionIndex + 1, draft.date.ifBlank { stringResource(R.string.detail_date_unknown) }))
        }
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            exerciseCardItem("exercise:${draft.parseRunId}:$sessionIndex:$exerciseIndex", exercise.rawName,
                exercise.notes, exercise.evidence,
                candidate?.analysis?.issues?.filter { it.path.startsWith(exercise.sourcePath.orEmpty()) }.orEmpty(), canEdit, onEdit) {
                val original = candidate?.analysis?.sessions?.flatMap { it.exercises }?.find { it.path == exercise.sourcePath }
                val expanded = exercise.sets.mapIndexed { index, set ->
                    val source = original?.sets?.find { it.groupIndex == set.groupIndex && it.setInGroup == set.setInGroup }
                    DiarySetUi(index + 1, set.weight, set.unit, set.basis, set.reps,
                        DiarySetAddress(sessionIndex, exerciseIndex, index), source, set.userEdited,
                        assumedKg = draft.fromCandidate && set.weight != null && set.unit == WeightUnit.KG && source != null &&
                            source.unit.value !in listOf(WeightUnit.KG, WeightUnit.LB))
                }
                val fragments = draft.fragments.filter { it.session == sessionIndex && it.exercise == exerciseIndex }
                val rows = if (fragments.isEmpty()) expanded else {
                    val groups = (exercise.sets.mapNotNull { it.groupIndex } + fragments.map { it.group }).distinct().sorted()
                    groups.flatMap { group -> expanded.filterIndexed { index, _ -> exercise.sets[index].groupIndex == group } +
                        fragments.filter { it.group == group }.map { DiarySetUi(null, it.weight, it.unit, it.basis, it.reps, null) } }
                }
                rows
            }
        }
    }
}

private fun LazyListScope.confirmedContextItems(parseRunId: String, partial: Boolean, empty: Boolean) {
    item(key = "result-heading:$parseRunId") {
        Text(stringResource(R.string.detail_confirmed_result), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary)
        if (partial) Text(stringResource(R.string.detail_partial_confirmation), style = MaterialTheme.typography.bodySmall)
        if (empty) Text(stringResource(R.string.detail_confirmed_no_sessions))
    }
}

internal fun LazyListScope.confirmedItems(record: ConfirmedDiaryRecord,
    canEdit: Boolean = false, onEdit: (DiarySetEdit) -> Unit = {}) {
    confirmedContextItems(record.diary.parseRunId, record.diary.acceptedPartialResult, record.sessions.isEmpty())
    record.sessions.forEachIndexed { sessionIndex, session ->
        if (record.sessions.size > 1) item(key = "session:${record.diary.parseRunId}:$sessionIndex") {
            Text(stringResource(R.string.detail_session, sessionIndex + 1, record.diary.date), style = MaterialTheme.typography.titleMediumEmphasized)
        }
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            exerciseCardItem("exercise:${record.diary.parseRunId}:$sessionIndex:$exerciseIndex", exercise.exercise.rawName,
                exercise.exercise.notes, exercise.exercise.evidence, emptyList(), canEdit, onEdit) {
                val rows = exercise.sets.mapIndexed { index, set ->
                    DiarySetUi(index + 1, set.weight, set.unit, set.basis, set.reps,
                        DiarySetAddress(sessionIndex, exerciseIndex, index), userEdited = set.userEdited)
                }
                rows
            }
        }
    }
}
