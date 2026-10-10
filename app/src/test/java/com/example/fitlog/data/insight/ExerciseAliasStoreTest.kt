package com.example.fitlog.data.insight

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseAliasStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val vault = "00000000-0000-4000-8000-000000000001"
    private val other = "00000000-0000-4000-8000-000000000002"
    private val group = ExerciseAliasGroup("Bench", listOf("bench", "卧推"))

    @Test fun persistedAliasesSurviveAStoreRestartAndRemainIsolatedByVault() = runBlocking {
        val file = File(temporary.root, "aliases.preferences_pb")
        var job = SupervisorJob()
        var preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        try {
            DataStoreExerciseAliasStore(preferences).save(vault, null, group)
            assertTrue(DataStoreExerciseAliasStore(preferences).observe(other).first().groups.isEmpty())
            job.cancelAndJoin()
            job = SupervisorJob()
            preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            val reopened = DataStoreExerciseAliasStore(preferences)
            assertEquals(listOf(group), reopened.observe(vault).first().groups)
            reopened.save(other, null, ExerciseAliasGroup("Squat", listOf("squat")))
            assertEquals(listOf(group), reopened.observe(vault).first().groups)
            reopened.save(vault, group.primaryKey, group.copy(members = listOf("bench")))
            assertEquals(listOf("bench"), reopened.observe(vault).first().groups.single().members)
            reopened.save(vault, "bench", null)
            assertTrue(reopened.observe(vault).first().groups.isEmpty())
            assertEquals("Squat", reopened.observe(other).first().groups.single().primaryName)
        } finally { job.cancelAndJoin() }
    }

    @Test fun corruptCorrectionsFailExplicitlyAndCannotBeOverwrittenByASave() = runTest {
        val preferences = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(temporary.root, "corrupt.preferences_pb") })
        val key = stringPreferencesKey(vault)
        preferences.edit { it[key] = "damaged-user-corrections" }
        val store = DataStoreExerciseAliasStore(preferences)
        assertNotNull(runCatching { store.observe(vault).first() }.exceptionOrNull())
        assertNotNull(runCatching { store.save(vault, null, group) }.exceptionOrNull())
        assertEquals("damaged-user-corrections", preferences.data.first()[key])
    }

    @Test fun failedWritePreservesThePreviousCorrection() = runTest {
        val key = stringPreferencesKey(vault)
        val before = preferencesOf(key to kotlinx.serialization.json.Json.encodeToString(ExerciseAliases.serializer(), ExerciseAliases(groups = listOf(group))))
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = MutableStateFlow(before)
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                transform(before)
                throw IOException("Disk unavailable")
            }
        }
        val store = DataStoreExerciseAliasStore(failing)
        assertTrue(runCatching { store.save(vault, "bench", null) }.exceptionOrNull() is IOException)
        assertEquals(listOf(group), store.observe(vault).first().groups)
    }

    @Test fun overlappingGroupsAndNonNormalizedNamesAreRejectedWithoutChangingExistingGroups() {
        val aliases = ExerciseAliases(groups = listOf(group)).validate()
        assertNotNull(runCatching { aliases.replacing(null, ExerciseAliasGroup("Other", listOf("other", "bench"))) }.exceptionOrNull())
        assertNotNull(runCatching { aliases.replacing("bench", group.copy(members = listOf("Bench"))) }.exceptionOrNull())
        assertNotNull(runCatching { aliases.replacing("missing", null) }.exceptionOrNull())
        assertEquals(listOf(group), aliases.groups)
        assertEquals("i bench", exerciseNameKey(" I BENCH "))
    }
}
