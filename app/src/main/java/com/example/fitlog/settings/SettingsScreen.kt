package com.example.fitlog.settings

import android.os.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.data.settings.ThemeMode
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.ui.components.*
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews

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
    onDiarySettings: () -> Unit,
    onRetryVault: () -> Unit,
    onBack: () -> Unit,
) {
    FitLogSettingsPage(stringResource(R.string.settings_title), onBack) {
        item {
            SettingsSectionTitle(stringResource(R.string.settings_personalization_heading))
            SettingsGroup {
                SettingsEntry(R.drawable.palette_24px, stringResource(R.string.appearance_title),
                    if (appearanceReady) stringResource(R.string.appearance_summary, themeModeLabel(appearance.themeMode),
                        stringResource(if (appearance.dynamicColor && Build.VERSION.SDK_INT >= 31)
                            R.string.appearance_dynamic_on else R.string.appearance_dynamic_off))
                    else stringResource(R.string.appearance_summary_unavailable), onAppearance, tone = 2)
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.settings_analysis_heading))
            SettingsGroup {
                SettingsEntry(R.drawable.auto_awesome_24px, stringResource(R.string.ai_settings_title),
                    stringResource(R.string.settings_ai_description), onAiSettings)
            }
        }
        item {
            SettingsSectionTitle(stringResource(R.string.settings_vault_heading))
            SettingsGroup {
                SettingsEntry(R.drawable.folder_open_24px, stringResource(R.string.vault_management_title),
                    stringResource(R.string.settings_vault_description), onVaultManagement, tone = 1)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsEntry(R.drawable.note_add_24px, stringResource(R.string.diary_settings_title),
                    stringResource(when (config) {
                        is VaultConfigState.Configured -> R.string.settings_diary_description
                        is VaultConfigState.Failed -> R.string.vault_error_load_config_failed
                        VaultConfigState.Loading -> R.string.vault_setup_loading_config
                        VaultConfigState.NotConfigured -> R.string.settings_diary_requires_vault
                    }), onDiarySettings, enabled = config is VaultConfigState.Configured, tone = 2)
                if (config is VaultConfigState.Failed) TextButton(onClick = onRetryVault) {
                    Text(stringResource(R.string.log_retry))
                }
            }
        }
    }
}

@FitLogPreviews
@Composable
private fun SettingsPreview() {
    FitLogPreview { SettingsScreen(AppearancePreferences(), true, VaultConfigState.NotConfigured,
        {}, {}, {}, {}, {}, {}) }
}
