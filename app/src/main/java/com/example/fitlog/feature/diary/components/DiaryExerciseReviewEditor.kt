package com.example.fitlog.feature.diary.components

import com.example.fitlog.feature.diary.DiaryExerciseAddress
import com.example.fitlog.feature.diary.DiaryReviewFragment
import com.example.fitlog.ui.training.weightLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.fitlog.R
import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.ui.components.FitLogSheetActions
import com.example.fitlog.ui.components.FitLogSheetTitle
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.FitLogType

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
    val spacing = FitLogTheme.spacing
    val sheetPadding = FitLogTheme.dimens.page.sheetPadding
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
      Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = sheetPadding).padding(top = spacing.s),
          verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                FitLogSheetTitle(stringResource(if (exercise == null) R.string.detail_add_exercise else R.string.detail_edit_exercise_title))
                Text(stringResource(R.string.detail_exercise_review_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.close_24px), stringResource(R.string.detail_close_exercise_editor))
            }
        }
        TextField(name, { name = it }, label = { Text(stringResource(R.string.detail_exercise_name)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            isError = name.isNotEmpty() && name.isBlank(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        ExerciseSetCount(count, { count = it }, knownCount)
        if (knownCount != null && exercise != null && knownCount < exercise.sets.size) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Row(Modifier.padding(spacing.l), horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Icon(painterResource(R.drawable.warning_24px), null)
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
                modifier = Modifier.fillMaxWidth(), singleLine = true,
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
          modifier = Modifier.padding(horizontal = sheetPadding).padding(top = spacing.m, bottom = sheetPadding),
          onConfirm = { onSave(name.trim(), requireNotNull(knownCount), fragmentCounts.mapValues { requireNotNull(it.value) }) })
    }
}

@Composable
private fun ExerciseSetCount(count: String, onCount: (String) -> Unit, knownCount: Int?) {
    val caption = stringResource(R.string.detail_review_set_count)
    val focus = LocalFocusManager.current
    val numberStyle = FitLogType.centeredNumber
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spacing = FitLogTheme.spacing
    // TextField insets its text by 16dp on each side.
    val numberWidth = with(density) { measurer.measure("1000", numberStyle, maxLines = 1).size.width.toDp() } + spacing.l * 2
    val stepperSize = IconButtonDefaults.mediumContainerSize()
    val input: @Composable (Modifier) -> Unit = { modifier ->
        // Default filled-field shape (extraSmall top) already fits the card's 12dp corner minus its 16dp inset.
        // The fill is set because the default field color equals the filled card's.
        TextField(count, onCount, singleLine = true, textStyle = numberStyle,
            modifier = modifier.semantics { contentDescription = caption }, isError = count.isNotBlank() && knownCount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = FitLogContainment.nestedColor,
                unfocusedContainerColor = FitLogContainment.nestedColor))
    }
    val remove: @Composable () -> Unit = {
        FilledTonalIconButton(onClick = { onCount((requireNotNull(knownCount) - 1).toString()) },
            enabled = knownCount != null && knownCount > 0, modifier = Modifier.size(stepperSize), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.remove_24px), stringResource(R.string.detail_remove_one_set))
        }
    }
    val add: @Composable () -> Unit = {
        FilledTonalIconButton(onClick = { onCount(((knownCount ?: 0) + 1).toString()) },
            enabled = count.isBlank() || (knownCount != null && knownCount < 1000), modifier = Modifier.size(stepperSize), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.add_24px), stringResource(R.string.detail_add_one_set))
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            Text(caption, style = MaterialTheme.typography.titleMediumEmphasized)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= numberWidth + (stepperSize.width + spacing.s) * 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.CenterVertically) {
                        remove(); input(Modifier.weight(1f)); add()
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
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
    val itemColors = FitLogContainment.sheetListItemColors()
    val segments = if (expanded) 2 else 1
    // The toggle and the revealed note form one segmented group.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        val rotation by animateFloatAsState(if (expanded) 270f else 90f,
            MaterialTheme.motionScheme.fastSpatialSpec(), label = "exerciseOriginalNote")
        SegmentedListItem(onClick = { expanded = !expanded }, shapes = ListItemDefaults.segmentedShapes(0, segments),
            colors = itemColors,
            trailingContent = { Icon(painterResource(R.drawable.chevron_right_24px), null, Modifier.rotate(rotation)) }) {
            Text(stringResource(R.string.detail_original_note))
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Surface(Modifier.fillMaxWidth(), shape = ListItemDefaults.segmentedShapes(1, 2).shape,
                color = itemColors.containerColor, contentColor = itemColors.contentColor) {
                SelectionContainer { Text(quote, Modifier.padding(FitLogTheme.spacing.l), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
