package com.example.fitlog.feature.settings

import com.example.fitlog.feature.settings.components.ConnectionIcon
import com.example.fitlog.feature.settings.components.ConnectionPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogBackGuard
import com.example.fitlog.ui.components.FitLogSettingsPage
import com.example.fitlog.ui.components.SettingsFormCard
import com.example.fitlog.ui.components.SettingsGroup
import com.example.fitlog.ui.components.SettingsSectionTitle
import com.example.fitlog.ui.theme.FitLogTheme

/** Connection credentials and model selection remain separate groups with one explicit Save action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsScreen(vm: AiSettingsViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    FitLogBackGuard(enabled = state.saving) { }
    AiSettingsContent(
        state = state,
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
    val spacing = FitLogTheme.spacing
    val deepSeek = stringResource(R.string.ai_deepseek)
    val custom = stringResource(R.string.ai_custom_connection)

    FitLogSettingsPage(stringResource(R.string.ai_settings_title),
        onBack = { if (!state.saving) { actions.cancelRequest(); onBack() } }, backEnabled = !state.saving,
        loading = state.loading || state.saving || state.requestRunning,
        actions = {
            if (state.requestRunning) IconButton(onClick = actions.cancelRequest, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.close_24px), stringResource(R.string.ai_cancel_request))
            } else Spacer(Modifier.minimumInteractiveComponentSize())
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
            // A status line, not a settings group: a low-emphasis outlined card on the page.
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                    if (!state.initialized) TextButton(onClick = actions.reload, enabled = !state.loading, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.log_retry))
                    }
                }
            }
        } }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_connection_heading))
            SettingsGroup {
                Row(Modifier.fillMaxWidth().clickable(enabled = state.canEdit, role = Role.Button,
                    onClickLabel = stringResource(R.string.ai_choose_connection), onClick = { providerExpanded = true })
                    .padding(spacing.l), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                    ConnectionIcon(state.baseUrl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(state.name.ifBlank { stringResource(R.string.ai_new_connection) },
                            style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(stringResource(R.string.ai_editing_connection), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(start = spacing.l, end = spacing.l, bottom = spacing.l),
                    verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    OutlinedTextField(state.name, actions.changeName, label = { Text(stringResource(R.string.ai_connection_name)) },
                        enabled = state.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (state.providers.any { it.id == state.providerId }) {
                        TextButton(onClick = { confirmDelete = true }, enabled = state.canEdit && !state.requestRunning,
                            shapes = ButtonDefaults.shapes(),) {
                            Text(stringResource(R.string.ai_delete_connection))
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
                        TextButton(onClick = actions.toggleKey, enabled = state.canEdit, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(if (state.showKey) R.string.ai_hide_key else R.string.ai_show_key))
                        }
                    }, enabled = state.canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_models_heading))
            SettingsFormCard {
                Text(stringResource(R.string.ai_models_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    FilledTonalButton(onClick = actions.addModel,
                        enabled = state.canEdit && state.selectedModel.isNotBlank() && state.selectedModel.trim() !in state.modelIds, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.ai_add_model))
                    }
                    TextButton(onClick = actions.removeModel, enabled = state.canEdit && state.selectedModel in state.modelIds, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.ai_remove_model))
                    }
                }
                OutlinedButton(onClick = actions.fetch, enabled = state.canEdit && !state.requestRunning, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.ai_fetch_models))
                }
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.ai_connection_test))
            SettingsFormCard {
                Text(stringResource(R.string.ai_test_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = actions.test, enabled = state.canEdit && !state.requestRunning, shapes = ButtonDefaults.shapes()) {
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
            enabled = state.canEdit && !state.requestRunning, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.ai_delete_connection))
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ai_cancel_request)) } })
}

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
