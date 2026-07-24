package com.example.fitlog.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File

/**
 * 创建测试专用的 Preferences [DataStore]。
 *
 * 与生产环境通过 `preferencesDataStore` 委托创建单例不同，
 * 测试直接用 [PreferenceDataStoreFactory] 指向临时文件：
 * - 每个测试方法使用独立临时文件，互不影响
 * - scope 使用 [UnconfinedTestDispatcher]，读写立即完成，无需手动推进调度器
 *
 * 注意：同一个文件在 JVM 进程内只能有一个活跃 DataStore 实例，
 * 因此调用方必须保证每个测试方法传入不同的文件（如用 TemporaryFolder 规则）。
 *
 * @param file 存储偏好数据的临时文件
 * @return 测试用 DataStore 实例
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createTestPreferencesDataStore(file: File): DataStore<Preferences> {
    val scope: CoroutineScope = TestScope(UnconfinedTestDispatcher())
    return PreferenceDataStoreFactory.create(scope = scope) { file }
}
