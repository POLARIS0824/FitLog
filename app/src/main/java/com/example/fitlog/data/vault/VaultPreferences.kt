package com.example.fitlog.data.vault

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vault_preferences"
)

class VaultPreferences(
    context: Context,
) {
    private val appContext = context.applicationContext

    val vaultUri: Flow<Uri?> =
        appContext.vaultDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[VAULT_URI]
                    ?.let(Uri::parse)
            }

    suspend fun setVaultUri(uri: Uri) {
        appContext.vaultDataStore.edit { preferences ->
            preferences[VAULT_URI] = uri.toString()
        }
    }

    suspend fun clearVaultUri() {
        appContext.vaultDataStore.edit { preferences ->
            preferences.remove(VAULT_URI)
        }
    }

    private companion object {
        val VAULT_URI = stringPreferencesKey("vault_uri")
    }
}