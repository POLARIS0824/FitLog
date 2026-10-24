package com.example.fitlog.feature.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.model.record.ReviewedSet
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.ui.components.FitLogBackGuard
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.training.WeightEditValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeRecordScreen(vm: NativeRecordViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) { vm.closeHandled(); currentOnBack() }
    }
    NativeRecordContent(state, NativeRecordActions(
        requestLeave = vm::requestLeave, retry = vm::retry, rest = vm::rest,
        name = vm::name, removeSet = vm::removeSet, addSet = vm::addSet,
        removeExercise = vm::removeExercise, exerciseNotes = vm::exerciseNotes,
        addExercise = vm::addExercise, notes = vm::notes, set = vm::set, date = vm::date,
        delete = vm::delete, cancelLeave = vm::cancelLeave, retryAndLeave = vm::retryAndLeave,
        discardAndLeave = vm::discardAndLeave, copySet = vm::copySet, undoRemoval = vm::undoRemoval, dismissUndo = vm::dismissUndo,
    ))
}

internal data class NativeRecordActions(
    val requestLeave: () -> Unit, val retry: () -> Unit, val rest: () -> Unit,
    val name: (Int, String) -> Unit, val removeSet: (Int, Int) -> Unit,
    val addSet: (Int) -> Unit, val removeExercise: (Int) -> Unit,
    val exerciseNotes: (Int, String) -> Unit, val addExercise: () -> Unit,
    val notes: (String) -> Unit, val set: (Int, Int, (ReviewedSet) -> ReviewedSet) -> Unit,
    val date: (LocalDate) -> Unit, val delete: () -> Unit, val cancelLeave: () -> Unit,
    val retryAndLeave: () -> Unit, val discardAndLeave: () -> Unit,
    val copySet: (Int, Int) -> Unit, val undoRemoval: () -> Unit, val dismissUndo: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeRecordContent(state: NativeRecordUiState, actions: NativeRecordActions) {
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var setEdit by remember { mutableStateOf<NativeSetOptions?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val behavior = rememberFitLogTopAppBarBehavior()
    val pagePadding = fitLogPagePadding()
    FitLogBackGuard(enabled = state.hasChanges || state.deleting) { actions.requestLeave() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().imePadding().nestedScroll(behavior.nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally) {
            FitLogTopAppBar(stringResource(R.string.record_title), behavior,
                onBack = { actions.requestLeave() }, backEnabled = !state.deleting, actions = {
                    TextButton(onClick = { deleting = true }, enabled = state.canEdit && (state.exists || state.input.hasContent),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.record_delete))
                    }
                })
            NativeRecordSaveFeedback(state, actions)
            LazyColumn(Modifier.weight(1f).widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth(), state = listState, contentPadding = PaddingValues(pagePadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(stringResource(R.string.record_ui_date), style = MaterialTheme.typography.labelLarge,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      OutlinedButton(onClick = { datePicker = true }, enabled = state.canEdit,
                          shapes = ButtonDefaults.shapes()) {
                          Text(state.input.date, style = MaterialTheme.typography.titleMediumEmphasized)
                      }
                    }
                }
                if (state.input.kind == RecordKind.REST) item { FitLogNotice(stringResource(R.string.record_rest)) }
                if (state.input.exercises.isEmpty() && state.input.kind != RecordKind.REST) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.record_ui_empty_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.record_empty), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = actions.rest, enabled = state.canEdit && state.input.kind != RecordKind.REST,
                            shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.record_mark_rest))
                        }
                    }
                }
                itemsIndexed(state.input.exercises) { exerciseIndex, exercise ->
                    NativeExerciseEntry(exercise, state.canEdit,
                        onName = { actions.name(exerciseIndex, it) },
                        onNotes = { actions.exerciseNotes(exerciseIndex, it) },
                        onAddSet = { actions.addSet(exerciseIndex) },
                        onCopy = { index -> actions.copySet(exerciseIndex, index) },
                        onRemove = { actions.removeExercise(exerciseIndex) },
                        onSet = { index, value -> actions.set(exerciseIndex, index) { value } },
                        onOptions = { index -> setEdit = NativeSetOptions(exerciseIndex, index, exercise.sets[index]) },
                        onRemoveSet = { actions.removeSet(exerciseIndex, it) })
                }
                item { OptionalRecordNotes(state.input.notes, state.canEdit, R.string.record_notes, actions.notes) }
                item { Text(stringResource(R.string.record_statistics_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state.canUndoRemoval) Snackbar(
                modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth().padding(horizontal = pagePadding),
                action = { TextButton(onClick = actions.undoRemoval, enabled = state.canEdit,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.record_undo))
                } },
                dismissAction = { TextButton(onClick = actions.dismissUndo,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_dismiss)) } }) {
                Text(stringResource(R.string.record_removed))
            }
            Surface(modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                FilledTonalButton(onClick = {
                    actions.addExercise()
                    scope.launch { listState.animateScrollToItem(state.input.exercises.size) }
                }, enabled = state.canEdit, modifier = Modifier.fillMaxWidth().padding(horizontal = pagePadding, vertical = 8.dp)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                    Icon(painterResource(R.drawable.add_24px), null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.record_add_exercise), style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
        }
    }
    setEdit?.let { edit -> NativeSetOptionsSheet(edit, { setEdit = null }) { unit, basis, all ->
        val indices = if (all) state.input.exercises[edit.exercise].sets.indices else listOf(edit.set)
        indices.forEach { index -> actions.set(edit.exercise, index) { value ->
            if (basis == WeightBasis.BODYWEIGHT) value.copy(weight = null, unit = null, basis = basis)
            else if (index == edit.set) {
                val converted = WeightEditValue(value.weight, value.unit, basis = basis).inUnit(unit)
                value.copy(weight = converted.weight, unit = converted.unit, basis = basis)
            } else value.copy(basis = basis)
        } }
        setEdit = null
    } }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(state.input.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let {
                actions.date(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
            }; datePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_done)) }
        }, dismissButton = { TextButton(onClick = { datePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_cancel)) } }) {
            DatePicker(state)
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        text = { Text(stringResource(R.string.record_delete_confirm)) },
        confirmButton = { TextButton(onClick = { deleting = false; actions.delete() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_cancel)) } })
    if (state.leaveRequested) AlertDialog(onDismissRequest = actions.cancelLeave,
        text = { Text(stringResource(R.string.record_leave_failed)) },
        confirmButton = { TextButton(onClick = actions.retryAndLeave, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_retry)) } },
        dismissButton = { Column {
            TextButton(onClick = actions.cancelLeave, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_keep_editing)) }
            TextButton(onClick = actions.discardAndLeave, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_discard_changes)) }
        } })
}

@Composable
private fun NativeRecordSaveFeedback(state: NativeRecordUiState, actions: NativeRecordActions) {
    val failed = state.state == RecordSaveState.FAILED || state.message != null
    val label = when {
        state.deleting -> R.string.record_deleting
        state.loading -> R.string.record_loading
        state.message != null -> requireNotNull(state.message)
        else -> when (state.state) {
            RecordSaveState.EMPTY -> R.string.record_autosave_help
            RecordSaveState.SAVING -> R.string.record_saving
            RecordSaveState.SAVED -> R.string.record_saved
            RecordSaveState.FAILED -> R.string.record_failed
        }
    }
    Surface(modifier = Modifier.widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth(), color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = fitLogPagePadding(), vertical = 8.dp)
            .semantics { if (failed) liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failed || state.state == RecordSaveState.SAVED) Icon(
                painterResource(if (failed) R.drawable.warning_24px else R.drawable.check_24px), null, Modifier.size(20.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (failed && !state.loading && !state.deleting && (state.readFailed || state.canEdit)) TextButton(onClick = {
                if (state.message == R.string.record_delete_failed) actions.delete() else actions.retry()
            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_retry)) }
        }
    }
}

@Composable
private fun NativeExerciseEntry(exercise: ReviewedExercise, enabled: Boolean,
    onName: (String) -> Unit, onNotes: (String) -> Unit, onAddSet: () -> Unit,
    onCopy: (Int) -> Unit, onRemove: () -> Unit, onSet: (Int, ReviewedSet) -> Unit,
    onOptions: (Int) -> Unit, onRemoveSet: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(exercise.rawName, onName, enabled = enabled, singleLine = true,
                textStyle = MaterialTheme.typography.titleLargeEmphasized,
                label = { Text(stringResource(R.string.record_exercise)) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onAddSet, enabled = enabled && exercise.sets.size < 1000,
                    shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.record_add_set)) }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.record_exercise_options))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        if (exercise.sets.isNotEmpty()) DropdownMenuItem(
                            text = { Text(stringResource(R.string.record_copy_last_set)) },
                            enabled = exercise.sets.size < 1000,
                            onClick = { menu = false; onCopy(exercise.sets.lastIndex) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.record_remove_exercise), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onRemove() })
                    }
                }
            }
            exercise.sets.forEachIndexed { index, set ->
                // A positional editor must not carry its local text into a different row after deletion.
                key(index, exercise.sets.size) {
                    NativeSetEntry(set, index + 1, enabled, exercise.sets.size < 1000, { onSet(index, it) }, { onOptions(index) },
                        { onCopy(index) }, { onRemoveSet(index) })
                }
            }
            if (exercise.sets.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddSet, enabled = enabled && exercise.sets.size < 1000, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.record_add_set))
                }
                TextButton(onClick = { onCopy(exercise.sets.lastIndex) }, enabled = enabled && exercise.sets.size < 1000, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.record_copy_last_set))
                }
            }
            OptionalRecordNotes(exercise.notes.orEmpty(), enabled, R.string.record_exercise_notes, onNotes)
        }
    }
}

@Composable
private fun OptionalRecordNotes(value: String, enabled: Boolean, label: Int, onValue: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(value.isNotBlank()) }
    if (expanded) OutlinedTextField(value, onValue, enabled = enabled,
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
    else TextButton(onClick = { expanded = true }, enabled = enabled, shapes = ButtonDefaults.shapes()) { Text(stringResource(label)) }
}

@Preview(name = "Native record sets", widthDp = 412, heightDp = 700, locale = "en")
@Composable
private fun NativeRecordSetsReviewPreview() {
    FitLogPreview {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NativeExerciseEntry(ReviewedExercise(stringResource(R.string.preview_exercise_name), listOf(
                ReviewedSet(weight = 60.0, unit = WeightUnit.KG, basis = WeightBasis.TOTAL, reps = 8),
                ReviewedSet(unit = WeightUnit.KG, basis = WeightBasis.TOTAL))), true,
                {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
    }
}

@Preview(name = "Native record sets · Large text", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "zh")
@Composable
private fun NativeRecordSetsLargeTextReviewPreview() { NativeRecordSetsReviewPreview() }
