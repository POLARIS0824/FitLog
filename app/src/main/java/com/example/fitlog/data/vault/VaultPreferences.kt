package com.example.fitlog.data.vault

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

internal val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_preferences"
)

/**
 * Vault 配置读取结果显式状态。
 *
 * 显式区分四种状态，避免用 null 同时表示“未配置”与“读取失败”。
 */
sealed interface VaultConfigState {
    /** 正在读取 DataStore 配置 */
    data object Loading : VaultConfigState

    /** 尚未配置 Vault */
    data object NotConfigured : VaultConfigState

    /** 已配置 Vault，包含持久化 UUID 身份及经 SAF 授权的目录 URI */
    data class Configured(val uri: Uri, val vaultId: String) : VaultConfigState {
        init { requireVaultId(vaultId) }
    }

    /** 读取配置失败（如存储损坏或 I/O 异常） */
    data class Failed(val cause: Throwable) : VaultConfigState
}

class VaultPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    val diary = DiaryPreferences(dataStore)
    /**
     * 供生产环境调用的便捷构造函数，复用 Context 单例 DataStore
     */
    constructor(context: Context) : this(context.applicationContext.vaultDataStore)

    /**
     * 响应式 Vault 配置状态流。
     *
     * 1. 订阅开始时先发出 [VaultConfigState.Loading]。
     * 2. 读取成功时，按键值是否存在发出 [VaultConfigState.NotConfigured] 或 [VaultConfigState.Configured]。
     * 3. 读取异常时发出 [VaultConfigState.Failed]，不再通过 emptyPreferences() 伪装为未配置。
     */
    val vaultConfig: Flow<VaultConfigState> =
        dataStore.data
            .map<Preferences, VaultConfigState> { preferences ->
                val uriString = preferences[VAULT_URI]
                if (uriString.isNullOrBlank()) {
                    VaultConfigState.NotConfigured
                } else {
                    val id = requireNotNull(preferences[VAULT_ID]) { "Missing vault identity" }
                    require(identities(preferences)[uriString] == id) { "Conflicting vault identity" }
                    VaultConfigState.Configured(uriString.toUri(), id)
                }
            }
            .onStart {
                emit(VaultConfigState.Loading)
            }
            .catch { exception ->
                if (exception is CancellationException) throw exception
                emit(VaultConfigState.Failed(exception))
            }

    /**
     * 单次挂起获取当前的 Vault 配置（自动过滤 Loading 中间态）。
     */
    suspend fun getVaultConfig(): VaultConfigState {
        return vaultConfig.first { it !is VaultConfigState.Loading }
    }

    /**
     * 在同一事务保存 Vault 目录 URI 和 UUID 身份；已知 URI 复用原 UUID。
     *
     * @param uri 用户通过 SAF 选择并持久化授权的目录 Uri
     * @return [Result.success] 表示保存成功；[Result.failure] 明确向调用方返回错误，避免静默失败
     */
    suspend fun setVaultUri(uri: Uri): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                preferences[VAULT_ID] = identityFor(preferences, uri.toString())
                preferences[VAULT_URI] = uri.toString()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * 清除 Vault 配置。
     *
     * 注意：
     * 1. 仅移除当前 vault_uri / vault_id，保留历史身份映射与设置，绝不删除本地文件。
     * 2. 本层不负责释放 SAF 持久化授权（授权生命周期由 SAF/权限管理层统一控制）。
     *
     * @return [Result.success] 表示清除成功；[Result.failure] 表示写入失败。
     */
    suspend fun clearVaultUri(): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                preferences.remove(VAULT_URI)
                preferences.remove(VAULT_ID)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun getVaultUri(vaultId: String): Uri {
        requireVaultId(vaultId)
        return requireNotNull(identities(dataStore.data.first()).entries.singleOrNull { it.value == vaultId }) {
            "Unknown vault identity"
        }.key.toUri()
    }

    private fun identities(preferences: Preferences): Map<String, String> {
        val values = preferences[VAULT_IDENTITIES]?.let { Json.decodeFromString<Map<String, String>>(it) }.orEmpty()
        values.forEach { (uri, id) -> require(uri.isNotBlank()); requireVaultId(id) }
        require(values.values.toSet().size == values.size) { "Duplicate vault identity" }
        return values
    }

    private fun identityFor(preferences: MutablePreferences, uri: String): String {
        val values = identities(preferences).toMutableMap()
        val id = values[uri] ?: UUID.randomUUID().toString()
        require(values.none { it.key != uri && it.value == id }) { "Duplicate vault identity" }
        values[uri] = id
        preferences[VAULT_IDENTITIES] = Json.encodeToString(values)
        return id
    }

    private companion object {
        val VAULT_URI = stringPreferencesKey("vault_uri")
        val VAULT_ID = stringPreferencesKey("vault_id")
        // Retained on disconnect and independent of the rebuildable Room index.
        val VAULT_IDENTITIES = stringPreferencesKey("vault_identities")
    }
}
