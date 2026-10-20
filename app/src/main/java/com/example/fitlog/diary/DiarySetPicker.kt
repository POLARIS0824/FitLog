package com.example.fitlog.diary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.WeightUnit
import com.example.fitlog.data.analysis.WeightBasis
import com.example.fitlog.ui.preview.FitLogPreview
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.math.abs

internal enum class DiarySetField { WEIGHT, REPS, BASIS }
internal data class DiarySetEdit(val address: DiarySetAddress, val number: Int, val field: DiarySetField,
    val weight: Double?, val unit: WeightUnit?, val reps: Int?, val basis: WeightBasis? = null)

internal fun repOptions(current: Int?): List<Int?> = listOf(null) +
    ((1..100).toList() + listOfNotNull(current)).distinct().sorted()
internal fun enteredReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiarySetPicker(edit: DiarySetEdit, onDismiss: () -> Unit, onWeight: (DiaryWeightValue) -> Unit, onReps: (Int?) -> Unit,
    onBasis: (WeightBasis, Boolean) -> Unit = { _, _ -> }) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        DiarySetPickerContent(edit, onDismiss, onWeight, onReps, onBasis)
    }
}

@Composable
internal fun DiarySetPickerContent(edit: DiarySetEdit, onDismiss: () -> Unit,
    onWeight: (DiaryWeightValue) -> Unit, onReps: (Int?) -> Unit, onBasis: (WeightBasis, Boolean) -> Unit = { _, _ -> }) {
    if (edit.field == DiarySetField.BASIS) {
        DiaryBasisPickerContent(edit, onDismiss, onBasis)
        return
    }
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
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
                ConnectedWeightButtons(listOf(WeightUnit.KG, WeightUnit.LB, WeightUnit.UNKNOWN), unit,
                    label = { if (it == WeightUnit.UNKNOWN) stringResource(R.string.detail_keep_unknown) else weightUnitLabel(it) },
                    enabled = !exactInput || validInput,
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
        PickerActions(onDismiss,
            enabled = if (edit.field == DiarySetField.WEIGHT) type != DiaryWeightType.NUMERIC ||
                (if (exactInput) enteredWeight(input) != null else weight != null)
                else !exactInput || validInput,
            onConfirm = {
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
            })
    }
}

@Composable
private fun DiaryBasisPickerContent(edit: DiarySetEdit, onDismiss: () -> Unit, onBasis: (WeightBasis, Boolean) -> Unit) {
    var basis by remember(edit) { mutableStateOf(edit.basis ?: WeightBasis.UNKNOWN) }
    var applyToExercise by remember(edit) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
        .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.detail_picker_title, edit.number, stringResource(R.string.detail_basis)),
            style = MaterialTheme.typography.titleLargeEmphasized)
        Text(stringResource(R.string.detail_basis_question, weightLabel(edit.weight, edit.unit, basis = edit.basis)))
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightBasis.entries.forEach { choice -> FilterChip(selected = basis == choice, onClick = { basis = choice },
                modifier = Modifier.semantics { role = Role.RadioButton },
                label = { Text(stringResource(if (choice == WeightBasis.UNKNOWN) R.string.detail_keep_unknown else basisLabel(choice))) }) }
        }
        Text(stringResource(when (basis) {
            WeightBasis.PER_SIDE -> R.string.detail_basis_per_side_help
            WeightBasis.TOTAL -> R.string.detail_basis_total_help
            WeightBasis.BODYWEIGHT -> R.string.detail_basis_bodyweight_help
            WeightBasis.ADDED -> R.string.detail_basis_added_help
            WeightBasis.ASSISTED -> R.string.detail_basis_assisted_help
            WeightBasis.UNKNOWN -> R.string.detail_basis_unknown_help
        }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().toggleable(applyToExercise, role = Role.Checkbox,
            onValueChange = { applyToExercise = it }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(applyToExercise, onCheckedChange = null)
            Text(stringResource(R.string.detail_basis_apply_exercise), Modifier.weight(1f))
        }
        PickerActions(onDismiss, onConfirm = { onBasis(basis, applyToExercise) })
    }
}

@Composable
private fun PickerActions(onDismiss: () -> Unit, enabled: Boolean = true, onConfirm: () -> Unit) {
    val cancelLabel = stringResource(R.string.record_cancel)
    val doneLabel = stringResource(R.string.detail_picker_done)
    val style = MaterialTheme.typography.labelLargeEmphasized
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minimumWidth = with(density) {
        (measurer.measure(cancelLabel, style, maxLines = 1).size.width +
            measurer.measure(doneLabel, style, maxLines = 1).size.width).toDp()
    } + 76.dp
    val cancel: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(onClick = onDismiss, modifier = modifier.heightIn(min = 48.dp), shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Text(cancelLabel, style = style, maxLines = 1, softWrap = false)
        }
    }
    val done: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onConfirm, enabled = enabled,
            modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Text(doneLabel, style = style, maxLines = 1, softWrap = false)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < minimumWidth) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            done(Modifier.fillMaxWidth()); cancel(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            cancel(Modifier); done(Modifier.weight(1f))
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
    val minimumWidths = labels.map { label ->
        maxOf(48.dp, with(density) { measurer.measure(label, textStyle, maxLines = 1).size.width.toDp() } + 32.dp)
    }
    val colors = FilledTonalToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = ButtonGroupDefaults.ConnectedSpaceBetween
        val requiredWidth = minimumWidths.fold(0.dp) { sum, width -> sum + width } + spacing * (choices.size - 1)
        val extraWidth = (maxWidth - requiredWidth).coerceAtLeast(0.dp) / choices.size
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing)) {
            choices.forEachIndexed { index, choice ->
                FilledTonalToggleButton(checked = selected == choice, onCheckedChange = { onSelect(choice) },
                    enabled = enabled && canSelect(choice),
                    modifier = Modifier.width(minimumWidths[index] + extraWidth).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), colors = colors,
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }) { Text(labels[index], style = textStyle, maxLines = 1, softWrap = false) }
            }
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
                    TextButton(onClick = { custom = false }) { Text(stringResource(R.string.record_cancel)) }
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
                    chosen -> MaterialTheme.colorScheme.secondaryContainer
                    horizontal -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                    MaterialTheme.motionScheme.fastEffectsSpec(), label = "pickerColor")
                Surface(shape = CircleShape, color = color,
                    contentColor = when {
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

@Preview(name = "Weight picker", widthDp = 412, heightDp = 800, locale = "en")
@Composable
private fun DiaryWeightPickerReviewPreview() {
    FitLogPreview {
        DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 0), 1, DiarySetField.WEIGHT,
            weight = 60.0, unit = WeightUnit.KG, reps = 8, basis = WeightBasis.TOTAL),
            onDismiss = {}, onWeight = {}, onReps = {})
    }
}

@Preview(name = "Weight picker · Large text", widthDp = 320, heightDp = 1400, fontScale = 2f, locale = "zh")
@Composable
private fun DiaryWeightPickerLargeTextReviewPreview() {
    DiaryWeightPickerReviewPreview()
}

@Preview(name = "Reps picker", widthDp = 412, heightDp = 800, locale = "en")
@Composable
private fun DiaryRepsPickerReviewPreview() {
    FitLogPreview {
        DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 0), 1, DiarySetField.REPS,
            weight = 60.0, unit = WeightUnit.KG, reps = 8, basis = WeightBasis.TOTAL),
            onDismiss = {}, onWeight = {}, onReps = {})
    }
}

@Preview(name = "Reps picker · Large text", widthDp = 320, heightDp = 1400, fontScale = 2f, locale = "zh")
@Composable
private fun DiaryRepsPickerLargeTextReviewPreview() {
    DiaryRepsPickerReviewPreview()
}
