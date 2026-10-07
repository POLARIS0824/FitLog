package com.example.fitlog.ai

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fitlog.R

/** Connection credentials and model selection remain separate groups with one explicit Save action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsScreen(vm: AiSettingsViewModel, onBack: () -> Unit) {
    BackHandler { if (!vm.saving) { vm.cancelRequest(); onBack() } }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deepSeek = stringResource(R.string.ai_deepseek)
    val custom = stringResource(R.string.ai_custom_connection)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                IconButton(onClick = { if (!vm.saving) { vm.cancelRequest(); onBack() } }, enabled = !vm.saving) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                }
                Text(stringResource(R.string.ai_settings_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.ai_settings_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.initialized) {
                    val active = vm.currentSelection
                    val activeProvider = vm.providers.firstOrNull { it.id == active?.providerId }
                    Text(
                        text = if (active != null && activeProvider != null)
                            stringResource(R.string.ai_active_selection, activeProvider.name, active.modelId)
                        else stringResource(R.string.ai_no_active_selection),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (vm.loading || vm.saving || vm.requestRunning) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            vm.message?.let { message ->
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(message), Modifier.padding(16.dp))
                        if (!vm.initialized) TextButton(onClick = vm::reload, enabled = !vm.loading) {
                            Text(stringResource(R.string.log_retry))
                        }
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_connection_heading)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (vm.baseUrl.trim().trimEnd('/') == AiSettingsViewModel.DEEPSEEK_URL)
                                Image(painterResource(R.drawable.deepseek), contentDescription = null, Modifier.size(40.dp))
                            else Icon(painterResource(R.drawable.auto_awesome_24px), contentDescription = null,
                                modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(vm.name.ifBlank { stringResource(R.string.ai_new_connection) },
                                style = MaterialTheme.typography.titleMedium)
                        }
                        ExposedDropdownMenuBox(expanded = providerExpanded,
                            onExpandedChange = { if (vm.canEdit) providerExpanded = it }) {
                            OutlinedTextField(
                                value = vm.providers.firstOrNull { it.id == vm.providerId }?.name
                                    ?: stringResource(R.string.ai_new_connection),
                                onValueChange = {}, readOnly = true, enabled = vm.canEdit,
                                label = { Text(stringResource(R.string.ai_connection_picker)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                                vm.providers.forEach { connection ->
                                    DropdownMenuItem(text = { Text(connection.name) }, onClick = {
                                        providerExpanded = false; vm.chooseProvider(connection.id)
                                    })
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { vm.newConnection(deepSeek, AiSettingsViewModel.DEEPSEEK_URL) },
                                enabled = vm.canEdit) { Text(stringResource(R.string.ai_add_deepseek)) }
                            OutlinedButton(onClick = { vm.newConnection(custom) }, enabled = vm.canEdit) {
                                Text(stringResource(R.string.ai_add_custom))
                            }
                        }
                        OutlinedTextField(vm.name, vm::changeName, label = { Text(stringResource(R.string.ai_connection_name)) },
                            enabled = vm.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (vm.providers.any { it.id == vm.providerId }) {
                            TextButton(onClick = { confirmDelete = true }, enabled = vm.canEdit && !vm.requestRunning) {
                                Text(stringResource(R.string.ai_delete_connection))
                            }
                        }
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_credentials_heading)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(vm.baseUrl, vm::changeBaseUrl, label = { Text(stringResource(R.string.ai_base_url)) },
                            supportingText = { Text(stringResource(R.string.ai_base_url_help)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            enabled = vm.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(vm.apiKey, vm::changeKey, label = { Text(stringResource(R.string.ai_api_key)) },
                            visualTransformation = if (vm.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = vm::toggleKey, enabled = vm.canEdit) {
                                    Text(stringResource(if (vm.showKey) R.string.ai_hide_key else R.string.ai_show_key))
                                }
                            }, enabled = vm.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_models_heading)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ai_models_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.ai_model_count, vm.modelIds.size), style = MaterialTheme.typography.labelLarge)
                        ExposedDropdownMenuBox(expanded = modelExpanded,
                            onExpandedChange = { if (vm.canEdit) modelExpanded = it }) {
                            OutlinedTextField(vm.selectedModel, vm::changeModel,
                                label = { Text(stringResource(R.string.ai_current_model)) },
                                singleLine = true, enabled = vm.canEdit,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
                            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                vm.modelIds.forEach { id ->
                                    DropdownMenuItem(text = { Text(id) }, onClick = { modelExpanded = false; vm.changeModel(id) })
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = vm::addModel,
                                enabled = vm.canEdit && vm.selectedModel.isNotBlank() && vm.selectedModel.trim() !in vm.modelIds) {
                                Text(stringResource(R.string.ai_add_model))
                            }
                            TextButton(onClick = vm::removeModel, enabled = vm.canEdit && vm.selectedModel in vm.modelIds) {
                                Text(stringResource(R.string.ai_remove_model))
                            }
                        }
                        OutlinedButton(onClick = vm::fetch, enabled = vm.canEdit && !vm.requestRunning) {
                            Text(stringResource(R.string.ai_fetch_models))
                        }
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_connection_test)
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ai_test_description), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::test, enabled = vm.canEdit && !vm.requestRunning) {
                                Text(stringResource(R.string.ai_test_button))
                            }
                            if (vm.requestRunning) TextButton(onClick = vm::cancelRequest) {
                                Text(stringResource(R.string.ai_cancel_request))
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = vm::save, enabled = vm.canSave,
                    shapes = ButtonDefaults.shapesFor(56.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.ai_save_selection))
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.ai_delete_connection)) },
        text = { Text(stringResource(R.string.ai_delete_description)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) {
            Text(stringResource(R.string.ai_delete_connection))
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ai_cancel_request)) } })
}

@Composable
private fun AiSectionLabel(@StringRes title: Int) {
    Text(stringResource(title), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
