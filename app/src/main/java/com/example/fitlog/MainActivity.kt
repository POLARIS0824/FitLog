package com.example.fitlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.feature.aisettings.AISettingsRoute
import com.example.fitlog.feature.chat.ChatRoute
import com.example.fitlog.feature.stats.StatsRoute
import com.example.fitlog.feature.today.TodayRoute
import com.example.fitlog.feature.workout.WorkoutRoute
import com.example.fitlog.ui.settings.SettingsRoute
import com.example.fitlog.ui.settings.AboutRoute
import com.example.fitlog.ui.settings.appearance.AppearanceRoute
import com.example.fitlog.ui.settings.dataimport.DataImportRoute
import com.example.fitlog.ui.navigation.AboutKey
import com.example.fitlog.ui.navigation.AISettingsKey
import com.example.fitlog.ui.navigation.AppearanceKey
import com.example.fitlog.ui.navigation.ChatKey
import com.example.fitlog.ui.navigation.DataImportKey
import com.example.fitlog.ui.navigation.ProfileKey
import com.example.fitlog.ui.navigation.ReminderKey
import com.example.fitlog.ui.navigation.SettingsKey
import com.example.fitlog.ui.navigation.StatsKey
import com.example.fitlog.ui.navigation.TodayKey
import com.example.fitlog.ui.navigation.WorkoutKey
import com.example.fitlog.ui.settings.profile.ProfileRoute
import com.example.fitlog.ui.settings.reminder.ReminderRoute
import com.example.fitlog.ui.theme.FitLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        // installSplashScreen 必须在 super.onCreate 之前接管 starting window
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Splash 保持到"外观已加载 + 种子完成"（isReady 必须 Eagerly，见 MainViewModel）
        splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }

        // 兜底：放行条件靠逐帧 preDraw 重估，isReady 翻转本身不调度帧——
        // 启动期 Compose 状态发射实践上保证有帧，此处防极端无帧场景
        lifecycleScope.launch {
            viewModel.isReady.filter { it }.first()
            window.decorView.invalidate()
        }

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
                // 启动页为 Today 主页；设置族页面经 Today 顶栏设置图标进入
                val backStack = rememberNavBackStack(TodayKey)

                NavDisplay(
                    backStack = backStack,
                    // 根页（栈仅剩一个 entry）时没有可弹的页面：
                    // 让位给系统默认行为 finish()，否则系统返回键按下后白屏
                    onBack = {
                        if (backStack.size > 1) backStack.removeLastOrNull() else finish()
                    },
                    entryProvider = entryProvider {
                        entry<TodayKey> {
                            TodayRoute(
                                onNavigateToSettings = { backStack.add(SettingsKey) },
                                onNavigateToWorkout = { backStack.add(WorkoutKey) },
                                onNavigateToStats = { backStack.add(StatsKey) },
                                onNavigateToChat = { backStack.add(ChatKey) },
                            )
                        }
                        entry<ChatKey> {
                            ChatRoute(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<WorkoutKey> {
                            WorkoutRoute()
                        }
                        entry<StatsKey> {
                            StatsRoute(onBack = { backStack.removeLastOrNull() })
                        }
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
