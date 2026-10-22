package com.example.fitlog.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.diary.*

@Composable
internal fun NativeSetEntry(set: ReviewedSet, number: Int, enabled: Boolean,
    canCopy: Boolean, onChange: (ReviewedSet) -> Unit, onOptions: () -> Unit, onCopy: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val setLabel = stringResource(R.string.record_set_number, number)
    var weightText by rememberSaveable { mutableStateOf(set.weight?.toString().orEmpty()) }
    var repsText by rememberSaveable { mutableStateOf(set.reps?.toString().orEmpty()) }
    // Preserve a decimal separator while typing; resync after copy, undo or changing units.
    LaunchedEffect(set.weight) {
        if (enteredWeight(weightText) != set.weight) weightText = set.weight?.toString().orEmpty()
    }
    LaunchedEffect(set.reps) {
        if (enteredReps(repsText) != set.reps) repsText = set.reps?.toString().orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(setLabel, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.weight(1f))
            Text(stringResource(basisLabel(set.basis)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                IconButton(onClick = { menu = true }, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.record_set_options, number))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.record_weight_options)) },
                        onClick = { menu = false; onOptions() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.record_copy_set)) }, enabled = canCopy,
                        onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.record_remove_set), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
        val weight: @Composable (Modifier) -> Unit = { modifier ->
            val bodyweight = set.basis == WeightBasis.BODYWEIGHT
            OutlinedTextField(value = if (bodyweight) stringResource(R.string.detail_weight_bodyweight) else weightText,
                onValueChange = { text ->
                    if (text.isEmpty() || (text.matches(Regex("[0-9]*[.,]?[0-9]*")) && enteredWeight(text) != null)) {
                        weightText = text; onChange(set.copy(weight = enteredWeight(text)))
                    }
                }, enabled = enabled, readOnly = bodyweight, singleLine = true,
                label = { Text(stringResource(R.string.detail_weight)) },
                suffix = if (bodyweight) null else ({ Text(stringResource(when (set.unit) {
                    WeightUnit.KG -> R.string.detail_unit_kg_short
                    WeightUnit.LB -> R.string.detail_unit_lb_short
                    else -> R.string.detail_unit_unknown_short
                })) }),
                placeholder = { Text(stringResource(R.string.record_weight_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                modifier = modifier.semantics { contentDescription = setLabel })
        }
        val reps: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedTextField(value = repsText, onValueChange = { text ->
                if (text.isEmpty() || (text.all(Char::isDigit) && enteredReps(text) != null)) {
                    repsText = text; onChange(set.copy(reps = enteredReps(text)))
                }
            }, enabled = enabled, singleLine = true, label = { Text(stringResource(R.string.detail_reps)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                modifier = modifier.semantics { contentDescription = setLabel })
        }
        val largeText = LocalDensity.current.fontScale > 1.3f
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (largeText || maxWidth < 280.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weight(Modifier.fillMaxWidth()); reps(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weight(Modifier.weight(1f)); reps(Modifier.weight(1f))
            }
        }
    }
}

internal data class NativeSetOptions(val exercise: Int, val set: Int, val value: ReviewedSet)

/** Numeric load, unit and its meaning have a single settings entry point. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeSetOptionsSheet(edit: NativeSetOptions, onDismiss: () -> Unit,
    onConfirm: (WeightUnit?, WeightBasis, Boolean) -> Unit) {
    var unit by remember { mutableStateOf(edit.value.unit ?: WeightUnit.UNKNOWN) }
    var basis by remember { mutableStateOf(edit.value.basis ?: WeightBasis.UNKNOWN) }
    var applyAll by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.record_weight_options), style = MaterialTheme.typography.titleLargeEmphasized)
            if (basis != WeightBasis.BODYWEIGHT) {
                Text(stringResource(R.string.detail_unit), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(WeightUnit.KG, WeightUnit.LB, WeightUnit.UNKNOWN).forEach { choice ->
                        FilterChip(selected = unit == choice, onClick = { unit = choice },
                            modifier = Modifier.semantics { role = Role.RadioButton }, label = {
                                Text(stringResource(when (choice) {
                                    WeightUnit.KG -> R.string.detail_unit_kg_short
                                    WeightUnit.LB -> R.string.detail_unit_lb_short
                                    else -> R.string.detail_keep_unknown
                                }))
                            })
                    }
                }
            }
            Text(stringResource(R.string.detail_basis), style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightBasis.entries.forEach { choice ->
                    FilterChip(selected = basis == choice, onClick = { basis = choice },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        label = { Text(stringResource(basisLabel(choice))) })
                }
            }
            Text(stringResource(if (basis == WeightBasis.BODYWEIGHT) R.string.record_bodyweight_change_help
                else R.string.record_weight_options_help), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().toggleable(applyAll, role = Role.Checkbox, onValueChange = { applyAll = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(applyAll, onCheckedChange = null)
                Text(stringResource(R.string.detail_basis_apply_exercise), Modifier.weight(1f))
            }
            Button(onClick = { onConfirm(unit, basis, applyAll) },
                enabled = basis == WeightBasis.BODYWEIGHT || DiaryWeightValue(edit.value.weight, edit.value.unit).inUnit(unit).weight?.isFinite() != false,
                modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.record_done))
            }
        }
    }
}
