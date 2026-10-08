package com.example.fitlog.vault

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.data.vault.DiaryDirectory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.vault.DiaryDateFormat
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogPageHeader
import com.example.fitlog.ui.components.FitLogSectionTitle
import java.time.LocalDate

@Composable
fun DiarySettingsScreen(vm: DiarySettingsViewModel, onBack: () -> Unit) {
    BackHandler { vm.requestBack(onBack) }
    LaunchedEffect(vm.completed) { if (vm.completed) onBack() }
    DiarySettingsContent(
        state = DiarySettingsUiState(
            format = vm.format,
            saving = vm.saving,
            canSave = vm.canSave,
            path = vm.path,
            loading = vm.loading,
            error = vm.error,
            children = vm.children,
        ),
        actions = DiarySettingsActions(
            requestBack = { vm.requestBack(it) },
            save = { vm.save() },
            chooseFormat = { vm.chooseFormat(it) },
            browse = { vm.browse(it) },
            retry = { vm.retry() },
        ),
        onBack = onBack,
    )
}

@Composable
private fun DiarySettingsContent(state: DiarySettingsUiState, actions: DiarySettingsActions, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            item {
                FitLogPageHeader(stringResource(R.string.diary_settings_title),
                    stringResource(R.string.diary_settings_description),
                    onBack = { actions.requestBack(onBack) }, backEnabled = !state.saving) {
                    Button(onClick = actions.save, enabled = state.canSave, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
                Spacer(Modifier.height(24.dp))
                FitLogSectionTitle(stringResource(R.string.diary_date_format))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    DiaryDateFormat.entries.forEachIndexed { index, format ->
                        val selected = state.format == format
                        Surface(
                            shape = ListItemDefaults.segmentedShapes(index, DiaryDateFormat.entries.size).shape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().selectable(selected, enabled = !state.saving,
                                role = Role.RadioButton, onClick = { actions.chooseFormat(format) }),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = selected, onClick = null, enabled = !state.saving)
                                Text(stringResource(when (format) {
                                    DiaryDateFormat.Dashed -> R.string.diary_format_dashed
                                    DiaryDateFormat.Compact -> R.string.diary_format_compact
                                    DiaryDateFormat.Chinese -> R.string.diary_format_chinese
                                }), style = if (selected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Text(stringResource(R.string.diary_filename_preview, state.format.fileName(LocalDate.now())),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
                FitLogSectionTitle(stringResource(R.string.diary_directory))
                Text(state.path.joinToString("/").ifEmpty { stringResource(R.string.diary_root) },
                    style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { actions.browse(emptyList()) }, enabled = !state.saving && !state.loading) {
                        Text(stringResource(R.string.diary_root))
                    }
                    OutlinedButton(onClick = { actions.browse(state.path.dropLast(1)) },
                        enabled = state.path.isNotEmpty() && !state.saving && !state.loading) {
                        Text(stringResource(R.string.diary_up))
                    }
                }
                if (state.loading || state.saving) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                state.error?.let {
                    FitLogNotice(stringResource(it), error = true)
                    OutlinedButton(onClick = actions.retry, enabled = !state.loading && !state.saving) {
                        Text(stringResource(R.string.log_retry))
                    }
                }
                if (!state.loading && state.error == null && state.children.isEmpty()) {
                    Text(stringResource(R.string.diary_no_subfolders), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            itemsIndexed(state.children, key = { _, it -> it.uri }) { index, directory ->
                SegmentedListItem(
                    onClick = { actions.browse(state.path + directory.name) }, enabled = !state.saving && !state.loading,
                    shapes = ListItemDefaults.segmentedShapes(index, state.children.size),
                    leadingContent = { Icon(painterResource(R.drawable.folder_open_24px), null) },
                ) { Text(directory.name) }
            }
        }
    }
}

private data class DiarySettingsUiState(
    val format: DiaryDateFormat = DiaryDateFormat.Dashed,
    val saving: Boolean = false,
    val canSave: Boolean = true,
    val path: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: Int? = null,
    val children: List<DiaryDirectory> = emptyList(),
)

private data class DiarySettingsActions(
    val requestBack: (() -> Unit) -> Unit = { it() },
    val save: () -> Unit = {},
    val chooseFormat: (DiaryDateFormat) -> Unit = {},
    val browse: (List<String>) -> Unit = {},
    val retry: () -> Unit = {},
)

@FitLogPreviews
@Composable
private fun DiarySettingsPreview() {
    val directory = DiaryDirectory("content://preview/document/training", stringResource(R.string.preview_directory_name))
    FitLogPreview {
        DiarySettingsContent(DiarySettingsUiState(children = listOf(directory)), DiarySettingsActions(), {})
    }
}
