package com.example.fitlog.ai

import com.example.fitlog.ui.components.FitLogWavyProgressIndicator

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.data.ai.AiModelSelection
import com.example.fitlog.data.ai.AiProviderConnection
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.fitlog.ui.components.FitLogPageHeader
import com.example.fitlog.ui.components.FitLogSectionTitle

/** Connection credentials and model selection remain separate groups with one explicit Save action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsScreen(vm: AiSettingsViewModel, onBack: () -> Unit) {
    BackHandler { if (!vm.saving) { vm.cancelRequest(); onBack() } }
    AiSettingsContent(
        state = AiSettingsUiState(
            saving = vm.saving,
            initialized = vm.initialized,
            currentSelection = vm.currentSelection,
            providers = vm.providers,
            loading = vm.loading,
            requestRunning = vm.requestRunning,
            message = vm.message,
            baseUrl = vm.baseUrl,
            name = vm.name,
            canEdit = vm.canEdit,
            providerId = vm.providerId,
            apiKey = vm.apiKey,
            showKey = vm.showKey,
            modelIds = vm.modelIds,
            selectedModel = vm.selectedModel,
            canSave = vm.canSave,
        ),
        actions = AiSettingsActions(
            cancelRequest = { vm.cancelRequest() },
            reload = { vm.reload() },
            toggleKey = { vm.toggleKey() },
            addModel = { vm.addModel() },
            removeModel = { vm.removeModel() },
            fetch = { vm.fetch() },
            test = { vm.test() },
            save = { vm.save() },
            delete = { vm.delete() },
            chooseProvider = { vm.chooseProvider(it) },
            changeName = { vm.changeName(it) },
            changeBaseUrl = { vm.changeBaseUrl(it) },
            changeKey = { vm.changeKey(it) },
            changeModel = { vm.changeModel(it) },
            newConnection = { name, url -> vm.newConnection(name, url) },
        ),
        onBack = onBack,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AiSettingsContent(state: AiSettingsUiState, actions: AiSettingsActions, onBack: () -> Unit) {
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deepSeek = stringResource(R.string.ai_deepseek)
    val custom = stringResource(R.string.ai_custom_connection)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                FitLogPageHeader(stringResource(R.string.ai_settings_title),
                    stringResource(R.string.ai_settings_description),
                    onBack = { if (!state.saving) { actions.cancelRequest(); onBack() } }, backEnabled = !state.saving)
                if (state.initialized) {
                    val active = state.currentSelection
                    val activeProvider = state.providers.firstOrNull { it.id == active?.providerId }
                    Text(
                        text = if (active != null && activeProvider != null)
                            stringResource(R.string.ai_active_selection, activeProvider.name, active.modelId)
                        else stringResource(R.string.ai_no_active_selection),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.loading || state.saving || state.requestRunning) FitLogWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            state.message?.let { message ->
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(message), Modifier.padding(16.dp))
                        if (!state.initialized) TextButton(onClick = actions.reload, enabled = !state.loading) {
                            Text(stringResource(R.string.log_retry))
                        }
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_connection_heading)
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.baseUrl.trim().trimEnd('/') == AiSettingsViewModel.DEEPSEEK_URL)
                                Image(painterResource(R.drawable.deepseek), contentDescription = null, Modifier.size(40.dp))
                            else Icon(painterResource(R.drawable.auto_awesome_24px), contentDescription = null,
                                modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(state.name.ifBlank { stringResource(R.string.ai_new_connection) },
                                style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                        ExposedDropdownMenuBox(expanded = providerExpanded,
                            onExpandedChange = { if (state.canEdit) providerExpanded = it }) {
                            OutlinedTextField(
                                value = state.providers.firstOrNull { it.id == state.providerId }?.name
                                    ?: stringResource(R.string.ai_new_connection),
                                onValueChange = {}, readOnly = true, enabled = state.canEdit,
                                label = { Text(stringResource(R.string.ai_connection_picker)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                                state.providers.forEach { connection ->
                                    DropdownMenuItem(text = { Text(connection.name) }, onClick = {
                                        providerExpanded = false; actions.chooseProvider(connection.id)
                                    })
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { actions.newConnection(deepSeek, AiSettingsViewModel.DEEPSEEK_URL) },
                                enabled = state.canEdit) { Text(stringResource(R.string.ai_add_deepseek)) }
                            OutlinedButton(onClick = { actions.newConnection(custom, "") }, enabled = state.canEdit) {
                                Text(stringResource(R.string.ai_add_custom))
                            }
                        }
                        OutlinedTextField(state.name, actions.changeName, label = { Text(stringResource(R.string.ai_connection_name)) },
                            enabled = state.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (state.providers.any { it.id == state.providerId }) {
                            TextButton(onClick = { confirmDelete = true }, enabled = state.canEdit && !state.requestRunning) {
                                Text(stringResource(R.string.ai_delete_connection))
                            }
                        }
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_credentials_heading)
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(state.baseUrl, actions.changeBaseUrl, label = { Text(stringResource(R.string.ai_base_url)) },
                            supportingText = { Text(stringResource(R.string.ai_base_url_help)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            enabled = state.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(state.apiKey, actions.changeKey, label = { Text(stringResource(R.string.ai_api_key)) },
                            visualTransformation = if (state.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = actions.toggleKey, enabled = state.canEdit) {
                                    Text(stringResource(if (state.showKey) R.string.ai_hide_key else R.string.ai_show_key))
                                }
                            }, enabled = state.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_models_heading)
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ai_models_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.ai_model_count, state.modelIds.size), style = MaterialTheme.typography.labelLarge)
                        ExposedDropdownMenuBox(expanded = modelExpanded,
                            onExpandedChange = { if (state.canEdit) modelExpanded = it }) {
                            OutlinedTextField(state.selectedModel, actions.changeModel,
                                label = { Text(stringResource(R.string.ai_current_model)) },
                                singleLine = true, enabled = state.canEdit,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
                            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                state.modelIds.forEach { id ->
                                    DropdownMenuItem(text = { Text(id) }, onClick = { modelExpanded = false; actions.changeModel(id) })
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = actions.addModel,
                                enabled = state.canEdit && state.selectedModel.isNotBlank() && state.selectedModel.trim() !in state.modelIds) {
                                Text(stringResource(R.string.ai_add_model))
                            }
                            TextButton(onClick = actions.removeModel, enabled = state.canEdit && state.selectedModel in state.modelIds) {
                                Text(stringResource(R.string.ai_remove_model))
                            }
                        }
                        OutlinedButton(onClick = actions.fetch, enabled = state.canEdit && !state.requestRunning) {
                            Text(stringResource(R.string.ai_fetch_models))
                        }
                    }
                }
            }
            item {
                AiSectionLabel(R.string.ai_connection_test)
                OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ai_test_description), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = actions.test, enabled = state.canEdit && !state.requestRunning) {
                                Text(stringResource(R.string.ai_test_button))
                            }
                            if (state.requestRunning) TextButton(onClick = actions.cancelRequest) {
                                Text(stringResource(R.string.ai_cancel_request))
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = actions.save, enabled = state.canSave,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                    Text(stringResource(R.string.ai_save_selection), style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.ai_delete_connection)) },
        text = { Text(stringResource(R.string.ai_delete_description)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete() }) {
            Text(stringResource(R.string.ai_delete_connection))
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ai_cancel_request)) } })
}

@Composable
private fun AiSectionLabel(@StringRes title: Int) {
    FitLogSectionTitle(stringResource(title))
}

private data class AiSettingsUiState(
    val saving: Boolean = false,
    val initialized: Boolean = true,
    val currentSelection: AiModelSelection? = null,
    val providers: List<AiProviderConnection> = emptyList(),
    val loading: Boolean = false,
    val requestRunning: Boolean = false,
    val message: Int? = null,
    val baseUrl: String = AiSettingsViewModel.DEEPSEEK_URL,
    val name: String = "",
    val canEdit: Boolean = true,
    val providerId: String = "",
    val apiKey: String = "",
    val showKey: Boolean = false,
    val modelIds: List<String> = emptyList(),
    val selectedModel: String = "",
    val canSave: Boolean = false,
)

private data class AiSettingsActions(
    val cancelRequest: () -> Unit = {},
    val reload: () -> Unit = {},
    val toggleKey: () -> Unit = {},
    val addModel: () -> Unit = {},
    val removeModel: () -> Unit = {},
    val fetch: () -> Unit = {},
    val test: () -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
    val chooseProvider: (String) -> Unit = {},
    val changeName: (String) -> Unit = {},
    val changeBaseUrl: (String) -> Unit = {},
    val changeKey: (String) -> Unit = {},
    val changeModel: (String) -> Unit = {},
    val newConnection: (String, String) -> Unit = { _, _ -> },
)

@FitLogPreviews
@Composable
private fun AiSettingsPreview() {
    val provider = AiProviderConnection("preview-provider", stringResource(R.string.ai_deepseek),
        AiSettingsViewModel.DEEPSEEK_URL, listOf("deepseek-chat", "deepseek-reasoner"))
    FitLogPreview {
        AiSettingsContent(AiSettingsUiState(providers = listOf(provider), providerId = provider.id,
            name = provider.name, apiKey = "preview-key", modelIds = provider.modelIds, selectedModel = "deepseek-chat",
            currentSelection = AiModelSelection(provider.id, "deepseek-chat"), canSave = true), AiSettingsActions(), {})
    }
}
