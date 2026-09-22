package com.example.fitlog.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.file.MarkdownExporter
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.remote.dto.ChatCompletionResponseDto
import com.example.fitlog.data.remote.dto.ChoiceDto
import com.example.fitlog.data.remote.dto.MessageDto
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.testing.FakeAIApi
import com.example.fitlog.testing.MainDispatcherRule
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import com.example.fitlog.util.security.KeystoreManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [WorkoutParseRepository] 的时间往返解析与异常防御单元测试。
 *
 * 验证：
 * 1. 同日正常训练 export -> import -> export 时间戳（startedAt / endedAt）完全一致；
 * 2. 跨午夜训练 export -> import -> export 时间戳完全一致，跨天日期正确；
 * 3. 跨年跨午夜训练 export -> import -> export 年份、月份、日期均正确保留；
 * 4. 时区独立性：在不同时区解析同一份导出文本，epoch millis 保持一致；
 * 5. 兼容未携带 ISO 元数据的旧 Markdown，继续走已有解析逻辑；
 * 6. 异常防御：结束时间早于开始时间抛出异常，格式损坏抛出异常；
 * 7. AI 干扰防御：即便 AI 返回不同/错误的时间，确定性本地逻辑也能正确覆盖；
 * 8. 缺失时间戳（"空"）正确解析为 null。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkoutParseRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fakeAIApi: FakeAIApi
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var aiChatRepository: AIChatRepository
    private lateinit var repository: WorkoutParseRepository

    private val testZone = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() = runTest(main.scheduler) {
        FakeAndroidKeyStoreProvider.setup()
        context = ApplicationProvider.getApplicationContext()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        exerciseRepository = ExerciseRepository(db.exerciseDao())
        db.exerciseDao().upsertAllPreservingRows(
            listOf(
                ExerciseEntity(id = "barbell-bench-press", name = "杠铃卧推"),
                ExerciseEntity(id = "barbell-squat", name = "深蹲"),
            ),
        )

        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("workout_parse_test_prefs.preferences_pb"),
            main.testScope(),
        )
        val providerConfigRepo = AIProviderConfigRepository(
            aiProviderConfigDao = db.aiProviderConfigDao(),
            dataStore = dataStore,
        )

        db.aiProviderConfigDao().insert(
            AIProviderConfigEntity(
                id = "test-provider",
                type = ProviderType.OPENAI.name,
                name = "Test Provider",
                baseUrl = "https://api.test.com/v1",
                encryptedApiKey = KeystoreManager.encrypt("valid-test-key"),
                model = "test-model",
                customEndpoint = null,
                apiVersion = null,
                isPreset = false,
            ),
        )
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("active_ai_provider_id")] = "test-provider"
        }

        fakeAIApi = FakeAIApi()
        // 默认 AI 响应：包含杠铃卧推动作明细，动态保留输入中的感受/备注
        fakeAIApi.chatHandler = { call ->
            val userContent = call.request.messages.lastOrNull()?.content.orEmpty()
            val feelingsMatch = """- 感受：(.+)""".toRegex().find(userContent)
                ?: """感受：(.+)""".toRegex().find(userContent)
            val feelings = feelingsMatch?.groupValues?.get(1)?.trim() ?: "状态良好"
            createAiResponse(
                startTime = "10:00",
                endTime = "11:00",
                exerciseName = "杠铃卧推",
                feelings = feelings,
            )
        }

        aiChatRepository = AIChatRepository(fakeAIApi, providerConfigRepo)
        repository = WorkoutParseRepository(aiChatRepository, exerciseRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createAiResponse(
        startTime: String?,
        endTime: String?,
        exerciseName: String,
        feelings: String = "状态良好",
    ) = ChatCompletionResponseDto(
        choices = listOf(
            ChoiceDto(
                message = MessageDto(
                    role = "assistant",
                    content = buildString {
                        append("{\n")
                        if (startTime != null) append("  \"startTime\": \"$startTime\",\n")
                        if (endTime != null) append("  \"endTime\": \"$endTime\",\n")
                        append("  \"feelings\": \"$feelings\",\n")
                        append("  \"exercises\": [\n")
                        append("    {\n")
                        append("      \"name\": \"$exerciseName\",\n")
                        append("      \"sets\": [{ \"weightKg\": 80.0, \"reps\": 10, \"type\": \"WORKING\" }]\n")
                        append("    }\n")
                        append("  ]\n")
                        append("}")
                    },
                ),
            ),
        ),
    )

    @Test
    fun `same day workout round trip preserves exact timestamps`() = runTest(main.scheduler) {
        val date = LocalDate.of(2026, 5, 20)
        val startEpoch = ZonedDateTime.of(2026, 5, 20, 9, 0, 0, 0, testZone).toInstant().toEpochMilli()
        val endEpoch = ZonedDateTime.of(2026, 5, 20, 10, 30, 0, 0, testZone).toInstant().toEpochMilli()

        val original = Workout(
            id = 1,
            userId = 0,
            date = date,
            feelings = "状态良好",
            startedAt = startEpoch,
            endedAt = endEpoch,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 80f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
        )

        val exportedMd = MarkdownExporter.export(listOf(original), zoneId = testZone)
        val parseResult = repository.parse(exportedMd, date)
        assertTrue(parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals(startEpoch, parsed.startedAt)
        assertEquals(endEpoch, parsed.endedAt)

        val reExportedMd = MarkdownExporter.export(listOf(parsed), zoneId = testZone)
        assertEquals(exportedMd, reExportedMd)
    }

    @Test
    fun `cross midnight workout round trip preserves exact timestamps and next-day date`() = runTest(main.scheduler) {
        val date = LocalDate.of(2026, 5, 20)
        val startEpoch = ZonedDateTime.of(2026, 5, 20, 23, 30, 0, 0, testZone).toInstant().toEpochMilli()
        val endEpoch = ZonedDateTime.of(2026, 5, 21, 1, 15, 0, 0, testZone).toInstant().toEpochMilli()

        val original = Workout(
            id = 1,
            userId = 0,
            date = date,
            feelings = "练到深夜",
            startedAt = startEpoch,
            endedAt = endEpoch,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 80f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
        )

        val exportedMd = MarkdownExporter.export(listOf(original), zoneId = testZone)
        val parseResult = repository.parse(exportedMd, date)
        assertTrue(parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals(startEpoch, parsed.startedAt)
        assertEquals(endEpoch, parsed.endedAt)

        val reExportedMd = MarkdownExporter.export(listOf(parsed), zoneId = testZone)
        assertEquals(exportedMd, reExportedMd)
    }

    @Test
    fun `cross year cross midnight workout round trip preserves exact year month and day`() = runTest(main.scheduler) {
        val date = LocalDate.of(2026, 12, 31)
        val startEpoch = ZonedDateTime.of(2026, 12, 31, 23, 30, 0, 0, testZone).toInstant().toEpochMilli()
        val endEpoch = ZonedDateTime.of(2027, 1, 1, 1, 0, 0, 0, testZone).toInstant().toEpochMilli()

        val original = Workout(
            id = 1,
            userId = 0,
            date = date,
            feelings = "跨年训练",
            startedAt = startEpoch,
            endedAt = endEpoch,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 80f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
        )

        val exportedMd = MarkdownExporter.export(listOf(original), zoneId = testZone)
        val parseResult = repository.parse(exportedMd, date)
        assertTrue(parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals(startEpoch, parsed.startedAt)
        assertEquals(endEpoch, parsed.endedAt)

        val reExportedMd = MarkdownExporter.export(listOf(parsed), zoneId = testZone)
        assertEquals(exportedMd, reExportedMd)
    }

    @Test
    fun `timezone independence parsing preserves epoch millis across different zones`() {
        val startIso = "2026-05-20T23:30:00+08:00"
        val endIso = "2026-05-21T01:15:00+08:00"
        val content = """
            # 2026-05-20 训练
            - 开始时间：$startIso
            - 结束时间：$endIso
            - **杠铃卧推** 80kg x 10
        """.trimIndent()

        val meta = WorkoutParseRepository.extractIsoTimeMetadata(content)
        assertNotNull(meta)

        val expectedStart = ZonedDateTime.of(2026, 5, 20, 23, 30, 0, 0, testZone).toInstant().toEpochMilli()
        val expectedEnd = ZonedDateTime.of(2026, 5, 21, 1, 15, 0, 0, testZone).toInstant().toEpochMilli()

        assertEquals(expectedStart, meta!!.startedAt)
        assertEquals(expectedEnd, meta.endedAt)
    }

    @Test
    fun `legacy markdown without ISO metadata falls back to existing AI parsing logic`() = runTest(main.scheduler) {
        val date = LocalDate.of(2026, 5, 20)
        // 旧 Markdown：只有人类可读的时间窗口，没有 ISO 字段
        val legacyMd = """
            # 2026-05-20 训练
            - 感受：旧格式训练
            - 时间：10:00–11:00
            - **杠铃卧推** 80kg x 10
        """.trimIndent()

        val parseResult = repository.parse(legacyMd, date)
        assertTrue(parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        // 应该由 AI 返回的 10:00 - 11:00 换算为当日时间戳
        val expectedStart = date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expectedEnd = date.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedStart, parsed.startedAt)
        assertEquals(expectedEnd, parsed.endedAt)
    }

    @Test
    fun `exception defense - endedAt before startedAt throws IllegalArgumentException`() = runTest(main.scheduler) {
        val content = """
            # 2026-05-20 训练
            - 开始时间：2026-05-20T23:30:00+08:00
            - 结束时间：2026-05-20T22:30:00+08:00
            - **杠铃卧推** 80kg x 10
        """.trimIndent()

        val result = repository.parse(content, LocalDate.of(2026, 5, 20))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("结束时间早于开始时间"))
    }

    @Test
    fun `exception defense - corrupted ISO format throws IllegalArgumentException`() = runTest(main.scheduler) {
        val content = """
            # 2026-05-20 训练
            - 开始时间：not-a-valid-iso-string
            - 结束时间：2026-05-20T23:30:00+08:00
            - **杠铃卧推** 80kg x 10
        """.trimIndent()

        val result = repository.parse(content, LocalDate.of(2026, 5, 20))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("开始时间格式错误"))
    }

    @Test
    fun `AI interference defense - deterministic local logic overrides incorrect AI time`() = runTest(main.scheduler) {
        // AI 返回了完全错误的时间 08:00 - 09:00
        fakeAIApi.chatHandler = {
            createAiResponse(
                startTime = "08:00",
                endTime = "09:00",
                exerciseName = "杠铃卧推",
            )
        }

        val date = LocalDate.of(2026, 5, 20)
        val startEpoch = ZonedDateTime.of(2026, 5, 20, 23, 30, 0, 0, testZone).toInstant().toEpochMilli()
        val endEpoch = ZonedDateTime.of(2026, 5, 21, 1, 15, 0, 0, testZone).toInstant().toEpochMilli()

        val content = """
            # 2026-05-20 训练
            - 开始时间：2026-05-20T23:30:00+08:00
            - 结束时间：2026-05-21T01:15:00+08:00
            - **杠铃卧推** 80kg x 10
        """.trimIndent()

        val result = repository.parse(content, date)
        assertTrue(result.isSuccess)

        val parsed = result.getOrThrow()
        // 关键断言：确定性本地逻辑覆盖了 AI 输出的 08:00/09:00，严格采用 ISO 元数据中的时间戳
        assertEquals(startEpoch, parsed.startedAt)
        assertEquals(endEpoch, parsed.endedAt)
    }

    @Test
    fun `missing timestamps with placeholder empty are parsed as null`() = runTest(main.scheduler) {
        val content = """
            # 2026-05-20 训练
            - 开始时间：空
            - 结束时间：空
            - **杠铃卧推** 80kg x 10
        """.trimIndent()

        val result = repository.parse(content, LocalDate.of(2026, 5, 20))
        assertTrue(result.isSuccess)

        val parsed = result.getOrThrow()
        assertNull(parsed.startedAt)
        assertNull(parsed.endedAt)
    }
}
