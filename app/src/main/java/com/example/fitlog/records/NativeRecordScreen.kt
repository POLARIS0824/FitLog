package com.example.fitlog.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.diary.*
import com.example.fitlog.navigation.FitLogBackGuard
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.preview.FitLogPreview
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeRecordScreen(vm: NativeRecordViewModel, onBack: () -> Unit) {
    var datePicker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var setEdit by remember { mutableStateOf<DiarySetEdit?>(null) }
    FitLogBackGuard(enabled = vm.hasChanges || vm.deleting) { vm.requestLeave(onBack) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.record_title), style = MaterialTheme.typography.titleLargeEmphasized) }, windowInsets = WindowInsets(0),
                navigationIcon = { IconButton(onClick = { vm.requestLeave(onBack) }, enabled = !vm.deleting,
                    shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                } }, actions = {
                    TextButton(onClick = { deleting = true }, enabled = vm.canEdit && (vm.exists || vm.input.hasContent),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.record_delete))
                    }
                })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(stringResource(R.string.record_ui_date), style = MaterialTheme.typography.labelLarge,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      OutlinedButton(onClick = { datePicker = true }, enabled = vm.canEdit,
                          shapes = ButtonDefaults.shapes()) {
                          Text(vm.input.date, style = MaterialTheme.typography.titleMediumEmphasized)
                      }
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.state == RecordSaveState.SAVED || vm.state == RecordSaveState.FAILED) Icon(
                            painterResource(if (vm.state == RecordSaveState.FAILED) R.drawable.warning_24px else R.drawable.check_24px),
                            null, Modifier.size(20.dp),
                            tint = if (vm.state == RecordSaveState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(when {
                            vm.loading -> R.string.record_loading
                            else -> when (vm.state) {
                                RecordSaveState.EMPTY -> R.string.record_autosave_help
                                RecordSaveState.SAVING -> R.string.record_saving
                                RecordSaveState.SAVED -> R.string.record_saved
                                RecordSaveState.FAILED -> R.string.record_failed
                            }
                        }), style = MaterialTheme.typography.labelLarge,
                            color = if (vm.state == RecordSaveState.FAILED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                }
                vm.message?.let { message -> item {
                    FitLogNotice(stringResource(message), error = true)
                    if (vm.canEdit) TextButton(onClick = vm::retry) { Text(stringResource(R.string.record_retry)) }
                } }
                if (vm.input.kind == RecordKind.REST) item { FitLogNotice(stringResource(R.string.record_rest)) }
                if (vm.input.exercises.isEmpty() && vm.input.kind != RecordKind.REST) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.record_ui_empty_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.record_empty), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = vm::rest, enabled = vm.canEdit && vm.input.kind != RecordKind.REST,
                            shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.record_mark_rest))
                        }
                    }
                }
                itemsIndexed(vm.input.exercises) { exerciseIndex, exercise ->
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(value = exercise.rawName, onValueChange = { vm.name(exerciseIndex, it) },
                                enabled = vm.canEdit, label = { Text(stringResource(R.string.record_exercise)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            exercise.sets.forEachIndexed { setIndex, set ->
                                NativeSetEditor(set, setIndex + 1, vm.canEdit,
                                    onRemove = { vm.removeSet(exerciseIndex, setIndex) }, onEdit = { field ->
                                        setEdit = DiarySetEdit(DiarySetAddress(0, exerciseIndex, setIndex), setIndex + 1,
                                            field, set.weight, set.unit, set.reps, set.basis)
                                    })
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { vm.addSet(exerciseIndex) }, enabled = vm.canEdit && exercise.sets.size < 1000,
                                    shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.record_add_set))
                                }
                                TextButton(onClick = { vm.removeExercise(exerciseIndex) }, enabled = vm.canEdit,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text(stringResource(R.string.record_remove_exercise))
                                }
                            }
                            TextField(value = exercise.notes.orEmpty(), onValueChange = { vm.exerciseNotes(exerciseIndex, it) },
                                enabled = vm.canEdit, label = { Text(stringResource(R.string.record_exercise_notes)) },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item { Button(onClick = vm::addExercise, enabled = vm.canEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                    Icon(painterResource(R.drawable.add_24px), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.record_add_exercise), style = MaterialTheme.typography.labelLargeEmphasized)
                } }
                item { TextField(value = vm.input.notes, onValueChange = vm::notes, enabled = vm.canEdit,
                    label = { Text(stringResource(R.string.record_notes)) }, modifier = Modifier.fillMaxWidth()) }
                item { Text(stringResource(R.string.record_statistics_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    setEdit?.let { edit -> DiarySetPicker(edit, { setEdit = null }, onWeight = { value ->
        vm.set(edit.address.exercise, edit.address.set) { it.copy(weight = value.weight, unit = value.unit, basis = value.basis) }
        setEdit = null
    }, onReps = { value ->
        vm.set(edit.address.exercise, edit.address.set) { it.copy(reps = value) }; setEdit = null
    }, onBasis = { basis, all ->
        val indices = if (all) vm.input.exercises[edit.address.exercise].sets.indices else listOf(edit.address.set)
        indices.forEach { index -> vm.set(edit.address.exercise, index) {
            if (basis == WeightBasis.BODYWEIGHT) it.copy(weight = null, unit = null, basis = basis) else it.copy(basis = basis)
        } }; setEdit = null
    }) }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(vm.input.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let {
                vm.date(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
            }; datePicker = false }) { Text(stringResource(R.string.record_done)) }
        }, dismissButton = { TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.record_cancel)) } }) {
            DatePicker(state)
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        text = { Text(stringResource(R.string.record_delete_confirm)) },
        confirmButton = { TextButton(onClick = { deleting = false; vm.delete(onBack) }) { Text(stringResource(R.string.record_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.record_cancel)) } })
    if (vm.leaveRequested) AlertDialog(onDismissRequest = vm::cancelLeave,
        text = { Text(stringResource(R.string.record_leave_failed)) },
        confirmButton = { TextButton(onClick = vm::retryAndLeave) { Text(stringResource(R.string.record_retry)) } },
        dismissButton = { Column {
            TextButton(onClick = vm::cancelLeave) { Text(stringResource(R.string.record_keep_editing)) }
            TextButton(onClick = vm::discardAndLeave) { Text(stringResource(R.string.record_discard_changes)) }
        } })
}

@Composable
private fun NativeSetEditor(set: ReviewedSet, number: Int, enabled: Boolean,
    onRemove: () -> Unit, onEdit: (DiarySetField) -> Unit) {
    val label = stringResource(R.string.record_set_number, number)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmallEmphasized)
            IconButton(onClick = onRemove, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.remove_24px), stringResource(R.string.record_remove_set))
            }
        }
        val fields: @Composable (Boolean) -> Unit = { stacked ->
            val weight: @Composable (Modifier) -> Unit = { modifier ->
                NativeSetValue(stringResource(R.string.detail_weight), weightLabel(set.weight, set.unit, basis = set.basis),
                    label, enabled, modifier, emphasized = true, onClick = { onEdit(DiarySetField.WEIGHT) })
            }
            val reps: @Composable (Modifier) -> Unit = { modifier ->
                NativeSetValue(stringResource(R.string.detail_reps), set.reps?.toString() ?: stringResource(R.string.detail_value_unknown),
                    label, enabled, modifier, onClick = { onEdit(DiarySetField.REPS) })
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weight(Modifier.fillMaxWidth()); reps(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weight(Modifier.weight(1f)); reps(Modifier.weight(1f))
            }
        }
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) { fields(fontScale > 1.3f || maxWidth < 280.dp) }
        NativeSetValue(stringResource(R.string.detail_basis), stringResource(basisLabel(set.basis)), label, enabled,
            Modifier.fillMaxWidth(), compact = true, onClick = { onEdit(DiarySetField.BASIS) })
    }
}

@Composable
private fun NativeSetValue(label: String, value: String, setLabel: String, enabled: Boolean, modifier: Modifier,
    emphasized: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val description = stringResource(R.string.record_ui_edit_set, setLabel, label, value)
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = description }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmallEmphasized)
        }
    }
}

@Preview(name = "Native record sets", widthDp = 412, heightDp = 700, locale = "en")
@Composable
private fun NativeRecordSetsReviewPreview() {
    FitLogPreview {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.preview_exercise_name), style = MaterialTheme.typography.headlineSmallEmphasized)
            NativeSetEditor(ReviewedSet(weight = 60.0, unit = WeightUnit.KG, basis = WeightBasis.TOTAL, reps = 8),
                number = 1, enabled = true, onRemove = {}, onEdit = {})
            NativeSetEditor(ReviewedSet(unit = WeightUnit.KG, basis = WeightBasis.TOTAL),
                number = 2, enabled = true, onRemove = {}, onEdit = {})
        }
    }
}

@Preview(name = "Native record sets · Large text", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "zh")
@Composable
private fun NativeRecordSetsLargeTextReviewPreview() {
    NativeRecordSetsReviewPreview()
}
