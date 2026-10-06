package com.example.fitlog.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(vm: DiaryDetailViewModel, onEdit: () -> Unit, onBack: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    // The app Scaffold already supplies system insets. This page adds only its own spacing.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cd_back)) }
                    TextButton(onClick = onEdit, enabled = vm.canEdit) { Text(stringResource(R.string.detail_edit)) }
                    TextButton(onClick = vm::refresh, enabled = !vm.sourceLoading) { Text(stringResource(R.string.log_refresh)) }
                }
                Text(vm.route.fileName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(vm.route.relPath, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            PrimaryTabRow(selectedTabIndex = vm.tab.ordinal) {
                DiaryDetailTab.entries.forEach { tab ->
                    Tab(selected = vm.tab == tab, onClick = { vm.selectTab(tab) }, text = {
                        Text(stringResource(if (tab == DiaryDetailTab.ORIGINAL) R.string.detail_original else R.string.detail_analysis))
                    })
                }
            }
            if (vm.sourceLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.tab == DiaryDetailTab.ORIGINAL) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (vm.sourceReadFailed) item { ErrorText(R.string.detail_source_failed) }
                    vm.original?.let { snapshot ->
                        if (!snapshot.file.writable) item { Text(stringResource(R.string.detail_read_only)) }
                        item {
                            if (snapshot.text.isEmpty()) Text(stringResource(R.string.detail_original_empty))
                            else SelectionContainer { Text(snapshot.text, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            } else {
                AnalysisContent(vm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnalysisContent(vm: DiaryDetailViewModel, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.sourceReadFailed) ErrorText(R.string.detail_source_failed)
                if (vm.parsesLoading || vm.confirmationLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!vm.parsesLoading && !vm.parsesReadFailed) {
                    Text(stringResource(R.string.detail_parse_state, stringResource(parseStatusLabel(vm.parses.latestAttempt?.status))))
                }
                if (!vm.confirmationLoading && !vm.confirmationReadFailed) {
                    Text(stringResource(R.string.detail_confirmation_state, stringResource(confirmationLabel(vm.confirmationStatus))))
                }
                if (vm.parsesReadFailed || vm.confirmationReadFailed) ErrorText(R.string.detail_analysis_failed)
                if (vm.parses.candidateReadFailed) ErrorText(R.string.detail_candidate_damaged)
                if (vm.parsesReadFailed || vm.confirmationReadFailed || vm.parses.candidateReadFailed) {
                    TextButton(onClick = vm::retryAnalysis, enabled = !vm.parsesLoading && !vm.confirmationLoading) {
                        Text(stringResource(R.string.detail_retry_analysis))
                    }
                }
            }
        }
        vm.parses.latestFailure?.let { failure ->
            item(key = "last-failure") {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.detail_latest_failure), style = MaterialTheme.typography.titleMedium)
                        Text(DateFormat.getDateTimeInstance().format(Date(failure.finishedAt)))
                        Text(stringResource(failureLabel(failure)))
                    }
                }
            }
        }
        vm.confirmed?.let { confirmedItems(it) }
        vm.parses.latestCandidate?.let { candidateItems(it, vm.candidateStatus, vm.confirmed?.diary?.parseRunId == it.attempt.id) }
        if (!vm.parsesLoading && !vm.confirmationLoading && !vm.parsesReadFailed && !vm.confirmationReadFailed &&
            !vm.parses.candidateReadFailed && vm.parses.latestCandidate == null && vm.confirmed == null) {
            item(key = "no-result") { Text(stringResource(R.string.detail_no_saved_result)) }
        }
    }
}

private fun LazyListScope.confirmedItems(record: ConfirmedDiaryRecord) {
    item(key = "confirmed-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_confirmed_result), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.detail_confirmed_date, record.diary.date))
            if (record.diary.acceptedPartialResult) Text(stringResource(R.string.detail_partial_confirmation))
            if (record.sessions.isEmpty()) Text(stringResource(R.string.detail_confirmed_no_sessions))
        }
    }
    record.sessions.forEachIndexed { sessionIndex, session ->
        item(key = "confirmed-session:$sessionIndex") {
            SessionHeading(sessionIndex, record.diary.date, session.session.notes)
        }
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            val prefix = "confirmed:$sessionIndex:$exerciseIndex"
            item(key = prefix) { ExerciseHeading(exercise.exercise.rawName, exercise.exercise.notes, exercise.exercise.evidence?.quote, exercise.sets.isEmpty()) }
            exercise.sets.forEachIndexed { index, set ->
                item(key = "$prefix:$index") {
                    SetEntry(index, set.weight, set.unit, set.basis, set.reps, userEdited = set.userEdited)
                }
            }
        }
    }
}

private fun LazyListScope.candidateItems(candidate: StoredDiaryCandidate, freshness: DiaryResultFreshness?, usedForConfirmation: Boolean) {
    val analysis = candidate.analysis
    item(key = "candidate-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_candidate_result), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (usedForConfirmation) R.string.detail_candidate_used else R.string.detail_candidate_unconfirmed))
            Text(stringResource(when (freshness) {
                DiaryResultFreshness.CURRENT -> R.string.detail_candidate_current
                DiaryResultFreshness.NEEDS_UPDATE -> R.string.detail_needs_update
                else -> R.string.detail_unverifiable
            }))
            if (analysis.sessions.isEmpty()) Text(stringResource(if (analysis.hasErrors) R.string.detail_partial_no_sessions else R.string.detail_candidate_no_sessions))
        }
    }
    analysis.issues.forEachIndexed { index, issue ->
        item(key = "validation:$index") {
            Text(stringResource(R.string.detail_issue, stringResource(if (issue.severity == IssueSeverity.ERROR) R.string.detail_issue_error else R.string.detail_issue_review),
                stringResource(validationLabel(issue.code))), color = if (issue.severity == IssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    analysis.modelIssues.forEachIndexed { index, issue ->
        item(key = "question:$index") { Text(stringResource(R.string.detail_model_question, issue.question)) }
    }
    analysis.sessions.forEachIndexed { sessionIndex, session ->
        item(key = "candidate-session:$sessionIndex") { SessionHeading(sessionIndex, session.date?.toString(), session.notes) }
        session.exercises.forEachIndexed { exerciseIndex, exercise ->
            val prefix = "candidate:$sessionIndex:$exerciseIndex"
            item(key = prefix) { ExerciseHeading(exercise.candidate.rawName, exercise.candidate.notes, exercise.evidence.quote, exercise.sets.isEmpty()) }
            exercise.sets.forEachIndexed { index, set ->
                item(key = "$prefix:$index") {
                    SetEntry(index, set.weight.value, set.unit.value, set.basis.value, set.reps.value, set.countOrigin,
                        set.weight, set.unit, set.basis, set.reps)
                }
            }
        }
    }
}

@Composable
private fun SessionHeading(index: Int, date: String?, notes: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.detail_session, index + 1, date ?: stringResource(R.string.detail_date_unknown)), style = MaterialTheme.typography.titleMedium)
        notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
}

@Composable
private fun ExerciseHeading(name: String, notes: String?, evidence: String?, noSets: Boolean) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
            evidence?.let {
                Text(stringResource(R.string.detail_evidence), style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(it) }
            }
            if (noSets) Text(stringResource(R.string.detail_no_sets))
        }
    }
}

@Composable
private fun SetEntry(index: Int, weight: Double?, unit: WeightUnit?, basis: WeightBasis?, reps: Int?, countOrigin: CandidateOrigin? = null,
    weightSource: CandidateValue<*>? = null, unitSource: CandidateValue<*>? = null,
    basisSource: CandidateValue<*>? = null, repsSource: CandidateValue<*>? = null, userEdited: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 340; isGroupingUsed = false } }
    val unknown = stringResource(R.string.detail_unknown)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_set, index + 1), style = MaterialTheme.typography.titleSmall)
            if (userEdited) Text(stringResource(R.string.detail_user_edited))
            countOrigin?.let { Text(stringResource(R.string.detail_count_source, stringResource(originLabel(it)))) }
            ValueEntry(R.string.detail_weight, weight?.let(numbers::format) ?: unknown, weightSource)
            ValueEntry(R.string.detail_unit, stringResource(when (unit) { WeightUnit.KG -> R.string.detail_kg; WeightUnit.LB -> R.string.detail_lb; else -> R.string.detail_unknown }), unitSource)
            ValueEntry(R.string.detail_basis, stringResource(when (basis) {
                WeightBasis.PER_SIDE -> R.string.detail_basis_per_side; WeightBasis.TOTAL -> R.string.detail_basis_total
                WeightBasis.BODYWEIGHT -> R.string.detail_basis_bodyweight; WeightBasis.ADDED -> R.string.detail_basis_added
                WeightBasis.ASSISTED -> R.string.detail_basis_assisted; else -> R.string.detail_unknown
            }), basisSource)
            ValueEntry(R.string.detail_reps, reps?.let(numbers::format) ?: unknown, repsSource)
        }
    }
}

@Composable
private fun ValueEntry(label: Int, value: String, source: CandidateValue<*>?) {
    Column {
        Text(stringResource(R.string.detail_field, stringResource(label), value))
        if (source != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(originLabel(source.origin)), style = MaterialTheme.typography.labelSmall)
            if (source.inferred && source.origin != CandidateOrigin.INFERRED) Text(stringResource(R.string.detail_origin_inferred), style = MaterialTheme.typography.labelSmall)
            source.inheritedFromGroup?.let { Text(stringResource(R.string.detail_inherited_group, it + 1), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ErrorText(message: Int) { Text(stringResource(message), color = MaterialTheme.colorScheme.error) }

private fun parseStatusLabel(status: ParseRunStatus?) = when (status) {
    null -> R.string.detail_not_parsed
    ParseRunStatus.SUCCEEDED -> R.string.detail_parse_succeeded
    ParseRunStatus.FAILED -> R.string.detail_parse_failed
}

private fun confirmationLabel(status: ConfirmationFreshness) = when (status) {
    ConfirmationFreshness.UNCONFIRMED -> R.string.detail_not_confirmed
    ConfirmationFreshness.CONFIRMED -> R.string.detail_confirmed
    ConfirmationFreshness.NEEDS_UPDATE -> R.string.detail_needs_update
    ConfirmationFreshness.UNVERIFIABLE -> R.string.detail_unverifiable
}

private fun originLabel(origin: CandidateOrigin) = when (origin) {
    CandidateOrigin.EXPLICIT -> R.string.detail_origin_explicit
    CandidateOrigin.INHERITED -> R.string.detail_origin_inherited
    CandidateOrigin.INFERRED -> R.string.detail_origin_inferred
    CandidateOrigin.MISSING -> R.string.detail_origin_missing
}

private fun failureLabel(attempt: DiaryParseAttempt) = when (attempt.failureCode) {
    "MALFORMED_JSON", "INVALID_TOP_LEVEL", "UNSUPPORTED_SCHEMA", "INVALID_RESPONSE" -> R.string.detail_failure_response
    "MODEL_REFUSAL" -> R.string.detail_failure_refusal
    "TIMEOUT" -> R.string.detail_failure_timeout
    "NETWORK_ERROR" -> R.string.detail_failure_network
    "AUTHENTICATION_ERROR" -> R.string.detail_failure_authentication
    "PERMISSION_DENIED" -> R.string.detail_failure_permission
    "RATE_LIMITED" -> R.string.detail_failure_rate_limit
    "REQUEST_REJECTED" -> R.string.detail_failure_request
    "SERVICE_UNAVAILABLE" -> R.string.detail_failure_service
    "EMPTY_RESPONSE" -> R.string.detail_failure_empty
    "TRUNCATED_RESPONSE" -> R.string.detail_failure_truncated
    else -> R.string.detail_parse_failed
}

private fun validationLabel(code: ValidationCode) = when (code) {
    ValidationCode.UNKNOWN_SEGMENT -> R.string.detail_issue_unknown_segment
    ValidationCode.EVIDENCE_NOT_FOUND -> R.string.detail_issue_evidence_missing
    ValidationCode.AMBIGUOUS_EVIDENCE -> R.string.detail_issue_evidence_ambiguous
    ValidationCode.EMPTY_NAME -> R.string.detail_issue_name
    ValidationCode.GROUP_TEXT_NOT_FOUND -> R.string.detail_issue_group_text
    ValidationCode.INVALID_WEIGHT -> R.string.detail_issue_weight
    ValidationCode.INVALID_COUNT -> R.string.detail_issue_count
    ValidationCode.INVALID_REPS -> R.string.detail_issue_reps
    ValidationCode.INCONSISTENT_REPS -> R.string.detail_issue_reps_inconsistent
    ValidationCode.TOO_MANY_SETS -> R.string.detail_issue_too_many_sets
    ValidationCode.INVALID_DATE -> R.string.detail_issue_date
    ValidationCode.DATE_CONFLICT -> R.string.detail_issue_date_conflict
    ValidationCode.MISSING_DATE -> R.string.detail_issue_date_missing
    ValidationCode.MISSING_WEIGHT -> R.string.detail_issue_weight_missing
    ValidationCode.MISSING_UNIT -> R.string.detail_issue_unit_missing
    ValidationCode.MISSING_BASIS -> R.string.detail_issue_basis_missing
    ValidationCode.MISSING_REPS -> R.string.detail_issue_reps_missing
    ValidationCode.MISSING_COUNT -> R.string.detail_issue_count_missing
    ValidationCode.INFERRED_VALUE -> R.string.detail_issue_inferred
    ValidationCode.UNKNOWN_VALUE -> R.string.detail_issue_unknown
}
