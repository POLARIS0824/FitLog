package com.example.fitlog.log

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.data.vault.DiaryDateFormat
import com.example.fitlog.data.vault.DiarySettings
import com.example.fitlog.data.vault.VaultPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogPreferencesTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test fun separatelyAssembledPreferencesShareStoreAndPreserveExistingSortKey() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempFolder.root, "vault_test.preferences_pb") },
        )
        val vaultPreferences = VaultPreferences(store)
        val logPreferences = LogPreferences(store)
        val sortKey = stringPreferencesKey("log_filename_sort")
        val diarySettings = DiarySettings(listOf("daily"), DiaryDateFormat.Compact)

        assertEquals(LogSortOrder.Descending, logPreferences.readSort())
        store.edit { it[sortKey] = LogSortOrder.Ascending.name }
        assertEquals(LogSortOrder.Ascending, logPreferences.readSort())

        vaultPreferences.diary.save("00000000-0000-4000-8000-000000000001", diarySettings)
        logPreferences.saveSort(LogSortOrder.Descending)
        vaultPreferences.clearVaultUri().getOrThrow()

        assertEquals(LogSortOrder.Descending.name, store.data.first()[sortKey])
        assertEquals(LogSortOrder.Descending, LogPreferences(store).readSort())
        assertEquals(diarySettings, VaultPreferences(store).diary.read("00000000-0000-4000-8000-000000000001"))
    }
}
