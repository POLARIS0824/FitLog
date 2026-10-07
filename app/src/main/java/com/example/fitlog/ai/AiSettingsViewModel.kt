package com.example.fitlog.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.ai.*
import com.example.fitlog.data.analysis.DiaryParseFailure
import com.example.fitlog.data.analysis.adapter.AiModelsResult
import com.example.fitlog.data.analysis.adapter.openAiEndpoint
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Form state survives Activity recreation; plaintext keys are never put in SavedStateHandle. */
internal class AiSettingsViewModel(
    private val repository: AiProviderRepository,
    private val defaultName: String,
    private val fetchModels: suspend (String, String) -> AiModelsResult,
    private val testConnection: suspend (String, String, String) -> DiaryParseFailure?,
) : ViewModel() {
    var providers by mutableStateOf<List<AiProviderConnection>>(emptyList()); private set
    var currentSelection by mutableStateOf<AiModelSelection?>(null); private set
    var providerId by mutableStateOf(UUID.randomUUID().toString()); private set
    var name by mutableStateOf(defaultName); private set
    var baseUrl by mutableStateOf(DEEPSEEK_URL); private set
    var apiKey by mutableStateOf(""); private set
    var showKey by mutableStateOf(false); private set
    var modelIds by mutableStateOf<List<String>>(emptyList()); private set
    var selectedModel by mutableStateOf(""); private set
    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var requestRunning by mutableStateOf(false); private set
    var initialized by mutableStateOf(false); private set
    var message by mutableStateOf<Int?>(null); private set
    private var savedKey = ""
    private var loadJob: Job? = null
    private var requestJob: Job? = null

    val canEdit get() = initialized && !loading && !saving
    val canSave get() = canEdit && !requestRunning && name.isNotBlank() && validCredentials() &&
        selectedModel.trim() in modelIds

    init { reload() }

    fun reload() {
        if (saving) return
        loadJob?.cancel(); cancelRequest()
        loading = true; message = null
        loadJob = viewModelScope.launch {
            try {
                val settings = repository.read()
                currentCoroutineContext().ensureActive()
                providers = settings.providers; currentSelection = settings.selection
                initialized = true
                val id = settings.selection?.providerId ?: settings.providers.firstOrNull()?.id
                if (id == null) reset(defaultName, DEEPSEEK_URL) else fill(providers.single { it.id == id })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
                message = R.string.ai_settings_read_failed
            } finally { if (currentCoroutineContext().isActive) loading = false }
        }
    }

    fun chooseProvider(id: String) {
        if (!canEdit || id == providerId) return
        val connection = providers.singleOrNull { it.id == id } ?: return
        loadJob?.cancel(); cancelRequest(); loading = true; message = null
        loadJob = viewModelScope.launch {
            try { fill(connection) }
            finally { if (currentCoroutineContext().isActive) loading = false }
        }
    }

    private suspend fun fill(connection: AiProviderConnection) {
        val key = try { repository.readKey(connection.id) } catch (e: AiConfigurationException) {
            currentCoroutineContext().ensureActive()
            message = aiConfigurationMessage(e.reason)
            ""
        }
        currentCoroutineContext().ensureActive()
        providerId = connection.id; name = connection.name; baseUrl = connection.baseUrl
        apiKey = key; savedKey = key; showKey = false; modelIds = connection.modelIds
        selectedModel = currentSelection?.takeIf { it.providerId == connection.id }?.modelId.orEmpty()
    }

    fun newConnection(connectionName: String, url: String = "") {
        if (!canEdit) return
        cancelRequest(); message = null; reset(connectionName, url)
    }

    private fun reset(connectionName: String, url: String) {
        providerId = UUID.randomUUID().toString(); name = connectionName; baseUrl = url
        apiKey = ""; savedKey = ""; showKey = false; modelIds = emptyList(); selectedModel = ""
    }

    private fun edit(action: () -> Unit) {
        if (!canEdit) return
        cancelRequest(); message = null; action()
    }
    fun changeName(value: String) = edit { name = value }
    fun changeBaseUrl(value: String) = edit { baseUrl = value }
    fun changeKey(value: String) = edit { apiKey = value }
    fun changeModel(value: String) = edit { selectedModel = value }
    fun toggleKey() { if (canEdit) showKey = !showKey }
    fun addModel() = edit {
        val id = selectedModel.trim()
        if (id.isNotEmpty()) { modelIds = (modelIds + id).distinct(); selectedModel = id }
    }
    fun removeModel() = edit { modelIds = modelIds.filterNot { it == selectedModel }; selectedModel = "" }

    private fun validCredentials(): Boolean = try {
        openAiEndpoint(baseUrl, "models")
        apiKey.trim().let { it.isNotEmpty() && it.all { char -> char in '!'..'~' } }
    } catch (_: IllegalArgumentException) { false }

    fun fetch() {
        if (!canEdit || requestRunning) return
        if (!validCredentials()) { message = R.string.ai_invalid_credentials; return }
        val url = baseUrl.trim(); val key = apiKey.trim()
        requestRunning = true; message = null
        requestJob = viewModelScope.launch {
            try {
                val result = fetchModels(url, key)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is AiModelsResult.Success -> {
                        modelIds = (modelIds + result.modelIds).distinct()
                        message = if (result.modelIds.isEmpty()) R.string.ai_models_empty else R.string.ai_models_loaded
                    }
                    is AiModelsResult.Failure -> message = aiRequestMessage(result.reason)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive(); message = R.string.ai_request_failed
            } finally { if (currentCoroutineContext().isActive) requestRunning = false }
        }
    }

    fun test() {
        if (!canEdit || requestRunning) return
        if (!validCredentials() || selectedModel.isBlank()) { message = R.string.ai_invalid_credentials; return }
        val url = baseUrl.trim(); val key = apiKey.trim(); val model = selectedModel.trim()
        requestRunning = true; message = null
        requestJob = viewModelScope.launch {
            try {
                val failure = testConnection(url, model, key)
                currentCoroutineContext().ensureActive()
                message = failure?.let(::aiRequestMessage) ?: R.string.ai_connection_success
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive(); message = R.string.ai_request_failed
            } finally { if (currentCoroutineContext().isActive) requestRunning = false }
        }
    }

    fun cancelRequest() { requestJob?.cancel(); requestRunning = false }

    fun save() {
        if (!canSave) return
        val connection = AiProviderConnection(providerId, name.trim(), baseUrl.trim().trimEnd('/'), modelIds.toList())
        val selection = AiModelSelection(providerId, selectedModel.trim())
        val key = apiKey.trim()
        saving = true; message = null
        viewModelScope.launch {
            try {
                if (providers.singleOrNull { it.id == providerId } == connection && key == savedKey)
                    repository.selectModel(selection)
                else repository.save(connection, key, selection)
                currentCoroutineContext().ensureActive()
                providers = if (providers.any { it.id == connection.id })
                    providers.map { if (it.id == connection.id) connection else it }
                else providers + connection
                currentSelection = selection; savedKey = key
                name = connection.name; baseUrl = connection.baseUrl; apiKey = key; selectedModel = selection.modelId
                message = R.string.ai_settings_saved
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = if (e is AiConfigurationException && e.reason != AiConfigurationFailure.STORAGE)
                    aiConfigurationMessage(e.reason) else R.string.ai_settings_save_failed
            } finally { saving = false }
        }
    }

    fun delete() {
        if (!canEdit || requestRunning || providers.none { it.id == providerId }) return
        val id = providerId; saving = true; message = null
        viewModelScope.launch {
            try {
                repository.delete(id)
                providers = providers.filterNot { it.id == id }
                currentSelection = currentSelection?.takeUnless { it.providerId == id }
                reset(defaultName, DEEPSEEK_URL); message = R.string.ai_settings_deleted
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = R.string.ai_settings_save_failed
            } finally { saving = false }
        }
    }

    companion object { const val DEEPSEEK_URL = "https://api.deepseek.com" }
}

internal fun aiConfigurationMessage(reason: AiConfigurationFailure): Int = when (reason) {
    AiConfigurationFailure.NOT_CONFIGURED -> R.string.ai_not_configured
    AiConfigurationFailure.KEY_UNREADABLE -> R.string.ai_key_unreadable
    AiConfigurationFailure.INVALID_CONFIGURATION -> R.string.ai_invalid_credentials
    AiConfigurationFailure.STORAGE -> R.string.ai_settings_read_failed
}

internal fun aiRequestMessage(reason: DiaryParseFailure): Int = when (reason) {
    DiaryParseFailure.AUTHENTICATION_ERROR -> R.string.detail_failure_authentication
    DiaryParseFailure.PERMISSION_DENIED -> R.string.detail_failure_permission
    DiaryParseFailure.RATE_LIMITED -> R.string.detail_failure_rate_limit
    DiaryParseFailure.SERVICE_UNAVAILABLE -> R.string.detail_failure_service
    DiaryParseFailure.TIMEOUT -> R.string.detail_failure_timeout
    DiaryParseFailure.NETWORK_ERROR -> R.string.detail_failure_network
    else -> R.string.ai_request_failed
}
