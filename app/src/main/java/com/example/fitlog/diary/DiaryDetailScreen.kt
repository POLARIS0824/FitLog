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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.data.analysis.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(vm: DiaryDetailViewModel, onEdit: () -> Unit, onBack: () -> Unit, onAiSettings: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The app Scaffold already supplies system insets. This page adds only its own spacing.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            MediumFlexibleTopAppBar(
                title = { Text(vm.route.fileName, style = MaterialTheme.typography.headlineSmallEmphasized) },
                subtitle = { Text(vm.route.relPath, style = MaterialTheme.typography.bodySmall) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                } },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.sourceLoading) {
                        Icon(painterResource(R.drawable.refresh_24px), stringResource(R.string.log_refresh))
                    }
                    FilledTonalButton(onClick = onEdit, enabled = vm.canEdit, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.detail_edit), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
            PrimaryTabRow(selectedTabIndex = vm.tab.ordinal) {
                DiaryDetailTab.entries.forEach { tab ->
                    Tab(selected = vm.tab == tab, onClick = { vm.selectTab(tab) }, text = {
                        Text(stringResource(if (tab == DiaryDetailTab.ORIGINAL) R.string.detail_original else R.string.detail_analysis))
                    })
                }
            }
            if (vm.sourceLoading) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            if (vm.tab == DiaryDetailTab.ORIGINAL) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (vm.sourceReadFailed) item { ErrorText(R.string.detail_source_failed) }
                    vm.original?.let { snapshot ->
                        if (!snapshot.file.writable) item { Text(stringResource(R.string.detail_read_only)) }
                        item {
                            if (snapshot.text.isEmpty()) Text(stringResource(R.string.detail_original_empty))
                            else SelectionContainer {
                                Text(snapshot.text, style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            } else {
                AnalysisContent(vm, Modifier.weight(1f), onAiSettings)
            }
        }
    }
}

@Composable
private fun AnalysisContent(vm: DiaryDetailViewModel, modifier: Modifier, onAiSettings: () -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::parse, enabled = !vm.parsing && !vm.sourceLoading,
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                        Icon(painterResource(R.drawable.auto_awesome_24px), null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (vm.parses.latestCandidate == null) R.string.ai_parse else R.string.ai_reparse),
                            style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                    OutlinedButton(onClick = onAiSettings) { Text(stringResource(R.string.ai_settings_title)) }
                    if (vm.parsing) TextButton(onClick = vm::cancelParse) { Text(stringResource(R.string.ai_cancel_request)) }
                }
                if (vm.parsing) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ai_parsing))
                }
                vm.parseMessage?.let { Text(stringResource(it)) }
                if (vm.sourceReadFailed) ErrorText(R.string.detail_source_failed)
                if (vm.parsesLoading || vm.confirmationLoading) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                if (!vm.parsesLoading && !vm.parsesReadFailed) {
                    FitLogNotice(stringResource(R.string.detail_parse_state, stringResource(parseStatusLabel(vm.parses.latestAttempt?.status))))
                }
                if (!vm.confirmationLoading && !vm.confirmationReadFailed) {
                    Text(stringResource(R.string.detail_confirmation_state, stringResource(confirmationLabel(vm.confirmationStatus))),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(stringResource(R.string.detail_confirmed_result), style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.tertiary)
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
            item(key = prefix) {
                ExerciseHeading(exercise.exercise.rawName, exercise.exercise.notes, exercise.sets.isEmpty()) {
                    Excerpt(exercise.exercise.evidence?.quote, R.string.detail_evidence)
                }
            }
            exercise.sets.forEachIndexed { index, set ->
                item(key = "$prefix:$index") {
                    SetEntry(index, set.weight, set.unit, set.basis, set.reps, userEdited = set.userEdited)
                }
            }
        }
    }
}

internal fun LazyListScope.candidateItems(candidate: StoredDiaryCandidate, freshness: DiaryResultFreshness?, usedForConfirmation: Boolean) {
    val analysis = candidate.analysis
    item(key = "candidate-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_candidate_result), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(stringResource(if (usedForConfirmation) R.string.detail_candidate_used else R.string.detail_candidate_unconfirmed))
            Text(stringResource(when (freshness) {
                DiaryResultFreshness.CURRENT -> R.string.detail_candidate_current
                DiaryResultFreshness.NEEDS_UPDATE -> R.string.detail_needs_update
                else -> R.string.detail_unverifiable
            }))
            if (analysis.sessions.all { it.exercises.isEmpty() }) Text(stringResource(if (analysis.hasErrors) R.string.detail_partial_no_sessions else R.string.detail_candidate_no_sessions))
        }
    }
    analysis.issues.filterNot { it.isExcerptReview() }.forEachIndexed { index, issue ->
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
            val groupsWithoutCount = exercise.candidate.groups.withIndex().filter { (index, group) ->
                group.count == null && group.repsList == null && analysis.issues.none {
                    it.severity == IssueSeverity.ERROR && it.path.startsWith("${exercise.path}.groups[$index]")
                }
            }
            item(key = prefix) {
                ExerciseHeading(exercise.candidate.rawName, exercise.candidate.notes,
                    exercise.sets.isEmpty() && groupsWithoutCount.isEmpty()) {
                    Excerpt(exercise.evidence.quote, R.string.detail_model_excerpt,
                        analysis.issues.filter { it.isExcerptReview() && it.path.startsWith("${exercise.path}.evidence.") })
                }
            }
            val setsByGroup = exercise.sets.withIndex().groupBy { it.value.groupIndex }
            exercise.candidate.groups.forEachIndexed { groupIndex, group ->
                if (groupsWithoutCount.any { it.index == groupIndex }) {
                    item(key = "$prefix:group:$groupIndex") {
                        SetEntry(groupIndex, group.weight, group.unit.takeUnless { it == WeightUnit.UNKNOWN },
                            group.basis.takeUnless { it == WeightBasis.UNKNOWN }, group.reps, countMissing = true)
                    }
                }
                setsByGroup[groupIndex].orEmpty().forEach { (index, set) ->
                    item(key = "$prefix:$index") {
                        SetEntry(index, set.weight.value, set.unit.value, set.basis.value, set.reps.value, set.countOrigin,
                            set.weight, set.unit, set.basis, set.reps)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHeading(index: Int, date: String?, notes: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.detail_session, index + 1, date ?: stringResource(R.string.detail_date_unknown)),
            style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.secondary)
        notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
}

@Composable
private fun ExerciseHeading(name: String, notes: String?, noSets: Boolean, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleLargeEmphasized)
            notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
            content()
            if (noSets) Text(stringResource(R.string.detail_no_sets))
        }
    }
}

private fun ValidationIssue.isExcerptReview() = severity == IssueSeverity.REVIEW && when (code) {
    ValidationCode.UNKNOWN_SEGMENT, ValidationCode.EVIDENCE_NOT_FOUND, ValidationCode.AMBIGUOUS_EVIDENCE -> true
    else -> false
}

@Composable
private fun Excerpt(text: String?, label: Int, issues: List<ValidationIssue> = emptyList()) {
    text?.takeIf { it.isNotBlank() }?.let {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(it) }
        issues.forEach { issue ->
            Text(stringResource(validationLabel(issue.code)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetEntry(index: Int, weight: Double?, unit: WeightUnit?, basis: WeightBasis?, reps: Int?, countOrigin: CandidateOrigin? = null,
    weightSource: CandidateValue<*>? = null, unitSource: CandidateValue<*>? = null,
    basisSource: CandidateValue<*>? = null, repsSource: CandidateValue<*>? = null, userEdited: Boolean = false,
    countMissing: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 340; isGroupingUsed = false } }
    val unknown = stringResource(R.string.detail_unknown)
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (countMissing) stringResource(R.string.detail_count_not_provided) else stringResource(R.string.detail_set, index + 1),
                style = MaterialTheme.typography.titleSmall)
            if (userEdited) Text(stringResource(R.string.detail_user_edited))
            countOrigin?.let { Text(stringResource(R.string.detail_count_source, stringResource(originLabel(it)))) }
            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ValueEntry(R.string.detail_weight, weight?.let(numbers::format) ?: unknown, weightSource, Modifier.weight(1f))
                ValueEntry(R.string.detail_reps, reps?.let(numbers::format) ?: unknown, repsSource, Modifier.weight(1f))
                ValueEntry(R.string.detail_unit, stringResource(when (unit) { WeightUnit.KG -> R.string.detail_kg; WeightUnit.LB -> R.string.detail_lb; else -> R.string.detail_unknown }), unitSource, Modifier.weight(1f))
                ValueEntry(R.string.detail_basis, stringResource(when (basis) {
                    WeightBasis.PER_SIDE -> R.string.detail_basis_per_side; WeightBasis.TOTAL -> R.string.detail_basis_total
                    WeightBasis.BODYWEIGHT -> R.string.detail_basis_bodyweight; WeightBasis.ADDED -> R.string.detail_basis_added
                    WeightBasis.ASSISTED -> R.string.detail_basis_assisted; else -> R.string.detail_unknown
                }), basisSource, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ValueEntry(label: Int, value: String, source: CandidateValue<*>?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized)
        if (source != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(originLabel(source.origin)), style = MaterialTheme.typography.labelSmall)
            if (source.inferred && source.origin != CandidateOrigin.INFERRED) Text(stringResource(R.string.detail_origin_inferred), style = MaterialTheme.typography.labelSmall)
            source.inheritedFromGroup?.let { Text(stringResource(R.string.detail_inherited_group, it + 1), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ErrorText(message: Int) { FitLogNotice(stringResource(message), error = true) }

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
