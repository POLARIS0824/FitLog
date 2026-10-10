package com.example.fitlog.data.insight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fitlog.data.vault.requireVaultId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun exerciseNameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

@Serializable
data class ExerciseAliasGroup(val primaryName: String, val members: List<String>) {
    val primaryKey: String get() = exerciseNameKey(primaryName)
}

@Serializable
data class ExerciseAliases(val schemaVersion: Int = 1, val groups: List<ExerciseAliasGroup> = emptyList()) {
    fun groupFor(nameKey: String): ExerciseAliasGroup? = groups.firstOrNull { nameKey in it.members }

    fun validate(): ExerciseAliases {
        require(schemaVersion == 1)
        val assigned = mutableSetOf<String>()
        groups.forEach { group ->
            require(group.primaryName.isNotBlank() && group.primaryName == group.primaryName.trim())
            require(group.members.isNotEmpty() && group.primaryKey in group.members)
            group.members.forEach { key ->
                require(key.isNotBlank() && key == exerciseNameKey(key) && assigned.add(key))
            }
        }
        return this
    }

    /** Replace one group atomically. A name belonging to another group cannot be stolen. */
    fun replacing(previousPrimaryKey: String?, replacement: ExerciseAliasGroup?): ExerciseAliases {
        val remaining = groups.filterNot { it.primaryKey == previousPrimaryKey }
        if (previousPrimaryKey != null) require(groups.any { it.primaryKey == previousPrimaryKey })
        return copy(groups = remaining + listOfNotNull(replacement)).validate()
    }
}

interface ExerciseAliasStore {
    fun observe(vaultId: String): Flow<ExerciseAliases>
    suspend fun save(vaultId: String, previousPrimaryKey: String?, replacement: ExerciseAliasGroup?)
}

// User corrections: deliberately separate from resettable vault/settings/index storage.
// A future format change must export a JSON snapshot before any rebuild.
private val Context.exerciseAliasDataStore: DataStore<Preferences> by preferencesDataStore(name = "exercise_aliases")

class DataStoreExerciseAliasStore(private val store: DataStore<Preferences>) : ExerciseAliasStore {
    constructor(context: Context) : this(context.applicationContext.exerciseAliasDataStore)

    private fun key(vaultId: String) = stringPreferencesKey(requireVaultId(vaultId))
    private fun decode(value: String?) = if (value == null) ExerciseAliases()
        else Json.decodeFromString<ExerciseAliases>(value).validate()

    override fun observe(vaultId: String): Flow<ExerciseAliases> =
        store.data.map { decode(it[key(vaultId)]) }

    override suspend fun save(vaultId: String, previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
        val key = key(vaultId)
        store.edit { preferences ->
            val updated = decode(preferences[key]).replacing(previousPrimaryKey, replacement)
            preferences[key] = Json.encodeToString(updated)
        }
    }
}
