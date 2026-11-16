package com.example.fitlog.feature.record.components

import com.example.fitlog.feature.record.NativeRecordActions
import com.example.fitlog.feature.record.NativeRecordUiState
import com.example.fitlog.feature.record.RecordSaveState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.model.record.ReviewedSet
import com.example.fitlog.ui.components.FitLogDimens
import com.example.fitlog.ui.components.fitLogPagePadding

@Composable
internal fun NativeRecordSaveFeedback(state: NativeRecordUiState, actions: NativeRecordActions) {
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
internal fun NativeExerciseEntry(exercise: ReviewedExercise, enabled: Boolean,
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
internal fun OptionalRecordNotes(value: String, enabled: Boolean, label: Int, onValue: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(value.isNotBlank()) }
    if (expanded) OutlinedTextField(value, onValue, enabled = enabled,
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
    else TextButton(onClick = { expanded = true }, enabled = enabled, shapes = ButtonDefaults.shapes()) { Text(stringResource(label)) }
}
