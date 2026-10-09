package com.example.fitlog.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.data.settings.*
import java.io.File
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
class AppearancePreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun absentKeysUseDefaultsAndSavedChoicesSurviveNewRepository() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "appearance.preferences_pb") })
        val settings = AppearancePreferencesStore(store)
        assertEquals(AppearancePreferences(ThemeMode.SYSTEM, true), settings.settings.first())
        settings.save(AppearancePreferences(ThemeMode.DARK, false))
        assertEquals(AppearancePreferences(ThemeMode.DARK, false), AppearancePreferencesStore(store).settings.first())
        settings.save(AppearancePreferences(ThemeMode.LIGHT, true))
        assertEquals(AppearancePreferences(ThemeMode.LIGHT, true), settings.settings.first())
    }

    @Test fun invalidStoredModeIsReportedWithoutRepairingOrReplacingData() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { File(folder.root, "appearance.preferences_pb") })
        val key = stringPreferencesKey("theme_mode")
        store.edit { it[key] = "invalid" }
        assertTrue(runCatching { AppearancePreferencesStore(store).settings.first() }.exceptionOrNull() is IllegalArgumentException)
        assertEquals("invalid", store.data.first()[key])
    }
}
