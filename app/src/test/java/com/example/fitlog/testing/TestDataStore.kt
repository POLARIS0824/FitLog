package com.example.fitlog.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * 创建测试专用的 Preferences [DataStore]。
 *
 * 与生产环境通过 `preferencesDataStore` 委托创建单例不同，
 * 测试直接用 [PreferenceDataStoreFactory] 指向临时文件：
 * - 每个测试方法使用独立临时文件，互不影响
 * - scope 由调用方显式传入，**必须与测试共享同一
 *   [kotlinx.coroutines.test.TestCoroutineScheduler]**：
 *   - 在 `@Before` 中创建 DataStore 的测试类，传入
 *     `TestScope(UnconfinedTestDispatcher(testScheduler))`，并让测试方法
 *     `runTest(testScheduler)` 复用同一调度器；
 *   - 在 `runTest` 内部创建 DataStore 的测试，直接传入 `backgroundScope`。
 * - 调用方必须在测试结束时（`@After`）取消该 scope（或依赖 `backgroundScope`
 *   在 `runTest` 结束时自动取消），保证 DataStore 的读写协程不会跨测试悬空，
 *   否则写盘协程可能在与测试无关的调度器上迟迟不完成，与临时目录清理竞态，
 *   产生 "Unable to rename ... multiple instances of DataStore for this file"
 *   与 [kotlinx.coroutines.test.UncompletedCoroutinesError]。
 *
 * 注意：同一个文件在 JVM 进程内只能有一个活跃 DataStore 实例，
 * 因此调用方必须保证每个测试方法传入不同的文件（如用 TemporaryFolder 规则）。
 *
 * @param file 存储偏好数据的临时文件
 * @param scope DataStore 内部协程的作用域（与测试共享调度器，测试结束时取消）
 * @return 测试用 DataStore 实例
 */
fun createTestPreferencesDataStore(
    file: File,
    scope: CoroutineScope,
): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(scope = scope) { file }
}
