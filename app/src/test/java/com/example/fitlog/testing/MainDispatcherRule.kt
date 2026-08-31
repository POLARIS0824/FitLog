package com.example.fitlog.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Main 调度器测试规则：统一收口各 ViewModel 测试重复的样板——
 *
 * ```
 * private val testScheduler = TestCoroutineScheduler()
 * private lateinit var dataStoreScope: TestScope
 *
 * @Before fun setUp() {
 *     Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
 *     ...
 * }
 * @After fun tearDown() { ...; Dispatchers.resetMain() }
 * ```
 *
 * 收敛为一条规则：
 *
 * ```
 * @get:Rule val main = MainDispatcherRule()
 * ```
 *
 * - `main.scheduler` 与 `runTest(...)` 共享同一调度器（虚拟时间一致）；
 * - Main dispatcher 在测试生命周期内自动 setMain/resetMain；
 * - DataStore 等需要共享调度器作用域的场景用
 *   `TestScope(UnconfinedTestDispatcher(main.scheduler))`（见
 *   [createTestPreferencesDataStore] KDoc 的约束）。
 *
 * 既有测试按文件逐步迁移即可，未迁移的文件行为不变。
 *
 * @param scheduler 显式传入时由调用方持有引用（如与既有 runTest 参数共享）；
 *   默认自建，经 [scheduler] 访问。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) : TestWatcher() {

    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    /** 与 [scheduler] 共享调度器的 TestScope（DataStore 内部协程作用域等场景）。 */
    fun testScope(): TestScope = TestScope(UnconfinedTestDispatcher(scheduler))

    override fun starting(description: Description) {
        Dispatchers.setMain(mainDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
