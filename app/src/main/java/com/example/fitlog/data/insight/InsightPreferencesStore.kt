package com.example.fitlog.data.insight

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.insightPreferences by preferencesDataStore(name = "insight_preferences")

interface InsightPreferences {
    fun observe(vaultId: String?): kotlinx.coroutines.flow.Flow<Set<String>?>
    fun observeRange(): kotlinx.coroutines.flow.Flow<String?>
    suspend fun saveRange(range: String)
    suspend fun save(vaultId: String?, names: Set<String>)
}

/** Display preferences only; training records and user-confirmed aliases keep their own storage. */
class InsightPreferencesStore(context: Context) : InsightPreferences {
    private val store = context.applicationContext.insightPreferences
    private fun key(vaultId: String?) = stringSetPreferencesKey("focused_exercises_${vaultId ?: "native"}")
    private val rangeKey = stringPreferencesKey("insight_range")

    override fun observe(vaultId: String?) = store.data.map { it[key(vaultId)] }

    override fun observeRange() = store.data.map { it[rangeKey] }

    override suspend fun saveRange(range: String) {
        store.edit { it[rangeKey] = range }
    }

    override suspend fun save(vaultId: String?, names: Set<String>) {
        require(names.size <= 3)
        store.edit { it[key(vaultId)] = names }
    }
}
