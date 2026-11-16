package com.example.fitlog.feature.settings.components

import com.example.fitlog.feature.settings.AiSettingsViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.ai.AiProviderConnection
import com.example.fitlog.ui.components.FitLogSheetDefaults
import com.example.fitlog.ui.components.FitLogSheetTitle
import com.example.fitlog.ui.components.SettingsSectionTitle

@Composable
internal fun ConnectionIcon(baseUrl: String) {
    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center) {
        if (baseUrl.trim().trimEnd('/') == AiSettingsViewModel.DEEPSEEK_URL)
            Image(painterResource(R.drawable.deepseek), null, Modifier.size(32.dp))
        else Icon(painterResource(R.drawable.auto_awesome_24px), null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
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
    LazyColumn(Modifier.fillMaxWidth().selectableGroup().navigationBarsPadding(),
        contentPadding = FitLogSheetDefaults.ContentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FitLogSheetTitle(stringResource(R.string.ai_choose_connection)) }
        if (connections.none { it.id == editingId }) item {
            Text(stringResource(R.string.ai_unsaved_connection), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(connections, key = { it.id }) { connection ->
            val selected = connection.id == editingId
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).selectable(selected = selected,
                    enabled = enabled, role = Role.RadioButton, onClick = { onChoose(connection.id) })
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ConnectionIcon(connection.baseUrl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(connection.name, style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                            else MaterialTheme.typography.titleMedium)
                        Text(pluralStringResource(R.plurals.ai_saved_connection_models, connection.modelIds.size, connection.modelIds.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = selected, onClick = null, enabled = enabled,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
                }
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_new_connection_heading))
            FilledTonalButton(onClick = onDeepSeek, enabled = enabled, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.ai_add_deepseek))
            }
            OutlinedButton(onClick = onCustom, enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.ai_add_custom))
            }
        }
    }
}
