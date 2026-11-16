package com.example.fitlog.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fitlog.model.record.ExerciseAliasGroup
import com.example.fitlog.model.record.ExerciseAliases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ExerciseAliasStore {
    fun observe(): Flow<ExerciseAliases>
    suspend fun save( previousPrimaryKey: String?, replacement: ExerciseAliasGroup?)
}

// User corrections: deliberately separate from resettable vault/settings/index storage.
// A future format change must export a JSON snapshot before any rebuild.
private val Context.exerciseAliasDataStore: DataStore<Preferences> by preferencesDataStore(name = "exercise_aliases")

class DataStoreExerciseAliasStore(private val store: DataStore<Preferences>) : ExerciseAliasStore {
    constructor(context: Context) : this(context.applicationContext.exerciseAliasDataStore)

    private val key = stringPreferencesKey("aliases")
    private fun decode(value: String?) = if (value == null) ExerciseAliases()
        else Json.decodeFromString<ExerciseAliases>(value).validate()

    override fun observe(): Flow<ExerciseAliases> =
        store.data.map { decode(it[key]) }

    override suspend fun save( previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
        store.edit { preferences ->
            val updated = decode(preferences[key]).replacing(previousPrimaryKey, replacement)
            preferences[key] = Json.encodeToString(updated)
        }
    }
}
