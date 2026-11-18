package com.example.fitlog.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.GlassColors

/** Single-select list of UI looks, built like the theme-mode list: a segmented list group. */
@Composable
internal fun GlassLookPicker(selected: UiLook, enabled: Boolean, onSelect: (UiLook) -> Unit) {
    val dark = GlassColors.isDark(MaterialTheme.colorScheme.surface)
    val glass = FitLogTheme.dimens.glass
    val spacing = FitLogTheme.spacing
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        UiLook.entries.forEachIndexed { index, look ->
            val checked = selected == look
            val liquid = look != UiLook.CLASSIC
            val fullLook = look == UiLook.LIQUID_GLASS
            SegmentedListItem(
                selected = checked,
                onClick = { onSelect(look) },
                shapes = ListItemDefaults.segmentedShapes(index, UiLook.entries.size),
                enabled = enabled,
                colors = FitLogContainment.pageListItemColors(),
                leadingContent = {
                    val gradient = if (fullLook) GlassColors.lookPreviewGradient(dark)
                        else listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest)
                    // The preview thumbnail sits inside a list item: a smaller, nested corner.
                    Box(Modifier.size(glass.lookPreviewSize).clip(MaterialTheme.shapes.small).background(Brush.linearGradient(gradient)),
                        contentAlignment = Alignment.BottomCenter) {
                        val pillBg = if (liquid) GlassColors.lookPreviewBar(dark) else MaterialTheme.colorScheme.secondaryContainer
                        val dotBg = if (liquid) GlassColors.lookPreviewDot(dark, MaterialTheme.colorScheme.primary)
                            else MaterialTheme.colorScheme.primary
                        Row(Modifier.padding(spacing.s).fillMaxWidth().height(glass.lookPreviewBarHeight)
                            .background(pillBg, CircleShape).padding(glass.lookPreviewBarPadding),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(3) { Box(Modifier.size(glass.lookPreviewDotSize).background(dotBg, CircleShape)) }
                        }
                    }
                },
                supportingContent = {
                    Text(stringResource(when (look) {
                        UiLook.CLASSIC -> R.string.look_classic_description
                        UiLook.LIQUID_GLASS -> R.string.look_liquid_description
                        UiLook.GLASS_NAVIGATION -> R.string.look_glass_navigation_description
                    }), style = MaterialTheme.typography.bodySmall)
                },
                trailingContent = {
                    RadioButton(checked, onClick = null, enabled = enabled,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
                },
            ) {
                Text(stringResource(when (look) {
                    UiLook.CLASSIC -> R.string.look_classic
                    UiLook.LIQUID_GLASS -> R.string.look_liquid
                    UiLook.GLASS_NAVIGATION -> R.string.look_glass_navigation
                }), style = if (checked) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium)
            }
        }
    }
}
