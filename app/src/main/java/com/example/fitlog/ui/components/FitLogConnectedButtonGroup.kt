package com.example.fitlog.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.GlassSegmentedControl

/** Single selection with measured labels; narrow layouts scroll instead of truncating choices. */
@Composable
internal fun <T> FitLogConnectedButtonGroup(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominentSelection: Boolean = false,
    canSelect: (T) -> Boolean = { true },
) {
    if (choices.isEmpty()) return
    val labels = choices.map { label(it) }
    if (LocalUiLook.current == UiLook.LIQUID_GLASS) {
        GlassSegmentedControl(choices, selected, labels, onSelect, modifier, enabled, canSelect)
        return
    }
    val selectedStyle = MaterialTheme.typography.labelLargeEmphasized
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelPadding = 16.dp
    val minimumWidth = labels.maxOf {
        with(density) { measurer.measure(it, selectedStyle, maxLines = 1).size.width.toDp() }
    } + labelPadding * 2
    val colors = FilledTonalToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = if (prominentSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = if (prominentSelection) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = ButtonGroupDefaults.ConnectedSpaceBetween
        val width = maxOf(minimumWidth, (maxWidth - spacing * (choices.size - 1)) / choices.size)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing)) {
            choices.forEachIndexed { index, choice ->
                FilledTonalToggleButton(checked = choice == selected, onCheckedChange = { onSelect(choice) },
                    enabled = enabled && canSelect(choice), colors = colors,
                    modifier = Modifier.width(width).heightIn(min = ButtonDefaults.MinHeight).semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }) {
                    Text(labels[index], style = if (choice == selected) selectedStyle else MaterialTheme.typography.labelLarge,
                        maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
