package com.example.fitlog.diary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.WeightUnit
import com.example.fitlog.data.analysis.WeightBasis
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.math.abs

internal enum class DiarySetField { WEIGHT, REPS }
internal data class DiarySetEdit(val address: DiarySetAddress, val number: Int, val field: DiarySetField,
    val weight: Double?, val unit: WeightUnit?, val reps: Int?, val basis: WeightBasis? = null)

internal fun repOptions(current: Int?): List<Int?> = listOf(null) +
    ((1..100).toList() + listOfNotNull(current)).distinct().sorted()
internal fun enteredReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiarySetPicker(edit: DiarySetEdit, onDismiss: () -> Unit, onWeight: (DiaryWeightValue) -> Unit, onReps: (Int?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        DiarySetPickerContent(edit, onDismiss, onWeight, onReps)
    }
}

@Composable
internal fun DiarySetPickerContent(edit: DiarySetEdit, onDismiss: () -> Unit,
    onWeight: (DiaryWeightValue) -> Unit, onReps: (Int?) -> Unit) {
    var exactInput by remember(edit) { mutableStateOf(false) }
    // Keep the entered value in its own unit. Switching units twice cannot accumulate rounding.
    val initialUnit = edit.unit.takeIf { it == WeightUnit.KG || it == WeightUnit.LB } ?: WeightUnit.KG
    var type by remember(edit) { mutableStateOf(weightType(edit.weight, edit.basis)) }
    var entered by remember(edit) { mutableStateOf(DiaryWeightValue(edit.weight, initialUnit, basis = numericWeightBasis(edit.basis))) }
    var unit by remember(edit) { mutableStateOf(initialUnit) }
    var step by remember(edit) { mutableDoubleStateOf(defaultWeightStep(initialUnit)) }
    val weight = entered.inUnit(unit).weight
    var reps by remember(edit) { mutableStateOf(edit.reps) }
    var input by remember(edit) { mutableStateOf(if (edit.field == DiarySetField.WEIGHT)
        edit.weight?.toString().orEmpty() else edit.reps?.toString().orEmpty()) }
    val fieldLabel = stringResource(if (edit.field == DiarySetField.WEIGHT) R.string.detail_weight else R.string.detail_reps)
    val validInput = input.isBlank() || if (edit.field == DiarySetField.WEIGHT) enteredWeight(input) != null else enteredReps(input) != null
    fun acceptEnteredWeight(value: Double?) {
        if (value != weight) entered = DiaryWeightValue(value, unit, basis = numericWeightBasis(edit.basis))
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 24.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.detail_picker_title, edit.number, fieldLabel),
            style = MaterialTheme.typography.titleLargeEmphasized)
        if (edit.field == DiarySetField.WEIGHT) {
            ConnectedWeightButtons(DiaryWeightType.entries, type,
                label = { stringResource(when (it) {
                    DiaryWeightType.UNRECORDED -> R.string.detail_weight_unrecorded
                    DiaryWeightType.BODYWEIGHT -> R.string.detail_weight_bodyweight
                    DiaryWeightType.NUMERIC -> R.string.detail_weight_numeric
                }) }, onSelect = { target ->
                    if (type == DiaryWeightType.NUMERIC && exactInput && validInput) acceptEnteredWeight(enteredWeight(input))
                    type = target
                })
            if (type != DiaryWeightType.NUMERIC) Text(stringResource(
                if (type == DiaryWeightType.BODYWEIGHT) R.string.detail_weight_bodyweight_help else R.string.detail_weight_unrecorded_help),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (edit.field == DiarySetField.REPS || type == DiaryWeightType.NUMERIC) {
            if (edit.field == DiarySetField.WEIGHT) {
                if (unit == WeightUnit.KG && edit.weight != null && edit.unit !in listOf(WeightUnit.KG, WeightUnit.LB)) {
                    Text(stringResource(R.string.detail_weight_assumed_kg), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.detail_unit), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConnectedWeightButtons(listOf(WeightUnit.KG, WeightUnit.LB), unit,
                    label = { weightUnitLabel(it) }, enabled = !exactInput || validInput,
                    canSelect = { target ->
                        val value = if (exactInput) DiaryWeightValue(enteredWeight(input), unit) else entered.inUnit(unit)
                        value.inUnit(target).weight?.isFinite() != false
                    }, onSelect = { target -> if (target != unit) {
                        if (exactInput) acceptEnteredWeight(enteredWeight(input))
                        unit = target
                        step = defaultWeightStep(target)
                        input = entered.inUnit(target).weight?.toString().orEmpty()
                    } })
            }
            Text(stringResource(if (edit.field == DiarySetField.WEIGHT) R.string.detail_weight_swipe else R.string.detail_reps_swipe),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (exactInput) {
                OutlinedTextField(input, { input = it }, label = { Text(fieldLabel) }, singleLine = true,
                    isError = !validInput, modifier = Modifier.fillMaxWidth(),
                    suffix = if (edit.field == DiarySetField.WEIGHT) ({ Text(weightUnitLabel(unit)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = if (edit.field == DiarySetField.WEIGHT) KeyboardType.Decimal else KeyboardType.Number),
                    supportingText = { Text(stringResource(if (edit.field == DiarySetField.WEIGHT)
                        R.string.detail_weight_exact_help else R.string.detail_exact_input_help)) })
            } else if (edit.field == DiarySetField.WEIGHT) {
                key(unit, step, exactInput) {
                    val initialWeight = remember { weight }
                    val options = remember { weightOptions(initialWeight, step) }
                    val precision = maxOf(3, BigDecimal.valueOf(step).stripTrailingZeros().scale())
                    val emptyLabel = stringResource(R.string.detail_weight_unfilled)
                    val tagWidth = (maxOf(weightNumberLabel(initialWeight, precision).length, emptyLabel.length) * 14 + 40).dp.coerceAtLeast(112.dp)
                    NumberSnapPicker(options, options.indexOf(initialWeight), horizontal = true,
                        minimumItemWidth = tagWidth, suffix = weightUnitLabel(unit),
                        label = { value -> if (value == null) emptyLabel else weightNumberLabel(value, precision) },
                        description = { value -> if (value == null) emptyLabel else weightLabel(value, unit) }, onSelected = ::acceptEnteredWeight)
                }
            } else {
                val initialReps = remember(exactInput) { reps }
                val options = remember(initialReps) { repOptions(initialReps) }
                NumberSnapPicker(options, options.indexOf(initialReps), horizontal = false,
                    suffix = stringResource(R.string.detail_reps_unit),
                    label = { value -> value?.toString() ?: stringResource(R.string.detail_value_unknown) },
                    onSelected = { reps = it })
            }
            if (edit.field == DiarySetField.WEIGHT) WeightStepControl(step, unit, onSelect = { step = it })
            TextButton(onClick = {
                if (exactInput) {
                    if (edit.field == DiarySetField.WEIGHT) acceptEnteredWeight(enteredWeight(input)) else reps = enteredReps(input)
                } else input = if (edit.field == DiarySetField.WEIGHT) weight?.toString().orEmpty() else reps?.toString().orEmpty()
                exactInput = !exactInput
            }, enabled = !exactInput || validInput) { Text(stringResource(if (exactInput) R.string.detail_use_picker else R.string.detail_exact_input)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.editor_cancel)) }
            Button(onClick = {
                if (edit.field == DiarySetField.WEIGHT) {
                    val value = if (exactInput) enteredWeight(input) else weight
                    onWeight(when (type) {
                        DiaryWeightType.UNRECORDED -> DiaryWeightValue(null, null, basis = numericWeightBasis(edit.basis))
                        DiaryWeightType.BODYWEIGHT -> DiaryWeightValue(null, null, basis = WeightBasis.BODYWEIGHT)
                        DiaryWeightType.NUMERIC -> if (value == weight) entered.inUnit(unit)
                            else DiaryWeightValue(value, unit, basis = numericWeightBasis(edit.basis))
                    })
                }
                else onReps(if (exactInput) enteredReps(input) else reps)
            }, enabled = if (edit.field == DiarySetField.WEIGHT) type != DiaryWeightType.NUMERIC ||
                (if (exactInput) enteredWeight(input) != null else weight != null)
                else !exactInput || validInput,
                modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.detail_picker_done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedWeightButtons(choices: List<T>, selected: T, label: @Composable (T) -> String,
    enabled: Boolean = true, canSelect: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    val labels = choices.map { label(it) }
    val textStyle = MaterialTheme.typography.labelLargeEmphasized
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minimumWidth = with(density) { labels.maxOf { measurer.measure(it, textStyle, maxLines = 1).size.width }.toDp() } + 24.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = ButtonGroupDefaults.ConnectedSpaceBetween
        val stacked = (maxWidth - spacing * (choices.size - 1)) / choices.size < minimumWidth
        val buttons: @Composable (Modifier) -> Unit = { buttonModifier ->
            choices.forEachIndexed { index, choice ->
                FilledTonalToggleButton(checked = selected == choice, onCheckedChange = { onSelect(choice) },
                    enabled = enabled && canSelect(choice), modifier = buttonModifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    shapes = if (stacked) FilledTonalToggleButtonDefaults.shapesFor(48.dp) else when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }) { Text(labels[index], style = textStyle, maxLines = 1, softWrap = false) }
            }
        }
        if (stacked) Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            buttons(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            buttons(Modifier.weight(1f))
        }
    }
}

@Composable
private fun weightUnitLabel(unit: WeightUnit?) = stringResource(when (unit) {
    WeightUnit.KG -> R.string.detail_unit_kg_short
    WeightUnit.LB -> R.string.detail_unit_lb_short
    else -> R.string.detail_unit_unknown_short
})

@Composable
private fun WeightStepControl(step: Double, unit: WeightUnit?, onSelect: (Double) -> Unit) {
    var menu by remember(unit) { mutableStateOf(false) }
    var custom by remember(unit) { mutableStateOf(false) }
    val options = listOf(2.5, 5.0, 10.0)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            TextButton(onClick = { menu = true }) {
                Text(stringResource(R.string.detail_weight_step, weightLabel(step, unit)))
            }
            if (!custom) DropdownMenuPopup(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                    options.forEachIndexed { index, value ->
                        SelectableDropdownMenuItem(selected = step == value,
                            text = { Text(weightLabel(value, unit)) }, shapes = MenuDefaults.itemShape(index, options.size),
                            onClick = { menu = false; onSelect(value) })
                    }
                }
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.detail_weight_step_custom)) },
                        shape = MenuDefaults.itemShape(0, 1).shape, onClick = { menu = false; custom = true })
                }
            }
        }
        if (custom) {
            var input by remember { mutableStateOf(step.toString()) }
            val value = enteredWeightStep(input)
            Column(Modifier.fillMaxWidth().testTag("weight-step-editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.detail_weight_step_input)) }, suffix = { Text(weightUnitLabel(unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = value == null,
                    supportingText = { Text(stringResource(R.string.detail_weight_step_help)) })
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { custom = false }) { Text(stringResource(R.string.editor_cancel)) }
                    TextButton(onClick = { value?.let(onSelect); custom = false }, enabled = value != null) {
                        Text(stringResource(R.string.detail_weight_step_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> NumberSnapPicker(values: List<T>, initial: Int, horizontal: Boolean,
    suffix: String? = null, minimumItemWidth: Dp = 112.dp, description: (@Composable (T) -> String)? = null,
    label: @Composable (T) -> String, onSelected: (T) -> Unit) {
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
        val itemWidth = (minimumItemWidth * density.fontScale).coerceAtMost(maxWidth - 24.dp)
        val padding = if (horizontal) PaddingValues(horizontal = (maxWidth - itemWidth) / 2)
            else PaddingValues(vertical = itemHeight * 2)
        val fling = rememberSnapFlingBehavior(list)
        val content: LazyListScope.() -> Unit = {
            itemsIndexed(values) { index, value ->
                val chosen = index == selected
                val semanticLabel = description?.invoke(value)
                val textSemantics = if (semanticLabel == null) Modifier else Modifier.clearAndSetSemantics {}
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
                        .semantics { if (semanticLabel != null) contentDescription = semanticLabel }
                        .selectable(chosen, role = Role.RadioButton, onClick = { scope.launch { list.animateScrollToItem(index) } })) {
                    Row(Modifier.wrapContentHeight(Alignment.CenterVertically),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(label(value), modifier = textSemantics.alignByBaseline(), style = when {
                            chosen && !horizontal -> MaterialTheme.typography.headlineLargeEmphasized
                            chosen -> MaterialTheme.typography.headlineSmallEmphasized
                            else -> MaterialTheme.typography.titleMedium
                        })
                        if ((horizontal || chosen) && suffix != null && value != null) Text(suffix, textSemantics.padding(start = 8.dp).alignByBaseline(),
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
