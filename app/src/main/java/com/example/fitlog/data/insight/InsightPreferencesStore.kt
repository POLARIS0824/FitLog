package com.example.fitlog.data.insight

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.insightPreferences by preferencesDataStore(name = "insight_preferences")

/** Display preferences only; training records and user-confirmed aliases keep their own storage. */
class InsightPreferencesStore(context: Context) {
    private val store = context.applicationContext.insightPreferences
    private fun key(vaultId: String?) = stringSetPreferencesKey("focused_exercises_${vaultId ?: "native"}")
    private val rangeKey = stringPreferencesKey("insight_range")

    fun observe(vaultId: String?) = store.data.map { it[key(vaultId)] }

    fun observeRange() = store.data.map { it[rangeKey] }

    suspend fun saveRange(range: String) {
        store.edit { it[rangeKey] = range }
    }

    suspend fun save(vaultId: String?, names: Set<String>) {
        require(names.size <= 3)
        store.edit { it[key(vaultId)] = names }
    }
}
