package com.example.fitlog.feature.settings.components

import com.example.fitlog.feature.settings.AiSettingsViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.data.ai.AiProviderConnection
import com.example.fitlog.ui.components.FitLogSheetDefaults
import com.example.fitlog.ui.components.FitLogSheetTitle
import com.example.fitlog.ui.components.SettingsSectionTitle
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun ConnectionIcon(baseUrl: String) {
    val dimens = FitLogTheme.dimens.settings
    // A neutral tile: secondaryContainer is the selected-row fill in the picker and would hide it.
    Box(Modifier.size(dimens.connectionIconContainer).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center) {
        if (baseUrl.trim().trimEnd('/') == AiSettingsViewModel.DEEPSEEK_URL)
            Image(painterResource(R.drawable.deepseek), null, Modifier.size(dimens.connectionLogoSize))
        else Icon(painterResource(R.drawable.auto_awesome_24px), null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Saved connections remain identities, rather than a fixed list of provider protocol types. */
@Composable
internal fun ConnectionPicker(
    connections: List<AiProviderConnection>,
    editingId: String,
    enabled: Boolean,
    onChoose: (String) -> Unit,
    onDeepSeek: () -> Unit,
    onCustom: () -> Unit,
) {
    val spacing = FitLogTheme.spacing
    LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = FitLogSheetDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        item { FitLogSheetTitle(stringResource(R.string.ai_choose_connection)) }
        if (connections.none { it.id == editingId }) item {
            Text(stringResource(R.string.ai_unsaved_connection), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connections.isNotEmpty()) item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                connections.forEachIndexed { index, connection ->
                    key(connection.id) {
                        val selected = connection.id == editingId
                        SegmentedListItem(
                            selected = selected,
                            onClick = { onChoose(connection.id) },
                            shapes = ListItemDefaults.segmentedShapes(index, connections.size),
                            enabled = enabled,
                            colors = FitLogContainment.sheetListItemColors(),
                            leadingContent = { ConnectionIcon(connection.baseUrl) },
                            trailingContent = {
                                RadioButton(selected = selected, onClick = null, enabled = enabled,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
                            },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.ai_saved_connection_models,
                                    connection.modelIds.size, connection.modelIds.size))
                            },
                        ) {
                            Text(connection.name, style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                                else MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_new_connection_heading))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                FilledTonalButton(onClick = onDeepSeek, enabled = enabled, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.ai_add_deepseek))
                }
                OutlinedButton(onClick = onCustom, enabled = enabled, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.ai_add_custom))
                }
            }
        }
    }
}
