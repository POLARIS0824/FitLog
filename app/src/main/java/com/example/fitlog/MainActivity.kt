package com.example.fitlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.feature.aisettings.AISettingsRoute
import com.example.fitlog.ui.settings.SettingsRoute
import com.example.fitlog.ui.settings.AboutRoute
import com.example.fitlog.ui.settings.appearance.AppearanceRoute
import com.example.fitlog.ui.settings.dataimport.DataImportRoute
import com.example.fitlog.ui.navigation.AboutKey
import com.example.fitlog.ui.navigation.AISettingsKey
import com.example.fitlog.ui.navigation.AppearanceKey
import com.example.fitlog.ui.navigation.DataImportKey
import com.example.fitlog.ui.navigation.ProfileKey
import com.example.fitlog.ui.navigation.ReminderKey
import com.example.fitlog.ui.navigation.SettingsKey
import com.example.fitlog.ui.settings.profile.ProfileRoute
import com.example.fitlog.ui.settings.reminder.ReminderRoute
import com.example.fitlog.ui.theme.FitLogTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用主入口 Activity。
 *
 * 导航采用 Navigation3：回退栈即状态（[rememberNavBackStack] 持久化），
 * 导航 = 对 backStack 的增删操作。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 外观设置（主题模式 + 动态取色）实时驱动主题
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val (themeMode, dynamicColor) = appearance
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FitLogTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                // 回退栈：rememberNavBackStack 跨配置更改/进程死亡持久化（key 需 @Serializable）
                val backStack = rememberNavBackStack(SettingsKey)

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<SettingsKey> {
                            SettingsRoute(
                                onBack = { backStack.removeLastOrNull() },
                                onNavigateToProfile = { backStack.add(ProfileKey) },
                                onNavigateToAppearance = { backStack.add(AppearanceKey) },
                                onNavigateToAISettings = { backStack.add(AISettingsKey) },
                                onNavigateToDataImport = { backStack.add(DataImportKey) },
                                onNavigateToReminder = { backStack.add(ReminderKey) },
                                onNavigateToAbout = { backStack.add(AboutKey) },
                            )
                        }
                        entry<AISettingsKey> {
                            AISettingsRoute(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<ProfileKey> {
                            ProfileRoute(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<AppearanceKey> {
                            AppearanceRoute(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<DataImportKey> {
                            DataImportRoute(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<ReminderKey> {
                            ReminderRoute(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<AboutKey> {
                            AboutRoute(onBack = { backStack.removeLastOrNull() })
                        }
                    },
                )
            }
        }
    }
}
