package com.example.fitlog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.ai.AiConfigurationException
import com.example.fitlog.data.ai.AiModelSelection
import com.example.fitlog.data.ai.AiOperations
import com.example.fitlog.data.ai.AiProviderConnection
import com.example.fitlog.data.ai.AiProviders
import com.example.fitlog.data.ai.transport.AiModelsResult
import com.example.fitlog.data.ai.transport.openAiEndpoint
import com.example.fitlog.model.ai.AiConfigurationFailure
import com.example.fitlog.ui.text.aiConfigurationMessage
import com.example.fitlog.ui.text.aiRequestMessage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Form state survives Activity recreation; plaintext keys are never put in SavedStateHandle. */
internal class AiSettingsViewModel(
    private val repository: AiProviders,
    private val defaultName: String,
    private val operations: AiOperations,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AiSettingsUiState(
            providerId = UUID.randomUUID().toString(),
            name = defaultName,
            loading = true,
            initialized = false,
        ).withDerivedValues()
    )
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    private inline fun updateState(transform: (AiSettingsUiState) -> AiSettingsUiState) {
        _uiState.update { transform(it).withDerivedValues() }
    }

    private val providers get() = _uiState.value.providers
    private val currentSelection get() = _uiState.value.currentSelection
    private val providerId get() = _uiState.value.providerId
    private val name get() = _uiState.value.name
    private val baseUrl get() = _uiState.value.baseUrl
    private val apiKey get() = _uiState.value.apiKey
    private val showKey get() = _uiState.value.showKey
    private val modelIds get() = _uiState.value.modelIds
    private val selectedModel get() = _uiState.value.selectedModel
    private val loading get() = _uiState.value.loading
    private val saving get() = _uiState.value.saving
    private val requestRunning get() = _uiState.value.requestRunning
    private val initialized get() = _uiState.value.initialized
    private val message get() = _uiState.value.message
    private var savedKey = ""
    private var loadJob: Job? = null
    private var requestJob: Job? = null

    private val canEdit get() = _uiState.value.canEdit
    private val canSave get() = _uiState.value.canSave

    init { reload() }

    fun reload() {
        if (saving) return
        loadJob?.cancel(); cancelRequest()
        updateState { current -> current.copy(loading = true, message = null) }
        loadJob = viewModelScope.launch {
            try {
                val settings = repository.read()
                currentCoroutineContext().ensureActive()
                updateState { current -> current.copy(providers = settings.providers, currentSelection = settings.selection, initialized = true) }
                val id = settings.selection?.providerId ?: settings.providers.firstOrNull()?.id
                if (id == null) reset(defaultName, DEEPSEEK_URL) else fill(providers.single { it.id == id })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                updateState { current -> current.copy(message = R.string.ai_settings_read_failed) }
            } finally { if (currentCoroutineContext().isActive) updateState { current -> current.copy(loading = false) } }
        }
    }

    fun chooseProvider(id: String) {
        if (!canEdit || id == providerId) return
        val connection = providers.singleOrNull { it.id == id } ?: return
        loadJob?.cancel(); cancelRequest(); updateState { current -> current.copy(loading = true, message = null) }
        loadJob = viewModelScope.launch {
            try { fill(connection) }
            finally { if (currentCoroutineContext().isActive) updateState { current -> current.copy(loading = false) } }
        }
    }

    private suspend fun fill(connection: AiProviderConnection) {
        val key = try { repository.readKey(connection.id) } catch (e: AiConfigurationException) {
            currentCoroutineContext().ensureActive()
            updateState { current -> current.copy(message = aiConfigurationMessage(e.reason)) }
            ""
        }
        currentCoroutineContext().ensureActive()
        updateState { current -> current.copy(providerId = connection.id, name = connection.name, baseUrl = connection.baseUrl, apiKey = key) }; savedKey = key; updateState { current -> current.copy(showKey = false, modelIds = connection.modelIds, selectedModel = currentSelection?.takeIf { it.providerId == connection.id }?.modelId.orEmpty()) }
    }

    fun newConnection(connectionName: String, url: String = "") {
        if (!canEdit) return
        cancelRequest(); updateState { current -> current.copy(message = null) }; reset(connectionName, url)
    }

    private fun reset(connectionName: String, url: String) {
        updateState { current -> current.copy(providerId = UUID.randomUUID().toString(), name = connectionName, baseUrl = url, apiKey = "") }; savedKey = ""; updateState { current -> current.copy(showKey = false, modelIds = emptyList(), selectedModel = "") }
    }

    private fun edit(action: () -> Unit) {
        if (!canEdit) return
        cancelRequest(); updateState { current -> current.copy(message = null) }; action()
    }
    fun changeName(value: String) = edit { updateState { current -> current.copy(name = value) } }
    fun changeBaseUrl(value: String) = edit { updateState { current -> current.copy(baseUrl = value) } }
    fun changeKey(value: String) = edit { updateState { current -> current.copy(apiKey = value) } }
    fun changeModel(value: String) = edit { updateState { current -> current.copy(selectedModel = value) } }
    fun toggleKey() { if (canEdit) updateState { current -> current.copy(showKey = !showKey) } }
    fun addModel() = edit {
        val id = selectedModel.trim()
        if (id.isNotEmpty()) { updateState { current -> current.copy(modelIds = (modelIds + id).distinct(), selectedModel = id) } }
    }
    fun removeModel() = edit { updateState { current -> current.copy(modelIds = modelIds.filterNot { it == selectedModel }, selectedModel = "") } }

    private fun validCredentials(): Boolean = try {
        openAiEndpoint(baseUrl, "models")
        apiKey.trim().let { it.isNotEmpty() && it.all { char -> char in '!'..'~' } }
    } catch (_: IllegalArgumentException) { false }

    fun fetch() {
        if (!canEdit || requestRunning) return
        if (!validCredentials()) { updateState { current -> current.copy(message = R.string.ai_invalid_credentials) }; return }
        val url = baseUrl.trim(); val key = apiKey.trim()
        updateState { current -> current.copy(requestRunning = true, message = null) }
        requestJob = viewModelScope.launch {
            try {
                val result = operations.models(url, key)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is AiModelsResult.Success -> {
                        updateState { current -> current.copy(modelIds = (modelIds + result.modelIds).distinct()) }
                        updateState { current -> current.copy(message = if (result.modelIds.isEmpty()) R.string.ai_models_empty else R.string.ai_models_loaded) }
                    }
                    is AiModelsResult.Failure -> updateState { current -> current.copy(message = aiRequestMessage(result.reason)) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive(); updateState { current -> current.copy(message = R.string.ai_request_failed) }
            } finally { if (currentCoroutineContext().isActive) updateState { current -> current.copy(requestRunning = false) } }
        }
    }

    fun test() {
        if (!canEdit || requestRunning) return
        if (!validCredentials() || selectedModel.isBlank()) { updateState { current -> current.copy(message = R.string.ai_invalid_credentials) }; return }
        val url = baseUrl.trim(); val key = apiKey.trim(); val model = selectedModel.trim()
        updateState { current -> current.copy(requestRunning = true, message = null) }
        requestJob = viewModelScope.launch {
            try {
                val failure = operations.test(url, model, key)
                currentCoroutineContext().ensureActive()
                updateState { current -> current.copy(message = failure?.let(::aiRequestMessage) ?: R.string.ai_connection_success) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive(); updateState { current -> current.copy(message = R.string.ai_request_failed) }
            } finally { if (currentCoroutineContext().isActive) updateState { current -> current.copy(requestRunning = false) } }
        }
    }

    fun cancelRequest() { requestJob?.cancel(); updateState { current -> current.copy(requestRunning = false) } }

    fun save() {
        if (!canSave) return
        val connection = AiProviderConnection(providerId, name.trim(), baseUrl.trim().trimEnd('/'), modelIds.toList())
        val selection = AiModelSelection(providerId, selectedModel.trim())
        val key = apiKey.trim()
        updateState { current -> current.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                if (providers.singleOrNull { it.id == providerId } == connection && key == savedKey)
                    repository.selectModel(selection)
                else repository.save(connection, key, selection)
                currentCoroutineContext().ensureActive()
                updateState { current -> current.copy(providers = if (providers.any { it.id == connection.id })
                    providers.map { if (it.id == connection.id) connection else it }
                    else providers + connection, currentSelection = selection) }; savedKey = key
                updateState { current -> current.copy(name = connection.name, baseUrl = connection.baseUrl, apiKey = key, selectedModel = selection.modelId, message = R.string.ai_settings_saved) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(message = if (e is AiConfigurationException && e.reason != AiConfigurationFailure.STORAGE)
                    aiConfigurationMessage(e.reason) else R.string.ai_settings_save_failed) }
            } finally { updateState { current -> current.copy(saving = false) } }
        }
    }

    fun delete() {
        if (!canEdit || requestRunning || providers.none { it.id == providerId }) return
        val id = providerId
        updateState { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                repository.delete(id)
                updateState { current -> current.copy(providers = providers.filterNot { it.id == id }, currentSelection = currentSelection?.takeUnless { it.providerId == id }) }
                reset(defaultName, DEEPSEEK_URL); updateState { current -> current.copy(message = R.string.ai_settings_deleted) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(message = R.string.ai_settings_save_failed) }
            } finally { updateState { current -> current.copy(saving = false) } }
        }
    }

    companion object { const val DEEPSEEK_URL = "https://api.deepseek.com" }
}
