package com.example.fitlog.feature.settings

import android.os.Build
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.ui.components.FitLogSettingsPage
import com.example.fitlog.ui.components.SettingsEntry
import com.example.fitlog.ui.components.SettingsGroup
import com.example.fitlog.ui.components.SettingsSectionTitle

@Composable
internal fun themeModeLabel(mode: ThemeMode): String = stringResource(when (mode) {
    ThemeMode.SYSTEM -> R.string.appearance_system
    ThemeMode.LIGHT -> R.string.appearance_light
    ThemeMode.DARK -> R.string.appearance_dark
})

@Composable
internal fun SettingsScreen(
    appearance: AppearancePreferences,
    appearanceReady: Boolean,
    config: VaultConfigState,
    onAppearance: () -> Unit,
    onAiSettings: () -> Unit,
    onVaultManagement: () -> Unit,
    onRetryVault: () -> Unit,
    onBack: () -> Unit,
) {
    FitLogSettingsPage(stringResource(R.string.settings_title), onBack) {
        item {
            SettingsSectionTitle(stringResource(R.string.settings_personalization_heading))
            SettingsGroup {
                SettingsEntry(R.drawable.palette_24px, stringResource(R.string.appearance_title),
                    if (appearanceReady && appearance.look == UiLook.LIQUID_GLASS) stringResource(R.string.look_liquid)
                    else if (appearanceReady && appearance.look == UiLook.GLASS_NAVIGATION) stringResource(R.string.look_glass_navigation)
                    else if (appearanceReady) stringResource(R.string.appearance_summary, themeModeLabel(appearance.themeMode),
                        stringResource(if (appearance.dynamicColor && Build.VERSION.SDK_INT >= 31)
                            R.string.appearance_dynamic_on else R.string.appearance_dynamic_off))
                    else stringResource(R.string.appearance_summary_unavailable), onAppearance, tone = 2)
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.settings_data_analysis_heading))
            SettingsGroup {
                SettingsEntry(R.drawable.auto_awesome_24px, stringResource(R.string.ai_settings_title),
                    stringResource(R.string.settings_ai_description), onAiSettings)
                SettingsEntry(R.drawable.folder_open_24px, stringResource(R.string.vault_management_title),
                    stringResource(when (config) {
                        VaultConfigState.Loading -> R.string.vault_setup_loading_config
                        VaultConfigState.NotConfigured -> R.string.log_no_vault
                        is VaultConfigState.Configured -> R.string.settings_ui_vault_connected
                        is VaultConfigState.Failed -> R.string.vault_error_load_config_failed
                    }), onVaultManagement, tone = 1)
                if (config is VaultConfigState.Failed) TextButton(onClick = onRetryVault, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.log_retry))
                }
            }
        }
    }
}
