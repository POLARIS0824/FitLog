package com.example.fitlog.feature.log.components

import com.example.fitlog.feature.log.BatchReviewItem

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.FitLogType
import com.example.fitlog.R
import com.example.fitlog.domain.analysis.batchReviewNotices
import com.example.fitlog.ui.text.validationLabel
import com.example.fitlog.ui.training.weightLabel
import com.example.fitlog.model.analysis.BatchReviewAssessment
import com.example.fitlog.model.analysis.BatchReviewBlock
import com.example.fitlog.model.analysis.BatchReviewEligibility
import com.example.fitlog.model.source.IndexedSource
import com.example.fitlog.ui.training.weightBasisLabel

@Composable
internal fun BatchReviewRow(item: BatchReviewItem, selected: Boolean, enabled: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val candidate = item.summary.candidate?.analysis
    val exercises = candidate?.sessions?.flatMap { it.exercises }.orEmpty()
    val supportingColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val spacing = FitLogTheme.spacing
    val colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.fillMaxWidth().padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            val selectionModifier = if (item.selectable) Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current).toggleable(value = selected,
                enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }) else Modifier.fillMaxWidth()
            Row(selectionModifier, verticalAlignment = Alignment.Top) {
                if (item.selectable) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(item.source?.name ?: item.summary.sourceKey.relPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(item.summary.sourceKey.relPath, style = MaterialTheme.typography.bodySmall,
                        color = supportingColor)
                    Text(stringResource(R.string.batch_review_summary, item.assessment.date?.toString()
                        ?: stringResource(R.string.detail_value_unknown), exercises.size, exercises.sumOf { it.sets.size }), style = FitLogType.data)
                }
            }
            if (!item.selectable) Text(stringResource(blockMessage(item.assessment)),
                color = if (item.assessment.eligibility == BatchReviewEligibility.ALREADY_CONFIRMED ||
                    item.assessment.block == BatchReviewBlock.STALE) supportingColor
                    else MaterialTheme.colorScheme.error)
            if (candidate != null) {
                if (exercises.isEmpty()) Text(stringResource(R.string.batch_review_no_training))
                batchReviewNotices(candidate).forEach { Text(stringResource(validationLabel(it)), style = MaterialTheme.typography.bodySmall) }
                candidate.modelIssues.forEach { Text(it.question, style = MaterialTheme.typography.bodySmall) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                TextButton(onClick = { expanded = !expanded }, shapes = ButtonDefaults.shapes()) { Text(stringResource(
                    if (expanded) R.string.batch_review_collapse else R.string.batch_review_expand)) }
                TextButton(onClick = onOpen, enabled = enabled && item.source != null && item.source.status != IndexedSource.MISSING, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.batch_review_open_diary))
                }
            }
            AnimatedVisibility(expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    exercises.forEach { exercise ->
                        Text(exercise.candidate.rawName, style = MaterialTheme.typography.titleSmall)
                        exercise.sets.forEachIndexed { index, set ->
                            Text(stringResource(R.string.batch_review_set, index + 1,
                                weightLabel(set.weight.value, set.unit.value, basis = set.basis.value),
                                stringResource(weightBasisLabel(set.basis.value)), set.reps.value?.toString()
                                    ?: stringResource(R.string.detail_value_unknown)), style = FitLogType.data)
                        }
                        if (exercise.evidence.quote.isNotBlank()) SelectionContainer {
                            Text(exercise.evidence.quote, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun blockMessage(value: BatchReviewAssessment) = when {
    value.eligibility == BatchReviewEligibility.ALREADY_CONFIRMED -> R.string.batch_review_has_confirmation
    else -> when (value.block) {
        BatchReviewBlock.SOURCE -> R.string.detail_source_failed
        BatchReviewBlock.VERSION -> R.string.detail_unverifiable
        BatchReviewBlock.STALE -> R.string.detail_needs_update
        BatchReviewBlock.DATE -> R.string.batch_review_date_problem
        BatchReviewBlock.PARTIAL -> R.string.batch_review_partial_problem
        BatchReviewBlock.ATTEMPT -> R.string.batch_review_attempt_failed
        else -> R.string.detail_candidate_damaged
    }
}
