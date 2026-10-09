package com.example.fitlog.ai

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.data.ai.AiModelSelection
import com.example.fitlog.data.ai.AiProviderConnection
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogSettingsPage
import com.example.fitlog.ui.components.SettingsSectionTitle
import com.example.fitlog.ui.components.SettingsGroup
import com.example.fitlog.ui.components.SettingsFormCard

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
internal fun AiSettingsContent(state: AiSettingsUiState, actions: AiSettingsActions, onBack: () -> Unit) {
    var providerExpanded by rememberSaveable { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deepSeek = stringResource(R.string.ai_deepseek)
    val custom = stringResource(R.string.ai_custom_connection)

    FitLogSettingsPage(stringResource(R.string.ai_settings_title),
        onBack = { if (!state.saving) { actions.cancelRequest(); onBack() } }, backEnabled = !state.saving,
        loading = state.loading || state.saving || state.requestRunning,
        actions = {
            if (state.requestRunning) IconButton(onClick = actions.cancelRequest, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.close_24px), stringResource(R.string.ai_cancel_request))
            } else Spacer(Modifier.size(48.dp))
        }) {
        item {
            val active = state.currentSelection
            val activeProvider = state.providers.firstOrNull { it.id == active?.providerId }
            Text(if (!state.initialized) "" else if (active != null && activeProvider != null)
                stringResource(R.string.ai_active_selection, activeProvider.name, active.modelId)
                else stringResource(R.string.ai_no_active_selection),
                minLines = 1, style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.message?.let { message -> item {
            SettingsFormCard {
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                if (!state.initialized) TextButton(onClick = actions.reload, enabled = !state.loading) {
                    Text(stringResource(R.string.log_retry))
                }
            }
        } }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_connection_heading))
            SettingsGroup {
                Row(Modifier.fillMaxWidth().clickable(enabled = state.canEdit, role = Role.Button,
                    onClickLabel = stringResource(R.string.ai_choose_connection), onClick = { providerExpanded = true })
                    .padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ConnectionIcon(state.baseUrl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.name.ifBlank { stringResource(R.string.ai_new_connection) },
                            style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(stringResource(R.string.ai_editing_connection), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.chevron_right_24px), null, modifier = Modifier.rotate(90f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.name, actions.changeName, label = { Text(stringResource(R.string.ai_connection_name)) },
                        enabled = state.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (state.providers.any { it.id == state.providerId }) {
                        TextButton(onClick = { confirmDelete = true }, enabled = state.canEdit && !state.requestRunning) {
                            Text(stringResource(R.string.ai_delete_connection), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_credentials_heading))
            SettingsFormCard {
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
        item {
            SettingsSectionTitle(stringResource(R.string.ai_models_heading))
            SettingsFormCard {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            SettingsSectionTitle(stringResource(R.string.ai_connection_test))
            SettingsFormCard {
                Text(stringResource(R.string.ai_test_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = actions.test, enabled = state.canEdit && !state.requestRunning) {
                    Text(stringResource(R.string.ai_test_button))
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
    if (providerExpanded) ModalBottomSheet(onDismissRequest = { providerExpanded = false }) {
        ConnectionPicker(state.providers, state.providerId, state.canEdit,
            onChoose = { providerExpanded = false; actions.chooseProvider(it) },
            onDeepSeek = { providerExpanded = false; actions.newConnection(deepSeek, AiSettingsViewModel.DEEPSEEK_URL) },
            onCustom = { providerExpanded = false; actions.newConnection(custom, "") })
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.ai_delete_connection)) },
        text = { Text(stringResource(R.string.ai_delete_description)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete() },
            enabled = state.canEdit && !state.requestRunning) {
            Text(stringResource(R.string.ai_delete_connection))
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ai_cancel_request)) } })
}

@Composable
private fun ConnectionIcon(baseUrl: String) {
    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center) {
        if (baseUrl.trim().trimEnd('/') == AiSettingsViewModel.DEEPSEEK_URL)
            Image(painterResource(R.drawable.deepseek), null, Modifier.size(32.dp))
        else Icon(painterResource(R.drawable.auto_awesome_24px), null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
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
    LazyColumn(Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SettingsSectionTitle(stringResource(R.string.ai_choose_connection)) }
        if (connections.none { it.id == editingId }) item {
            Text(stringResource(R.string.ai_unsaved_connection), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(connections, key = { it.id }) { connection ->
            Row(Modifier.fillMaxWidth().selectable(selected = connection.id == editingId,
                enabled = enabled, role = Role.RadioButton, onClick = { onChoose(connection.id) })
                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConnectionIcon(connection.baseUrl)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(connection.name, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(pluralStringResource(R.plurals.ai_saved_connection_models, connection.modelIds.size, connection.modelIds.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = connection.id == editingId, onClick = null, enabled = enabled)
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_new_connection_heading))
            FilledTonalButton(onClick = onDeepSeek, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_add_deepseek))
            }
            OutlinedButton(onClick = onCustom, enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.ai_add_custom))
            }
        }
    }
}

internal data class AiSettingsUiState(
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

internal data class AiSettingsActions(
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
