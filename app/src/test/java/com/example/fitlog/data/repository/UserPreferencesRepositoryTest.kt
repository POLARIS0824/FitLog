package com.example.fitlog.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [UserPreferencesRepository] 的单元测试。
 *
 * 使用指向临时文件的真实 DataStore 验证各项 UI 偏好的默认值与读写，
 * 包括损坏数据（非法主题字符串）的容错降级。
 */
class UserPreferencesRepositoryTest {

    /**
     * 每个测试方法使用独立的临时目录，保证 DataStore 文件互不冲突。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var repository: UserPreferencesRepository
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    /**
     * 测试调度器：与 DataStore scope 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 初始化临时文件 DataStore 与仓库实例。
     */
    @Before
    fun setUp() {
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("prefs_test.preferences_pb"),
            dataStoreScope,
        )
        repository = UserPreferencesRepository(dataStore)
    }

    /**
     * 取消 DataStore 作用域，避免写协程跨测试悬空。
     */
    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    /**
     * 测试未写入任何偏好时的默认值：
     * 主题跟随系统、动态取色开启、提醒关闭、提醒时间 18:00（1080 分钟）。
     */
    @Test
    fun testDefaults() = runTest(testScheduler) {
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        assertEquals(true, repository.dynamicColor.first())
        assertEquals(false, repository.reminderEnabled.first())
        assertEquals(18 * 60, repository.reminderMinutes.first())
    }

    /**
     * 测试主题模式的写入与读取。
     */
    @Test
    fun testSetThemeMode() = runTest(testScheduler) {
        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.themeMode.first())

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    /**
     * 测试存储了非法主题字符串时降级为 SYSTEM（而非崩溃）。
     */
    @Test
    fun testInvalidThemeModeValue_fallsBackToSystem() = runTest(testScheduler) {
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "NEON_GLOW" }

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    /**
     * 测试动态取色开关的写入与读取。
     */
    @Test
    fun testSetDynamicColor() = runTest(testScheduler) {
        repository.setDynamicColor(false)
        assertEquals(false, repository.dynamicColor.first())
    }

    /**
     * 测试训练提醒开关与时间的写入与读取。
     */
    @Test
    fun testSetReminder() = runTest(testScheduler) {
        repository.setReminderEnabled(true)
        repository.setReminderMinutes(7 * 60 + 30)

        assertEquals(true, repository.reminderEnabled.first())
        assertEquals(7 * 60 + 30, repository.reminderMinutes.first())
    }
}
