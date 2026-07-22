package com.example.fitlog.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 应用的导航 Key 集合（Navigation3）。
 *
 * 均为无参目的地，故使用 data object；
 * 带参数的页面（如将来的训练详情页）改为
 * `@Serializable data class XxxKey(val id: Long) : NavKey`。
 *
 * `@Serializable` 是 `rememberNavBackStack` 持久化回退栈的前提。
 */

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
