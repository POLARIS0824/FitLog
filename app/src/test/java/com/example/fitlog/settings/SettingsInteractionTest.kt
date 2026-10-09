package com.example.fitlog.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ai.AiSettingsActions
import com.example.fitlog.ai.AiSettingsContent
import com.example.fitlog.ai.AiSettingsUiState
import com.example.fitlog.ai.ConnectionPicker
import com.example.fitlog.data.ai.AiModelSelection
import com.example.fitlog.data.ai.AiProviderConnection
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.ui.components.FitLogSettingsPage
import com.example.fitlog.ui.theme.FitLogTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private fun text(id: Int) = context.getString(id)

    @Test fun diaryEntryStaysDisabledUntilVaultIsConfiguredAndOtherEntriesNavigate() {
        val config = mutableStateOf<VaultConfigState>(VaultConfigState.NotConfigured)
        var appearance = 0; var ai = 0; var management = 0; var diary = 0
        render {
            SettingsScreen(AppearancePreferences(), true, config.value,
                { appearance++ }, { ai++ }, { management++ }, { diary++ }, {}, {})
        }
        compose.onNodeWithText(text(R.string.diary_settings_title)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.appearance_title)).performClick()
        compose.onNodeWithText(text(R.string.ai_settings_title)).performClick()
        compose.onNodeWithText(text(R.string.vault_management_title)).performClick()
        assertEquals(listOf(1, 1, 1, 0), listOf(appearance, ai, management, diary))
        snapshot("settings-light")
        compose.runOnIdle {
            config.value = VaultConfigState.Configured(android.net.Uri.parse("content://vault"),
                "00000000-0000-4000-8000-000000000001")
        }
        compose.onNodeWithText(text(R.string.diary_settings_title)).assertIsEnabled().performClick()
        assertEquals(1, diary)
    }

    @Test
    @Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp")
    fun darkAppearanceSelectionAndSwitchHaveOneActionPerRow() {
        var mode: ThemeMode? = null
        var dynamic: Boolean? = null
        render(dark = true) {
            AppearanceContent(AppearancePreferences(ThemeMode.DARK, true), true, false, null,
                { mode = it }, { dynamic = it }, {}, {})
        }
        compose.onNodeWithText(text(R.string.appearance_dark)).assertIsSelected()
        compose.onNodeWithText(text(R.string.appearance_light)).performClick()
        assertEquals(ThemeMode.LIGHT, mode)
        compose.onNodeWithText(text(R.string.appearance_dynamic_color)).performClick()
        assertEquals(false, dynamic)
        snapshot("appearance-dark")
    }

    @Test fun unsupportedDynamicColorExplainsAndDisablesTheSwitch() {
        render {
            AppearanceContent(AppearancePreferences(), true, false, null, {}, { error("Disabled switch") }, {}, {},
                dynamicColorSupported = false)
        }
        compose.onNodeWithText(text(R.string.appearance_dynamic_color)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.appearance_dynamic_unsupported)).assertIsDisplayed()
    }

    @Test fun aiConnectionSheetSelectsSavedIdentityAndStartsANewCustomConnection() {
        var chosen: String? = null
        var newName: String? = null
        val providers = connections(2)
        render {
            AiSettingsContent(AiSettingsUiState(providers = providers, providerId = providers[0].id,
                name = providers[0].name, apiKey = "preview-key", modelIds = listOf("model-a"), selectedModel = "model-a",
                currentSelection = AiModelSelection(providers[0].id, "model-a")),
                AiSettingsActions(chooseProvider = { chosen = it }, newConnection = { name, _ -> newName = name }), {})
        }
        snapshot("ai-light")
        compose.onNodeWithText(text(R.string.ai_editing_connection)).performClick()
        compose.onNodeWithText(providers[1].name).performClick()
        assertEquals(providers[1].id, chosen)
        compose.onNodeWithText(text(R.string.ai_editing_connection)).performClick()
        compose.onNodeWithText(text(R.string.ai_add_custom)).performScrollTo().performClick()
        assertEquals(text(R.string.ai_custom_connection), newName)
    }

    @Test fun longConnectionPickerCanReachLastConnectionAndBothCreationActions() {
        var chosen: String? = null
        var deepSeek = 0; var custom = 0
        val providers = connections(30)
        render {
            ConnectionPicker(providers, providers[0].id, true, { chosen = it }, { deepSeek++ }, { custom++ })
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(providers.last().name))
        compose.onNodeWithText(providers.last().name).performClick()
        assertEquals(providers.last().id, chosen)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.ai_add_custom)))
        compose.onNodeWithText(text(R.string.ai_add_deepseek)).performClick()
        compose.onNodeWithText(text(R.string.ai_add_custom)).performClick()
        assertEquals(1, deepSeek); assertEquals(1, custom)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h480dp")
    fun largeTextCanReachTheLastAiSaveAction() {
        var saves = 0
        render(fontScale = 2f) {
            AiSettingsContent(AiSettingsUiState(name = "Connection", apiKey = "preview-key",
                modelIds = listOf("model-a"), selectedModel = "model-a", canSave = true),
                AiSettingsActions(save = { saves++ }), {})
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.ai_save_selection)))
        compose.onNodeWithText(text(R.string.ai_save_selection)).assertIsDisplayed().performClick()
        assertEquals(1, saves)
        snapshot("ai-large-text")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h480dp")
    fun shortPageKeepsItsFinalActionReachableAfterScrollAndSnap() {
        var clicks = 0
        render {
            FitLogSettingsPage(text(R.string.settings_title), {}) {
                item { Text(text(R.string.settings_ai_description)) }
                item { Button(onClick = { clicks++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(text(R.string.ai_save_selection))
                } }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText(text(R.string.ai_save_selection)).assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    private fun connections(count: Int) = (0 until count).map {
        AiProviderConnection("connection-$it", "Connection ${it.toString().padStart(2, '0')}",
            "https://api.deepseek.com", listOf("model-a"))
    }

    private fun render(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FitLogTheme(darkTheme = dark, dynamicColor = false) { Surface(Modifier.fillMaxSize(), content = content) }
            }
        }
    }

    private fun snapshot(name: String) {
        val target = File("build/settings-previews/$name.png")
        requireNotNull(target.parentFile).mkdirs()
        target.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
