package com.example.fitlog.model.record

import java.util.Locale
import kotlinx.serialization.Serializable

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
