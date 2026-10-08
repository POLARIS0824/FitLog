package com.example.fitlog.diary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.WeightUnit
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class DiarySetField { WEIGHT, REPS }
internal data class DiarySetEdit(val address: DiarySetAddress, val number: Int, val field: DiarySetField,
    val weight: Double?, val unit: WeightUnit?, val reps: Int?)

// Product shortcuts, not validation limits. Exact input preserves arbitrary source values.
internal fun weightOptions(current: Double?): List<Double?> = listOf(null) +
    ((0..200).map { it * 2.5 } + listOfNotNull(current)).distinct().sorted()
internal fun repOptions(current: Int?): List<Int?> = listOf(null) +
    ((1..100).toList() + listOfNotNull(current)).distinct().sorted()
internal fun enteredWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }
internal fun enteredReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiarySetPicker(edit: DiarySetEdit, onDismiss: () -> Unit, onWeight: (Double?) -> Unit, onReps: (Int?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        DiarySetPickerContent(edit, onDismiss, onWeight, onReps)
    }
}

@Composable
internal fun DiarySetPickerContent(edit: DiarySetEdit, onDismiss: () -> Unit,
    onWeight: (Double?) -> Unit, onReps: (Int?) -> Unit) {
    var exactInput by remember(edit) { mutableStateOf(false) }
    var weight by remember(edit) { mutableStateOf(edit.weight) }
    var reps by remember(edit) { mutableStateOf(edit.reps) }
    var input by remember(edit) { mutableStateOf(if (edit.field == DiarySetField.WEIGHT)
        edit.weight?.toString().orEmpty() else edit.reps?.toString().orEmpty()) }
    val fieldLabel = stringResource(if (edit.field == DiarySetField.WEIGHT) R.string.detail_weight else R.string.detail_reps)
    val validInput = input.isBlank() || if (edit.field == DiarySetField.WEIGHT) enteredWeight(input) != null else enteredReps(input) != null
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 24.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.detail_picker_title, edit.number, fieldLabel),
            style = MaterialTheme.typography.titleLargeEmphasized)
        Text(stringResource(if (edit.field == DiarySetField.WEIGHT) R.string.detail_weight_swipe else R.string.detail_reps_swipe),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (exactInput) {
            OutlinedTextField(input, { input = it }, label = { Text(fieldLabel) }, singleLine = true,
                isError = !validInput, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = if (edit.field == DiarySetField.WEIGHT) KeyboardType.Decimal else KeyboardType.Number),
                supportingText = { Text(stringResource(R.string.detail_exact_input_help)) })
        } else if (edit.field == DiarySetField.WEIGHT) {
            val initialWeight = remember(exactInput) { weight }
            val options = remember(initialWeight) { weightOptions(initialWeight) }
            NumberSnapPicker(options, options.indexOf(initialWeight), horizontal = true,
                label = { value -> weightLabel(value, edit.unit) }, onSelected = { weight = it })
        } else {
            val initialReps = remember(exactInput) { reps }
            val options = remember(initialReps) { repOptions(initialReps) }
            NumberSnapPicker(options, options.indexOf(initialReps), horizontal = false,
                suffix = stringResource(R.string.detail_reps_unit),
                label = { value -> value?.toString() ?: stringResource(R.string.detail_value_unknown) },
                onSelected = { reps = it })
        }
        TextButton(onClick = {
            if (exactInput) {
                if (edit.field == DiarySetField.WEIGHT) weight = enteredWeight(input) else reps = enteredReps(input)
            } else input = if (edit.field == DiarySetField.WEIGHT) weight?.toString().orEmpty() else reps?.toString().orEmpty()
            exactInput = !exactInput
        }, enabled = !exactInput || validInput) { Text(stringResource(if (exactInput) R.string.detail_use_picker else R.string.detail_exact_input)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.editor_cancel)) }
            Button(onClick = {
                if (edit.field == DiarySetField.WEIGHT) onWeight(if (exactInput) enteredWeight(input) else weight)
                else onReps(if (exactInput) enteredReps(input) else reps)
            }, enabled = !exactInput || validInput, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.detail_picker_done))
            }
        }
    }
}

@Composable
private fun <T> NumberSnapPicker(values: List<T>, initial: Int, horizontal: Boolean,
    suffix: String? = null, label: @Composable (T) -> String, onSelected: (T) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val selected by remember(list) { derivedStateOf {
        val info = list.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: initial
    } }
    // Reading the callback through updated state avoids capturing an old edit target.
    val select by rememberUpdatedState(onSelected)
    LaunchedEffect(list, values) {
        snapshotFlow { selected }.collect { index -> if (index in values.indices) select(values[index]) }
    }
    val density = LocalDensity.current
    val itemHeight = with(density) { 40.sp.toDp() + 16.dp }.coerceAtLeast(56.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemWidth = (112.dp * density.fontScale).coerceAtMost(maxWidth - 24.dp)
        val padding = if (horizontal) PaddingValues(horizontal = (maxWidth - itemWidth) / 2)
            else PaddingValues(vertical = itemHeight * 2)
        val fling = rememberSnapFlingBehavior(list)
        val content: LazyListScope.() -> Unit = {
            itemsIndexed(values) { index, value ->
                val chosen = index == selected
                val scale by animateFloatAsState(if (chosen) 1f else .88f,
                    MaterialTheme.motionScheme.fastSpatialSpec(), label = "pickerScale")
                val color by animateColorAsState(when {
                    horizontal && chosen -> MaterialTheme.colorScheme.primary
                    horizontal -> MaterialTheme.colorScheme.surfaceContainerHigh
                    chosen -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                    MaterialTheme.motionScheme.fastEffectsSpec(), label = "pickerColor")
                Surface(shape = CircleShape, color = color,
                    contentColor = when {
                        chosen && horizontal -> MaterialTheme.colorScheme.onPrimary
                        chosen -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = (if (horizontal) Modifier.width(itemWidth) else Modifier.fillParentMaxWidth())
                        .height(itemHeight).graphicsLayer { scaleX = scale; scaleY = scale }
                        .selectable(chosen, role = Role.RadioButton, onClick = { scope.launch { list.animateScrollToItem(index) } })) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(label(value), style = when {
                            chosen && !horizontal -> MaterialTheme.typography.headlineLargeEmphasized
                            chosen -> MaterialTheme.typography.headlineSmallEmphasized
                            else -> MaterialTheme.typography.titleMedium
                        })
                        if (chosen && suffix != null && value != null) Text(suffix, Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (horizontal) LazyRow(state = list, contentPadding = padding, flingBehavior = fling,
            horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(itemHeight + 16.dp), content = content)
        else LazyColumn(state = list, contentPadding = padding, flingBehavior = fling,
            modifier = Modifier.fillMaxWidth().height(itemHeight * 5), content = content)
    }
}
