package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.remote.dto.ChatCompletionResponseDto
import com.example.fitlog.data.remote.dto.ChoiceDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.model.ai.CoachInsightContext
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.testing.FakeAIApi
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * [CoachInsightRepository] 的单元测试。
 *
 * 网络层用 [FakeAIApi] 替身，配置/缓存层用真实内存 Room + 临时 DataStore，
 * 验证 JSON mode 请求装配、容错解析、指纹缓存（命中零网络/变化重发）与错误兜底。
 */
@RunWith(RobolectricTestRunner::class)
class CoachInsightRepositoryTest {

    /** 每个测试方法使用独立的临时目录存放 DataStore 文件。 */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var fakeApi: FakeAIApi
    private lateinit var providerConfigRepo: AIProviderConfigRepository
    private lateinit var repository: CoachInsightRepository

    /**
     * 测试调度器：与 DataStore scope 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    private val today = LocalDate.of(2026, 7, 27)

    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("insight_prefs.preferences_pb"),
            dataStoreScope,
        )
        providerConfigRepo = AIProviderConfigRepository(db.aiProviderConfigDao(), dataStore)
        fakeApi = FakeAIApi()
        repository = CoachInsightRepository(
            aiChatRepository = AIChatRepository(fakeApi, providerConfigRepo),
            providerConfigRepo = providerConfigRepo,
            dataStore = dataStore,
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
    }

    // ── 夹具 ──

    private fun insightJson(action: String = "START_WORKOUT") = """
        {"observation":"昨天练了腿，今天需要中等强度","recommendation":"建议 30 分钟 Zone 2 有氧","action":"$action"}
    """.trimIndent()

    private fun respondInsight(action: String = "START_WORKOUT") {
        fakeApi.chatHandler = {
            ChatCompletionResponseDto(
                choices = listOf(ChoiceDto(message = MessageDto(role = "assistant", content = insightJson(action)))),
            )
        }
    }

    private fun insightContext(weekCompleted: Int = 1) = CoachInsightContext(
        profile = null,
        weekCompleted = weekCompleted,
        weekTarget = 4,
        todayCompleted = false,
        activePlan = WorkoutPlan(
            id = "plan-1",
            name = "增肌计划",
            description = null,
            goal = null,
            durationWeeks = 4,
            sessionsPerWeek = 3,
            isCustom = false,
            createdAt = today,
            rawPlanText = null,
            sessions = emptyList(),
        ),
        nextSession = PlannedSession(
            id = "w2d1",
            name = "腿日 · 股四头后侧链",
            description = null,
            dayNumber = 1,
            weekNumber = 2,
            targetDurationMinutes = 60,
            exercises = listOf(PlannedExerciseItem(exerciseKey = "barbell-squat", targetSets = 4, order = 0)),
        ),
        recentWorkouts = listOf(
            Workout(id = 42L, userId = 0L, date = today.minusDays(1), exercises = emptyList(), feelings = null),
        ),
        catalog = emptyList(),
        today = today,
    )

    private suspend fun activateProvider() {
        providerConfigRepo.insert(
            AIProviderConfig(
                id = ProviderType.OPENAI.name,
                name = "OpenAI",
                type = ProviderType.OPENAI,
                baseUrl = "https://api.openai.com",
                apiKey = "sk-test",
                model = "gpt-test",
                customEndpoint = null,
                apiVersion = null,
                isPreset = true,
            ),
        )
        providerConfigRepo.setActiveProviderId(ProviderType.OPENAI.name)
    }

    // ── aiAvailable ──

    @Test
    fun testAiAvailable_reflectsActiveProvider() = runTest(testScheduler) {
        assertEquals(false, repository.aiAvailable.first())
        activateProvider()
        assertEquals(true, repository.aiAvailable.first())
    }

    // ── 无服务商 ──

    @Test
    fun testGetAiInsight_noProvider_returnsFailureWithoutNetwork() = runTest(testScheduler) {
        val result = repository.getAiInsight(insightContext())

        assertTrue(result.isFailure)
        assertTrue(fakeApi.chatCalls.isEmpty())
    }

    // ── 成功链路 ──

    @Test
    fun testGetAiInsight_success_parsesInsight() = runTest(testScheduler) {
        activateProvider()
        respondInsight(action = "REST")

        val result = repository.getAiInsight(insightContext())

        assertTrue(result.isSuccess)
        val insight = result.getOrNull()
        assertNotNull(insight)
        assertEquals("昨天练了腿，今天需要中等强度", insight?.observation)
        assertEquals("建议 30 分钟 Zone 2 有氧", insight?.recommendation)
        assertEquals(CoachAction.REST, insight?.action)
    }

    @Test
    fun testGetAiInsight_assemblesJsonModeRequest() = runTest(testScheduler) {
        activateProvider()
        respondInsight()

        repository.getAiInsight(insightContext())

        val call = fakeApi.chatCalls[0]
        assertEquals("json_object", call.request.responseFormat?.type)
        assertEquals(300, call.request.maxTokens)
        assertEquals(0.7, call.request.temperature)
        // system 约束 + user 上下文（含今日课次名）
        assertEquals("system", call.request.messages[0].role)
        assertEquals("user", call.request.messages[1].role)
        assertTrue(call.request.messages[1].content.orEmpty().contains("腿日 · 股四头后侧链"))
    }

    // ── 指纹缓存 ──

    @Test
    fun testGetAiInsight_cacheHit_avoidsNetwork() = runTest(testScheduler) {
        activateProvider()
        respondInsight()

        repository.getAiInsight(insightContext())
        repository.getAiInsight(insightContext())

        assertEquals(1, fakeApi.chatCalls.size)
    }

    @Test
    fun testGetAiInsight_fingerprintChange_triggersNewRequest() = runTest(testScheduler) {
        activateProvider()
        respondInsight()

        repository.getAiInsight(insightContext(weekCompleted = 1))
        repository.getAiInsight(insightContext(weekCompleted = 2))

        assertEquals(2, fakeApi.chatCalls.size)
    }

    // ── 错误兜底 ──

    @Test
    fun testGetAiInsight_unparseableResponse_returnsFailureAndDoesNotCache() = runTest(testScheduler) {
        activateProvider()
        fakeApi.chatHandler = {
            ChatCompletionResponseDto(
                choices = listOf(ChoiceDto(message = MessageDto(role = "assistant", content = "今天加油练！"))),
            )
        }

        val first = repository.getAiInsight(insightContext())
        val second = repository.getAiInsight(insightContext())

        assertTrue(first.isFailure)
        assertTrue(second.isFailure)
        // 未写缓存 → 第二次仍然走网络
        assertEquals(2, fakeApi.chatCalls.size)
    }
}
