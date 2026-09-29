package com.example.fitlog.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.vault.DiaryDateFormat
import java.time.LocalDate

@Composable
fun DiarySettingsScreen(vm: DiarySettingsViewModel, onBack: () -> Unit) {
    BackHandler { vm.requestBack(onBack) }
    LaunchedEffect(vm.completed) { if (vm.completed) onBack() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.requestBack(onBack) }, enabled = !vm.saving) { Text(stringResource(R.string.cd_back)) }
                Button(onClick = vm::save, enabled = vm.canSave) { Text(stringResource(R.string.editor_save)) }
            }
            Text(stringResource(R.string.diary_settings_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.diary_settings_description))
        }
        item {
            Text(stringResource(R.string.diary_date_format), style = MaterialTheme.typography.titleMedium)
            DiaryDateFormat.entries.forEach { format ->
                Row {
                    RadioButton(selected = vm.format == format, onClick = { vm.chooseFormat(format) }, enabled = !vm.saving)
                    TextButton(onClick = { vm.chooseFormat(format) }, enabled = !vm.saving) {
                        Text(stringResource(when (format) {
                            DiaryDateFormat.Dashed -> R.string.diary_format_dashed
                            DiaryDateFormat.Compact -> R.string.diary_format_compact
                            DiaryDateFormat.Chinese -> R.string.diary_format_chinese
                        }))
                    }
                }
            }
            Text(stringResource(R.string.diary_filename_preview, vm.format.fileName(LocalDate.now())))
        }
        item {
            Text(stringResource(R.string.diary_directory), style = MaterialTheme.typography.titleMedium)
            Text(vm.path.joinToString("/").ifEmpty { stringResource(R.string.diary_root) })
            Row {
                TextButton(onClick = { vm.browse(emptyList()) }, enabled = !vm.saving && !vm.loading) { Text(stringResource(R.string.diary_root)) }
                TextButton(onClick = { vm.browse(vm.path.dropLast(1)) }, enabled = vm.path.isNotEmpty() && !vm.saving && !vm.loading) { Text(stringResource(R.string.diary_up)) }
            }
            if (vm.loading || vm.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::retry, enabled = !vm.loading && !vm.saving) { Text(stringResource(R.string.log_retry)) }
            }
            if (!vm.loading && vm.error == null && vm.children.isEmpty()) Text(stringResource(R.string.diary_no_subfolders))
        }
        items(vm.children, key = { it.uri }) { directory ->
            ListItem(onClick = { vm.browse(vm.path + directory.name) }, enabled = !vm.saving && !vm.loading,
                content = { Text(directory.name) })
        }
    }
}
