package com.example.fitlog.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fitlog.data.analysis.adapter.openAiEndpoint
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/** Connection metadata contains multiple model IDs and no plaintext credential or selected model. */
@Serializable
data class AiProviderConnection(val id: String, val name: String, val baseUrl: String, val modelIds: List<String>)

@Serializable
data class AiModelSelection(val providerId: String, val modelId: String)

data class AiSettings(val providers: List<AiProviderConnection> = emptyList(), val selection: AiModelSelection? = null)

enum class AiConfigurationFailure { NOT_CONFIGURED, STORAGE, KEY_UNREADABLE, INVALID_CONFIGURATION }

class AiConfigurationException(val reason: AiConfigurationFailure, cause: Exception? = null) :
    IOException("AI configuration: ${reason.name}", cause)

/** One request snapshot. Ordinary identity-based toString intentionally does not print the key. */
class AiSelectedModel(val provider: AiProviderConnection, val modelId: String, val apiKey: String)

/** All configuration writes replace one value atomically; no read performs a repair or network call. */
class AiProviderRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val encrypt: (String) -> String = AiKeyCipher::encrypt,
    private val decrypt: (String) -> String = AiKeyCipher::decrypt,
) {
    suspend fun read(): AiSettings = storage {
        val saved = decode(store.data.first()[CONFIG])
        AiSettings(saved.providers.map { it.connection }, saved.selection)
    }

    suspend fun readKey(providerId: String): String = storage {
        val saved = decode(store.data.first()[CONFIG])
        val provider = saved.providers.singleOrNull { it.connection.id == providerId }
            ?: throw AiConfigurationException(AiConfigurationFailure.NOT_CONFIGURED)
        decryptKey(provider.encryptedKey)
    }

    /** Reads the connection, model and credential from the same persisted configuration snapshot. */
    suspend fun readActive(): AiSelectedModel = storage {
        val saved = decode(store.data.first()[CONFIG])
        val selected = saved.selection ?: throw AiConfigurationException(AiConfigurationFailure.NOT_CONFIGURED)
        val provider = saved.providers.single { it.connection.id == selected.providerId }
        AiSelectedModel(provider.connection, selected.modelId, decryptKey(provider.encryptedKey))
    }

    /** Explicit Save also selects the model, preserving every other connection and its model list. */
    suspend fun save(connection: AiProviderConnection, apiKey: String, selection: AiModelSelection) = storage {
        val normalized = connection.copy(name = connection.name.trim(), baseUrl = connection.baseUrl.trim().trimEnd('/'),
            modelIds = connection.modelIds.map(String::trim).filter(String::isNotEmpty).distinct())
        val key = apiKey.trim()
        try {
            require(normalized.id.isNotBlank() && normalized.name.isNotBlank())
            openAiEndpoint(normalized.baseUrl, "models")
            require(normalized.modelIds.isNotEmpty())
            require(selection.providerId == normalized.id && selection.modelId in normalized.modelIds)
            require(key.isNotEmpty() && key.all { it in '!'..'~' })
        } catch (e: IllegalArgumentException) {
            throw AiConfigurationException(AiConfigurationFailure.INVALID_CONFIGURATION, e)
        }
        // Encryption happens before the transaction; failure cannot clear or partially replace a setting.
        val encrypted = encrypt(key)
        store.edit { preferences ->
            val previous = decode(preferences[CONFIG])
            val entry = StoredProvider(normalized, encrypted)
            val providers = if (previous.providers.any { it.connection.id == normalized.id }) {
                previous.providers.map { if (it.connection.id == normalized.id) entry else it }
            } else previous.providers + entry
            preferences[CONFIG] = json.encodeToString(StoredSettings(providers, selection))
        }
    }

    /** Switching a model only changes the selection; it does not rewrite credentials or model lists. */
    suspend fun selectModel(selection: AiModelSelection) = storage {
        store.edit { preferences ->
            val saved = decode(preferences[CONFIG])
            val provider = saved.providers.singleOrNull { it.connection.id == selection.providerId }
            if (provider == null || selection.modelId !in provider.connection.modelIds)
                throw AiConfigurationException(AiConfigurationFailure.INVALID_CONFIGURATION)
            preferences[CONFIG] = json.encodeToString(saved.copy(selection = selection))
        }
    }

    suspend fun delete(providerId: String) = storage {
        store.edit { preferences ->
            val saved = decode(preferences[CONFIG])
            preferences[CONFIG] = json.encodeToString(saved.copy(
                providers = saved.providers.filterNot { it.connection.id == providerId },
                selection = saved.selection?.takeUnless { it.providerId == providerId },
            ))
        }
    }

    private fun decryptKey(encrypted: String): String = try {
        decrypt(encrypted).also { require(it.isNotEmpty() && it.all { char -> char in '!'..'~' }) }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw AiConfigurationException(AiConfigurationFailure.KEY_UNREADABLE, e)
    }

    private fun decode(text: String?): StoredSettings {
        val saved = text?.let { json.decodeFromString<StoredSettings>(it) } ?: StoredSettings()
        require(saved.providers.map { it.connection.id }.distinct().size == saved.providers.size)
        saved.providers.forEach { entry ->
            val connection = entry.connection
            require(connection.id.isNotBlank() && connection.name.isNotBlank() && entry.encryptedKey.isNotBlank())
            openAiEndpoint(connection.baseUrl, "models")
            require(connection.modelIds.isNotEmpty() && connection.modelIds.all { it.isNotBlank() })
            require(connection.modelIds.distinct() == connection.modelIds)
        }
        saved.selection?.let { selected ->
            require(saved.providers.any { it.connection.id == selected.providerId && selected.modelId in it.connection.modelIds })
        }
        return saved
    }

    private suspend fun <T> storage(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() } catch (e: Exception) {
            if (e is CancellationException || e is AiConfigurationException) throw e
            throw AiConfigurationException(AiConfigurationFailure.STORAGE, e)
        }
    }

    private companion object {
        val CONFIG = stringPreferencesKey("configuration")
        val json = Json { encodeDefaults = true }
    }
}

@Serializable
private data class StoredProvider(val connection: AiProviderConnection, val encryptedKey: String)

@Serializable
private data class StoredSettings(val providers: List<StoredProvider> = emptyList(), val selection: AiModelSelection? = null)
