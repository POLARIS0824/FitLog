package com.example.fitlog.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.ui.components.*
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews

@Composable
internal fun AppearanceScreen(vm: AppearanceViewModel, onBack: () -> Unit) {
    AppearanceContent(vm.preferences, vm.canEdit, vm.loading || vm.saving, vm.message,
        vm::changeThemeMode, vm::changeDynamicColor, vm::reload, onBack)
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
) {
    FitLogSettingsPage(stringResource(R.string.appearance_title), onBack, loading = busy) {
        message?.let { item {
            SettingsFormCard {
                FitLogNotice(stringResource(it), error = true)
                if (!canEdit) TextButton(onClick = onRetry, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.log_retry))
                }
            }
        } }
        item {
            SettingsSectionTitle(stringResource(R.string.appearance_theme_heading))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = preferences.themeMode == mode
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).selectable(selected = selected,
                            enabled = canEdit, role = Role.RadioButton, onClick = { onMode(mode) })
                            .padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RadioButton(selected = selected, onClick = null, enabled = canEdit,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
                            Text(themeModeLabel(mode), style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                                else MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.appearance_colors_heading))
            SettingsGroup {
                Row(Modifier.fillMaxWidth().toggleable(value = preferences.dynamicColor,
                    enabled = canEdit && dynamicColorSupported, role = Role.Switch, onValueChange = onDynamicColor)
                    .padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.appearance_dynamic_color), style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(stringResource(if (dynamicColorSupported) R.string.appearance_dynamic_description else R.string.appearance_dynamic_unsupported),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = preferences.dynamicColor, onCheckedChange = null,
                        enabled = canEdit && dynamicColorSupported)
                }
            }
        }
    }
}

@FitLogPreviews
@Composable
private fun AppearancePreview() {
    FitLogPreview { AppearanceContent(AppearancePreferences(), true, false, null, {}, {}, {}, {}) }
}
