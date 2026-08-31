package com.example.fitlog.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 应用的导航 Key 集合（Navigation3）。
 *
 * 页面默认无参，用 data object；带参数的页面用 `@Serializable data class`
 * （如 [WorkoutKey.autoStart]）。带默认值的参数保证旧版本持久化回退栈的
 * JSON 反序列化兼容（缺失字段取默认值）。
 *
 * `@Serializable` 是 `rememberNavBackStack` 持久化回退栈的前提。
 */

@Serializable
data object TodayKey : NavKey

@Serializable
data object ChatKey : NavKey

/**
 * 训练页。携带 [autoStart] 时进入即自动启动训练会话（Today「开始训练」）；
 * 无参打开仅查看历史记录（已有会话时无论何种入口都恢复会话视图）。
 */
@Serializable
data class WorkoutKey(val autoStart: Boolean = false) : NavKey

@Serializable
data object StatsKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AISettingsKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object AppearanceKey : NavKey

@Serializable
data object DataImportKey : NavKey

@Serializable
data object ReminderKey : NavKey

@Serializable
data object AboutKey : NavKey
