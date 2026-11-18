package com.example.fitlog.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.GlassLookPicker
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSettingsPage
import com.example.fitlog.ui.components.SettingsGroup
import com.example.fitlog.ui.components.SettingsSectionTitle
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun AppearanceScreen(vm: AppearanceViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    AppearanceContent(state.preferences, state.canEdit, state.loading || state.saving, state.message,
        vm::changeThemeMode, vm::changeDynamicColor, vm::reload, onBack,
        onLook = vm::changeLook, onLiquidGlass = vm::changeLiquidGlass)
}

@Composable
internal fun AppearanceContent(
    preferences: AppearancePreferences,
    canEdit: Boolean,
    busy: Boolean,
    message: Int?,
    onMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    onLook: (UiLook) -> Unit = {},
    onLiquidGlass: (Boolean) -> Unit = {},
) {
    FitLogSettingsPage(stringResource(R.string.appearance_title), onBack, loading = busy) {
        // The error notice is its own card on the page; wrapping it in a group would nest equal corners.
        message?.let { item {
            Column(verticalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.s)) {
                FitLogNotice(stringResource(it), error = true)
                if (!canEdit) TextButton(onClick = onRetry, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.log_retry))
                }
            }
        } }
        item {
            SettingsSectionTitle(stringResource(R.string.look_heading))
            GlassLookPicker(preferences.look, canEdit, onLook)
        }
        if (preferences.look != UiLook.CLASSIC) item {
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.look_effect_title),
                    description = stringResource(if (dynamicColorSupported) R.string.look_effect_description
                        else R.string.look_effect_unsupported),
                    checked = preferences.liquidGlass,
                    enabled = canEdit && dynamicColorSupported,
                    onCheckedChange = onLiquidGlass,
                )
            }
        }
        if (preferences.look != UiLook.LIQUID_GLASS) item {
            SettingsSectionTitle(stringResource(R.string.appearance_theme_heading))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    val selected = preferences.themeMode == mode
                    SegmentedListItem(
                        selected = selected,
                        onClick = { onMode(mode) },
                        shapes = ListItemDefaults.segmentedShapes(index, ThemeMode.entries.size),
                        enabled = canEdit,
                        colors = FitLogContainment.pageListItemColors(),
                        leadingContent = {
                            RadioButton(selected = selected, onClick = null, enabled = canEdit,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
                        },
                    ) {
                        Text(themeModeLabel(mode), style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                            else MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (preferences.look != UiLook.LIQUID_GLASS) item {
            SettingsSectionTitle(stringResource(R.string.appearance_colors_heading))
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_dynamic_color),
                    description = stringResource(if (dynamicColorSupported) R.string.appearance_dynamic_description
                        else R.string.appearance_dynamic_unsupported),
                    checked = preferences.dynamicColor,
                    enabled = canEdit && dynamicColorSupported,
                    onCheckedChange = onDynamicColor,
                )
            }
        }
    }
}

/** One on/off setting; the whole row toggles, and the switch only shows state. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = FitLogTheme.spacing
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch,
        onValueChange = onCheckedChange).padding(spacing.l), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
