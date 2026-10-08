package com.example.fitlog.vault

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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            item {
                FitLogPageHeader(stringResource(R.string.diary_settings_title),
                    stringResource(R.string.diary_settings_description),
                    onBack = { vm.requestBack(onBack) }, backEnabled = !vm.saving) {
                    Button(onClick = vm::save, enabled = vm.canSave, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
                Spacer(Modifier.height(24.dp))
                FitLogSectionTitle(stringResource(R.string.diary_date_format))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    DiaryDateFormat.entries.forEachIndexed { index, format ->
                        val selected = vm.format == format
                        Surface(
                            shape = ListItemDefaults.segmentedShapes(index, DiaryDateFormat.entries.size).shape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().selectable(selected, enabled = !vm.saving,
                                role = Role.RadioButton, onClick = { vm.chooseFormat(format) }),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = selected, onClick = null, enabled = !vm.saving)
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
                Text(stringResource(R.string.diary_filename_preview, vm.format.fileName(LocalDate.now())),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
                FitLogSectionTitle(stringResource(R.string.diary_directory))
                Text(vm.path.joinToString("/").ifEmpty { stringResource(R.string.diary_root) },
                    style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.browse(emptyList()) }, enabled = !vm.saving && !vm.loading) {
                        Text(stringResource(R.string.diary_root))
                    }
                    OutlinedButton(onClick = { vm.browse(vm.path.dropLast(1)) },
                        enabled = vm.path.isNotEmpty() && !vm.saving && !vm.loading) {
                        Text(stringResource(R.string.diary_up))
                    }
                }
                if (vm.loading || vm.saving) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                vm.error?.let {
                    FitLogNotice(stringResource(it), error = true)
                    OutlinedButton(onClick = vm::retry, enabled = !vm.loading && !vm.saving) {
                        Text(stringResource(R.string.log_retry))
                    }
                }
                if (!vm.loading && vm.error == null && vm.children.isEmpty()) {
                    Text(stringResource(R.string.diary_no_subfolders), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            itemsIndexed(vm.children, key = { _, it -> it.uri }) { index, directory ->
                SegmentedListItem(
                    onClick = { vm.browse(vm.path + directory.name) }, enabled = !vm.saving && !vm.loading,
                    shapes = ListItemDefaults.segmentedShapes(index, vm.children.size),
                    leadingContent = { Icon(painterResource(R.drawable.folder_open_24px), null) },
                ) { Text(directory.name) }
            }
        }
    }
}
