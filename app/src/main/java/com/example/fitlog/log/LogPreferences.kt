package com.example.fitlog.log

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

enum class LogSortOrder { Ascending, Descending }

interface LogSettingsStore {
    suspend fun readSort(): LogSortOrder
    suspend fun saveSort(order: LogSortOrder)
}

class LogPreferences(private val store: DataStore<Preferences>) : LogSettingsStore {
    private val sortKey = stringPreferencesKey("log_filename_sort")
    override suspend fun readSort(): LogSortOrder = store.data.first()[sortKey]
        ?.let { value -> LogSortOrder.entries.firstOrNull { it.name == value } } ?: LogSortOrder.Descending

    override suspend fun saveSort(order: LogSortOrder) {
        store.edit { it[sortKey] = order.name }
    }
}
