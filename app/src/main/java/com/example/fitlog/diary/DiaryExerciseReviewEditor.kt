package com.example.fitlog.diary

import com.example.fitlog.ui.components.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.ReviewedExercise

internal data class DiaryExerciseAddress(val session: Int, val exercise: Int)
internal data class DiaryExerciseEdit(val address: DiaryExerciseAddress, val exercise: ReviewedExercise?,
    val fragments: List<DiaryReviewFragment>)

/** Structural corrections are explicit user input; new sets start with unknown values. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryExerciseReviewEditor(address: DiaryExerciseAddress, exercise: ReviewedExercise?, fragments: List<DiaryReviewFragment>,
    onDismiss: () -> Unit, onSave: (String, Int, Map<Int, Int>) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        DiaryExerciseReviewEditorContent(address, exercise, fragments, onDismiss, onSave)
    }
}

@Composable
internal fun DiaryExerciseReviewEditorContent(address: DiaryExerciseAddress, exercise: ReviewedExercise?, fragments: List<DiaryReviewFragment>,
    onDismiss: () -> Unit, onSave: (String, Int, Map<Int, Int>) -> Unit) {
    var name by remember(address) { mutableStateOf(exercise?.rawName.orEmpty()) }
    var count by remember(address) { mutableStateOf(exercise?.sets?.size?.toString().orEmpty()) }
    var counts by remember(address) { mutableStateOf(fragments.associate { it.group to "" }) }
    val knownCount = count.toIntOrNull()?.takeIf { it in 0..1000 }
    val supplied = counts.filterValues { it.isNotBlank() }
    val fragmentCounts = supplied.mapValues { it.value.toIntOrNull() }
    val total = knownCount?.plus(fragmentCounts.values.sumOf { it ?: 0 })
    val valid = name.isNotBlank() && knownCount != null && fragmentCounts.values.all { it != null && it in 0..1000 } &&
        total != null && total <= 1000
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = FitLogDimens.SheetPadding).padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FitLogSheetTitle(stringResource(if (exercise == null) R.string.detail_add_exercise else R.string.detail_edit_exercise_title))
                Text(stringResource(R.string.detail_exercise_review_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.close_24px), stringResource(R.string.detail_close_exercise_editor))
            }
        }
        TextField(name, { name = it }, label = { Text(stringResource(R.string.detail_exercise_name)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.large,
            isError = name.isNotEmpty() && name.isBlank(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        ExerciseSetCount(count, { count = it }, knownCount)
        if (knownCount != null && exercise != null && knownCount < exercise.sets.size) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.warning_24px), null, Modifier.size(20.dp))
                    Text(pluralStringResource(R.plurals.detail_remove_sets_warning, exercise.sets.size - knownCount,
                        exercise.sets.size - knownCount), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (fragments.isNotEmpty()) Text(stringResource(R.string.detail_uncounted_sets),
            style = MaterialTheme.typography.titleMediumEmphasized)
        fragments.forEach { fragment ->
            TextField(counts[fragment.group].orEmpty(), { counts = counts + (fragment.group to it) },
                label = { Text(stringResource(R.string.detail_fragment_count, fragment.group + 1)) },
                supportingText = { Text(stringResource(R.string.detail_fragment_count_help,
                    weightLabel(fragment.weight, fragment.unit, basis = fragment.basis), fragment.reps?.toString()
                        ?: stringResource(R.string.detail_value_unknown))) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.large,
                isError = counts[fragment.group].orEmpty().isNotBlank() && fragmentCounts[fragment.group]?.let { it in 0..1000 } != true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        if (total != null && total > 1000) Text(stringResource(R.string.detail_review_set_limit), color = MaterialTheme.colorScheme.error)
        exercise?.evidence?.quote?.takeIf { it.isNotBlank() }?.let { ExerciseOriginalNote(it) }
        Text(stringResource(R.string.detail_manual_review_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      FitLogSheetActions(onDismiss, enabled = valid,
          confirmLabel = stringResource(R.string.detail_apply_correction),
          modifier = Modifier.padding(horizontal = FitLogDimens.SheetPadding).padding(top = 12.dp, bottom = FitLogDimens.SheetPadding),
          onConfirm = { onSave(name.trim(), requireNotNull(knownCount), fragmentCounts.mapValues { requireNotNull(it.value) }) })
    }
}

@Composable
private fun ExerciseSetCount(count: String, onCount: (String) -> Unit, knownCount: Int?) {
    val caption = stringResource(R.string.detail_review_set_count)
    val focus = LocalFocusManager.current
    val numberStyle = MaterialTheme.typography.headlineMediumEmphasized.copy(textAlign = TextAlign.Center)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberWidth = with(density) { measurer.measure("1000", numberStyle, maxLines = 1).size.width.toDp() } + 32.dp
    val input: @Composable (Modifier) -> Unit = { modifier ->
        TextField(count, onCount, singleLine = true, textStyle = numberStyle, shape = MaterialTheme.shapes.large,
            modifier = modifier.semantics { contentDescription = caption }, isError = count.isNotBlank() && knownCount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow))
    }
    val remove: @Composable () -> Unit = {
        FilledTonalIconButton(onClick = { onCount((requireNotNull(knownCount) - 1).toString()) },
            enabled = knownCount != null && knownCount > 0, modifier = Modifier.size(48.dp), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.remove_24px), stringResource(R.string.detail_remove_one_set))
        }
    }
    val add: @Composable () -> Unit = {
        FilledTonalIconButton(onClick = { onCount(((knownCount ?: 0) + 1).toString()) },
            enabled = count.isBlank() || (knownCount != null && knownCount < 1000), modifier = Modifier.size(48.dp), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.add_24px), stringResource(R.string.detail_add_one_set))
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(caption, style = MaterialTheme.typography.titleMediumEmphasized)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= numberWidth + 112.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        remove(); input(Modifier.weight(1f)); add()
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    input(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { remove(); add() }
                }
            }
            Text(stringResource(R.string.detail_review_set_count_help), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExerciseOriginalNote(quote: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.detail_original_note), Modifier.weight(1f), textAlign = TextAlign.Start)
                val rotation by animateFloatAsState(if (expanded) 270f else 90f,
                    MaterialTheme.motionScheme.fastSpatialSpec(), label = "exerciseOriginalNote")
                Icon(painterResource(R.drawable.chevron_right_24px), null, Modifier.rotate(rotation))
            }
            AnimatedVisibility(expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                SelectionContainer { Text(quote, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
