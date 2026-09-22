package com.example.fitlog.ui.settings.dataimport

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.AIProviderConfigEntity
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.data.repository.AIChatRepository
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.WorkoutParseRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.testing.FakeAIApi
import com.example.fitlog.testing.MainDispatcherRule
import com.example.fitlog.testing.createTestPreferencesDataStore
import com.example.fitlog.util.security.FakeAndroidKeyStoreProvider
import com.example.fitlog.util.security.KeystoreManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * [MarkdownFileScanner] 的测试替身：直接返回预设的扫描结果。
 */
private class FakeMarkdownFileScanner : MarkdownFileScanner() {
    var scanResult: ScanResult = ScanResult(emptyList(), emptyList())

    override fun scanFolder(contentResolver: ContentResolver, treeUri: Uri): ScanResult {
        return scanResult
    }
}

/**
 * [WorkoutParseRepository] 的测试替身：提供可编程的解析与动作库 key 反查行为。
 */
private class FakeWorkoutParseRepository(
    aiChatRepository: AIChatRepository,
    exerciseRepository: ExerciseRepository,
) : WorkoutParseRepository(aiChatRepository, exerciseRepository) {

    var parseHandler: (suspend (String, LocalDate) -> Result<Workout>)? = null
    var resolveHandler: (suspend (String) -> String?)? = null

    override suspend fun parse(content: String, dateHint: LocalDate): Result<Workout> {
        return parseHandler?.invoke(content, dateHint) ?: super.parse(content, dateHint)
    }

    override suspend fun resolveExerciseKey(name: String): String? {
        return resolveHandler?.invoke(name) ?: super.resolveExerciseKey(name)
    }
}

/**
 * [DataImportViewModel] 的编辑草稿隔离与取消语义单元测试。
 *
 * 验证：
 * 1. 改重量仅更新编辑缓冲，保存前原草稿完全不变，保存后生效；
 * 2. 删动作仅更新编辑缓冲，保存前原草稿完全不变，保存后生效；
 * 3. 取消并重新打开丢弃全部未保存修改；
 * 4. 保存成功完成动作库匹配并提交给原条目；
 * 5. 保存失败保留编辑缓冲供重试，原条目不被污染；
 * 6. 异步保存结果不能写入用户后来打开的另一条记录；
 * 7. 正在保存时取消不写入原条目。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataImportViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var providerConfigRepository: AIProviderConfigRepository
    private lateinit var fakeScanner: FakeMarkdownFileScanner
    private lateinit var fakeWorkoutParseRepo: FakeWorkoutParseRepository
    private lateinit var viewModel: DataImportViewModel

    @Before
    fun setUp() = runTest(main.scheduler) {
        FakeAndroidKeyStoreProvider.setup()
        context = ApplicationProvider.getApplicationContext()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        workoutRepository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseLogDao = db.exerciseLogDao(),
            setLogDao = db.setLogDao(),
            workoutPlanDao = db.workoutPlanDao(),
            db = db,
        )
        exerciseRepository = ExerciseRepository(db.exerciseDao())

        // 插入基础测试动作
        db.exerciseDao().upsertAllPreservingRows(
            listOf(
                ExerciseEntity(id = "barbell-bench-press", name = "杠铃卧推"),
                ExerciseEntity(id = "pull-up", name = "引体向上"),
                ExerciseEntity(id = "dumbbell-bench-press", name = "哑铃卧推"),
                ExerciseEntity(id = "barbell-squat", name = "深蹲"),
            ),
        )

        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("data_import_test_prefs.preferences_pb"),
            main.testScope(),
        )
        providerConfigRepository = AIProviderConfigRepository(
            aiProviderConfigDao = db.aiProviderConfigDao(),
            dataStore = dataStore,
        )

        // 配置激活的 AI 服务商
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

        val fakeAIApi = FakeAIApi()
        val aiChatRepository = AIChatRepository(fakeAIApi, providerConfigRepository)
        fakeWorkoutParseRepo = FakeWorkoutParseRepository(aiChatRepository, exerciseRepository)
        fakeScanner = FakeMarkdownFileScanner()

        viewModel = DataImportViewModel(
            context = context,
            workoutRepository = workoutRepository,
            markdownFileScanner = fakeScanner,
            workoutParseRepository = fakeWorkoutParseRepo,
            providerConfigRepository = providerConfigRepository,
            exerciseRepository = exerciseRepository,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * 辅助方法：准备包含预设训练记录草稿的导入批次。
     */
    private suspend fun setupBatchWithWorkout(
        sourceKey: String = "2026-05-07.md",
        date: LocalDate = LocalDate.of(2026, 5, 7),
        workout: Workout,
    ) {
        fakeScanner.scanResult = MarkdownFileScanner.ScanResult(
            successes = listOf(
                MarkdownFileScanner.ScannedMarkdown(
                    fileName = sourceKey,
                    date = date,
                    content = "# 训练日志\n$sourceKey",
                    sourceKey = sourceKey,
                ),
            ),
            failures = emptyList(),
        )
        fakeWorkoutParseRepo.parseHandler = { _, _ -> Result.success(workout) }

        viewModel.onFolderSelected(Uri.parse("content://dummy/tree"))
        viewModel.uiState.first { !it.isScanning && it.items.isNotEmpty() }

        viewModel.onParse()
        viewModel.uiState.first { !it.isParsing && it.items.all { item -> item.status == ImportItemStatus.PARSED } }
    }

    /**
     * 测试改重量：仅更新编辑缓冲，保存前原草稿完全不变，保存后生效。
     */
    @Test
    fun testEdit_changeWeight_updatesBufferOnlyUntilSaved() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "初始感受",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        // 打开编辑弹层
        viewModel.onStartEdit("2026-05-07.md")

        val stateOpen = viewModel.uiState.value
        assertEquals("2026-05-07.md", stateOpen.editingSourceKey)
        assertNotNull(stateOpen.editingDraft)
        val initialExercise = stateOpen.editingDraft!!.exercises.first()
        val initialSet = initialExercise.sets.first()
        assertEquals(60f, initialSet.weightKg)

        // 修改重量为 75kg
        viewModel.onDraftSetChange(initialExercise.localId, initialSet.localId, 75f, 10)

        // 断言：缓冲内已变为 75kg
        val stateEditing = viewModel.uiState.value
        assertEquals(75f, stateEditing.editingDraft!!.exercises.first().sets.first().weightKg)

        // 断言：原 items 中的草稿依然保持 60kg，完全未被污染
        val originalDraftBeforeSave = stateEditing.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals(60f, originalDraftBeforeSave.exercises.first().sets.first().weightKg)

        // 保存编辑
        viewModel.onSaveEdit()
        val stateSaved = viewModel.uiState.first { it.editingSourceKey == null }

        // 断言：保存后原条目更新为 75kg，弹层关闭且缓冲清空
        assertNull(stateSaved.editingSourceKey)
        assertNull(stateSaved.editingDraft)
        val originalDraftAfterSave = stateSaved.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals(75f, originalDraftAfterSave.exercises.first().sets.first().weightKg)
    }

    /**
     * 测试删动作：仅更新编辑缓冲，保存前原草稿完全不变，保存后生效。
     */
    @Test
    fun testEdit_removeExercise_updatesBufferOnlyUntilSaved() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = null,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
                ExerciseLog(
                    name = "引体向上",
                    exerciseKey = "pull-up",
                    sets = listOf(SetLog(weightKg = 0f, reps = 8, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val stateOpen = viewModel.uiState.value
        val openDraft = stateOpen.editingDraft!!
        assertEquals(2, openDraft.exercises.size)

        val secondExerciseId = openDraft.exercises[1].localId
        // 删除第二个动作（引体向上）
        viewModel.onRemoveDraftExercise(secondExerciseId)

        // 断言：编辑缓冲内仅余 1 个动作
        val stateEditing = viewModel.uiState.value
        val editingDraft = stateEditing.editingDraft!!
        assertEquals(1, editingDraft.exercises.size)
        assertEquals("杠铃卧推", editingDraft.exercises.first().name)

        // 断言：原 items 条目草稿依然包含 2 个动作
        val originalItem = stateEditing.items.first { it.sourceKey == "2026-05-07.md" }
        assertEquals(2, originalItem.draft!!.exercises.size)

        // 保存编辑
        viewModel.onSaveEdit()
        val stateSaved = viewModel.uiState.first { it.editingSourceKey == null }

        // 断言：保存后原条目仅余 1 个动作
        val savedDraft = stateSaved.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals(1, savedDraft.exercises.size)
        assertEquals("杠铃卧推", savedDraft.exercises.first().name)
    }

    /**
     * 测试取消重开：修改感受和数值后取消，原草稿完全不变，重新打开仍为初始值。
     */
    @Test
    fun testEdit_cancelAndReopen_discardsAllModifications() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "原始感受",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val exercise = viewModel.uiState.value.editingDraft!!.exercises.first()
        val set = exercise.sets.first()

        // 编辑感受与重量
        viewModel.onDraftFeelingsChange("改动了感受")
        viewModel.onDraftSetChange(exercise.localId, set.localId, 100f, 5)

        // 取消编辑（下滑关闭 / 点击取消按钮 / 返回键）
        viewModel.onDismissEdit()

        // 断言：弹层已关闭，缓冲已清空
        val stateDismissed = viewModel.uiState.value
        assertNull(stateDismissed.editingSourceKey)
        assertNull(stateDismissed.editingDraft)

        // 断言：原条目草稿完全未变
        val originalItem = stateDismissed.items.first { it.sourceKey == "2026-05-07.md" }
        val originalDraft = originalItem.draft!!
        assertEquals("原始感受", originalDraft.feelings)
        assertEquals(60f, originalDraft.exercises.first().sets.first().weightKg)

        // 重新打开编辑
        viewModel.onStartEdit("2026-05-07.md")
        val stateReopened = viewModel.uiState.value
        assertEquals("2026-05-07.md", stateReopened.editingSourceKey)
        assertNotNull(stateReopened.editingDraft)
        val reopenedDraft = stateReopened.editingDraft!!
        assertEquals("原始感受", reopenedDraft.feelings)
        assertEquals(60f, reopenedDraft.exercises.first().sets.first().weightKg)
    }

    /**
     * 测试保存成功：重跑动作库匹配后写回原条目并关闭弹层。
     */
    @Test
    fun testEdit_saveSuccess_rematchesAndCommitsChanges() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val exerciseId = viewModel.uiState.value.editingDraft!!.exercises.first().localId

        // 修改动作为动作库已存在的"哑铃卧推"
        viewModel.onDraftExerciseNameChange(exerciseId, "哑铃卧推")

        viewModel.onSaveEdit()
        val stateSaved = viewModel.uiState.first { it.editingSourceKey == null }

        assertNull(stateSaved.editingSourceKey)
        assertNull(stateSaved.editingDraft)

        val updatedDraft = stateSaved.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals("哑铃卧推", updatedDraft.exercises.first().name)
        assertEquals("dumbbell-bench-press", updatedDraft.exercises.first().exerciseKey)
    }

    /**
     * 测试保存失败：保留编辑缓冲供重试，弹层保持打开，原条目不被污染。
     */
    @Test
    fun testEdit_saveFailure_retainsBufferForRetryAndLeavesOriginalItemUntouched() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        // 模拟 resolveExerciseKey 抛出数据库异常
        fakeWorkoutParseRepo.resolveHandler = { throw IllegalStateException("SQLite 查询异常") }

        viewModel.onStartEdit("2026-05-07.md")
        val exercise = viewModel.uiState.value.editingDraft!!.exercises.first()
        val set = exercise.sets.first()

        viewModel.onDraftSetChange(exercise.localId, set.localId, 95f, 3)

        // 触发保存（会失败）
        viewModel.onSaveEdit()
        val stateFailed = viewModel.uiState.first { it.message != null }

        // 断言：错误提示已落位
        assertNotNull(stateFailed.message)
        assertTrue(stateFailed.message!!.contains("保存编辑失败"))

        // 断言：弹层依然打开，且缓冲依然保留用户编辑的 95kg
        assertEquals("2026-05-07.md", stateFailed.editingSourceKey)
        assertNotNull(stateFailed.editingDraft)
        assertEquals(95f, stateFailed.editingDraft!!.exercises.first().sets.first().weightKg)

        // 断言：原 items 条目完全未受污染，依然是 60kg
        val originalDraft = stateFailed.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals(60f, originalDraft.exercises.first().sets.first().weightKg)

        // 恢复正常并重试保存
        fakeWorkoutParseRepo.resolveHandler = null
        viewModel.onSaveEdit()
        val stateRecovered = viewModel.uiState.first { it.editingSourceKey == null }

        assertNull(stateRecovered.editingSourceKey)
        assertNull(stateRecovered.editingDraft)
        val recoveredDraft = stateRecovered.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals(95f, recoveredDraft.exercises.first().sets.first().weightKg)
    }

    /**
     * 测试异步保存结果不能写入用户后来打开的另一条记录。
     */
    @Test
    fun testEdit_asyncSaveResult_doesNotWriteIntoAnotherRecordOpenedLater() = runTest(main.scheduler) {
        val workout1 = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "记录1",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "item1.md",
        )
        val workout2 = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 6),
            feelings = "记录2",
            exercises = listOf(
                ExerciseLog(
                    name = "深蹲",
                    exerciseKey = "barbell-squat",
                    sets = listOf(SetLog(weightKg = 100f, reps = 5, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "item2.md",
        )

        fakeScanner.scanResult = MarkdownFileScanner.ScanResult(
            successes = listOf(
                MarkdownFileScanner.ScannedMarkdown("item1.md", LocalDate.of(2026, 5, 7), "# 日志1", "item1.md"),
                MarkdownFileScanner.ScannedMarkdown("item2.md", LocalDate.of(2026, 5, 6), "# 日志2", "item2.md"),
            ),
            failures = emptyList(),
        )
        fakeWorkoutParseRepo.parseHandler = { content, _ ->
            if (content.contains("日志1")) Result.success(workout1) else Result.success(workout2)
        }

        viewModel.onFolderSelected(Uri.parse("content://dummy/tree"))
        viewModel.uiState.first { !it.isScanning && it.items.size == 2 }
        viewModel.onParse()
        viewModel.uiState.first { !it.isParsing && it.items.all { item -> item.status == ImportItemStatus.PARSED } }

        // 1. 打开 item1，修改重量为 80kg
        viewModel.onStartEdit("item1.md")
        val item1Ex = viewModel.uiState.value.editingDraft!!.exercises.first()
        val item1Set = item1Ex.sets.first()
        viewModel.onDraftSetChange(item1Ex.localId, item1Set.localId, 80f, 10)

        // 2. 挂起 resolveExerciseKey，模拟慢速异步保存
        val deferred = CompletableDeferred<String?>()
        fakeWorkoutParseRepo.resolveHandler = { name ->
            if (name == "杠铃卧推") deferred.await() else "barbell-squat"
        }

        // 3. 触发 item1 保存
        viewModel.onSaveEdit()

        // 4. 在 item1 异步保存仍在进行时，用户打开 item2
        viewModel.onStartEdit("item2.md")

        val stateWithItem2 = viewModel.uiState.value
        assertEquals("item2.md", stateWithItem2.editingSourceKey)
        val item2Draft = stateWithItem2.editingDraft!!
        assertEquals("深蹲", item2Draft.exercises.first().name)
        assertEquals(100f, item2Draft.exercises.first().sets.first().weightKg)

        // 5. item1 异步保存完成
        deferred.complete("barbell-bench-press")
        advanceUntilIdle()

        val stateFinal = viewModel.uiState.value

        // 断言：打开新条目使 token 递增并取消旧任务，item1 的旧保存未写入，item1 保持初始 60kg
        val item1Final = stateFinal.items.first { it.sourceKey == "item1.md" }
        assertEquals(60f, item1Final.draft!!.exercises.first().sets.first().weightKg)

        // 核心断言：item2 绝未被写入 item1 的结果！
        val item2Final = stateFinal.items.first { it.sourceKey == "item2.md" }
        val finalDraft2 = item2Final.draft!!
        assertEquals(100f, finalDraft2.exercises.first().sets.first().weightKg)
        assertEquals("深蹲", finalDraft2.exercises.first().name)

        // 核心断言：当前打开的依然是 item2，且 item2 的编辑缓冲完好无损，没有被 item1 关闭或冲掉
        assertEquals("item2.md", stateFinal.editingSourceKey)
        assertNotNull(stateFinal.editingDraft)
        val currentDraft = stateFinal.editingDraft!!
        assertEquals("深蹲", currentDraft.exercises.first().name)
        assertEquals(100f, currentDraft.exercises.first().sets.first().weightKg)
    }

    /**
     * 测试正在保存时取消：在异步保存途中用户点击取消，保存协程被取消，改动不写入原条目。
     */
    @Test
    fun testEdit_dismissWhileSaving_cancelsSaveAndDoesNotCommit() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val ex = viewModel.uiState.value.editingDraft!!.exercises.first()
        val set = ex.sets.first()
        viewModel.onDraftSetChange(ex.localId, set.localId, 85f, 5)

        val deferred = CompletableDeferred<String?>()
        fakeWorkoutParseRepo.resolveHandler = { deferred.await() }

        // 触发保存
        viewModel.onSaveEdit()

        // 此时在保存途中取消
        viewModel.onDismissEdit()

        val stateDismissed = viewModel.uiState.value
        assertNull(stateDismissed.editingSourceKey)
        assertNull(stateDismissed.editingDraft)

        // 恢复 deferred
        deferred.complete("barbell-bench-press")
        advanceUntilIdle()

        // 断言：由于保存已被取消，原条目草稿依然保持初始 60kg，未被 85kg 写入
        val stateFinal = viewModel.uiState.value
        val originalDraft = stateFinal.items.first { it.sourceKey == "2026-05-07.md" }.draft!!
        assertEquals(60f, originalDraft.exercises.first().sets.first().weightKg)
    }

    /**
     * 测试保存期间草稿冻结：在异步保存途中调用各类编辑回调，草稿快照不被修改。
     */
    @Test
    fun testEdit_mutationsIgnoredWhileSaving() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "初始感受",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val ex = viewModel.uiState.value.editingDraft!!.exercises.first()
        val set = ex.sets.first()

        // 挂起保存
        val deferred = CompletableDeferred<String?>()
        fakeWorkoutParseRepo.resolveHandler = { deferred.await() }

        // 触发保存
        viewModel.onSaveEdit()
        assertTrue(viewModel.uiState.value.isSavingDraft)

        // 在保存中尝试修改感受、动作名、组数值、类型、添加组、移除动作
        viewModel.onDraftFeelingsChange("保存中新感受")
        viewModel.onDraftExerciseNameChange(ex.localId, "新动作名")
        viewModel.onDraftSetChange(ex.localId, set.localId, 999f, 99)
        viewModel.onToggleDraftSetType(ex.localId, set.localId)
        viewModel.onAddDraftSet(ex.localId)
        viewModel.onRemoveDraftSet(ex.localId, set.localId)
        viewModel.onRemoveDraftExercise(ex.localId)

        // 断言：保存期间 editingDraft 依然保持保存发起时的快照，未被任何修改污染
        val draftWhileSaving = viewModel.uiState.value.editingDraft!!
        assertEquals("初始感受", draftWhileSaving.feelings)
        assertEquals("杠铃卧推", draftWhileSaving.exercises.first().name)
        assertEquals(60f, draftWhileSaving.exercises.first().sets.first().weightKg)
        assertEquals(1, draftWhileSaving.exercises.size)
        assertEquals(1, draftWhileSaving.exercises.first().sets.size)

        // 完成保存
        deferred.complete("barbell-bench-press")
        advanceUntilIdle()

        // 断言：原条目保存的是保存发起时的快照
        val stateFinal = viewModel.uiState.value
        assertNull(stateFinal.editingSourceKey)
        val finalDraft = stateFinal.items.first().draft!!
        assertEquals("初始感受", finalDraft.feelings)
        assertEquals("杠铃卧推", finalDraft.exercises.first().name)
        assertEquals(60f, finalDraft.exercises.first().sets.first().weightKg)
    }

    /**
     * 测试保存防重复触发：在保存中重复点击保存，只发起一次匹配。
     */
    @Test
    fun testEdit_repeatedSaveCallsOnlyLaunchOneMatching() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val deferred = CompletableDeferred<String?>()
        var resolveCount = 0
        fakeWorkoutParseRepo.resolveHandler = {
            resolveCount++
            deferred.await()
        }

        // 第一次触发保存
        viewModel.onSaveEdit()
        assertTrue(viewModel.uiState.value.isSavingDraft)

        // 重复触发保存
        viewModel.onSaveEdit()
        viewModel.onSaveEdit()

        // 断言：只发起了一次匹配调用
        assertEquals(1, resolveCount)

        deferred.complete("barbell-bench-press")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingDraft)
        assertNull(viewModel.uiState.value.editingSourceKey)
        assertEquals(1, resolveCount)
    }

    /**
     * 测试取消后重新打开同一条目：旧保存任务完成后无法关闭新弹层或清空新状态。
     */
    @Test
    fun testEdit_dismissAndReopenSameItem_oldTaskCannotDismissOrClearNewSheet() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "旧感受",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        // 1. 打开编辑，修改感受为 "编辑中"，点击保存
        viewModel.onStartEdit("2026-05-07.md")
        viewModel.onDraftFeelingsChange("编辑中")
        val deferred = CompletableDeferred<String?>()
        fakeWorkoutParseRepo.resolveHandler = { deferred.await() }
        viewModel.onSaveEdit()
        assertTrue(viewModel.uiState.value.isSavingDraft)

        // 2. 在保存途中取消
        viewModel.onDismissEdit()
        assertNull(viewModel.uiState.value.editingSourceKey)
        assertFalse(viewModel.uiState.value.isSavingDraft)

        // 3. 重新打开同一条目
        viewModel.onStartEdit("2026-05-07.md")
        assertEquals("2026-05-07.md", viewModel.uiState.value.editingSourceKey)
        assertNotNull(viewModel.uiState.value.editingDraft)
        assertFalse(viewModel.uiState.value.isSavingDraft)

        // 4. 旧保存任务完成
        deferred.complete("barbell-bench-press")
        advanceUntilIdle()

        // 断言：旧保存任务无法关闭新弹层，也无法将编辑缓冲清空
        val stateAfterOldTask = viewModel.uiState.value
        assertEquals("2026-05-07.md", stateAfterOldTask.editingSourceKey)
        assertNotNull(stateAfterOldTask.editingDraft)
        // 原条目未被写入之前取消的修改，仍然是 "旧感受"
        val itemDraft = stateAfterOldTask.items.first().draft!!
        assertEquals("旧感受", itemDraft.feelings)
    }

    /**
     * 测试保存失败：保留草稿缓冲、恢复可编辑状态（isSavingDraft=false），重试保存可成功。
     */
    @Test
    fun testEdit_saveFailure_preservesDraftAndReEnablesEditingAndRetrySucceeds() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val ex = viewModel.uiState.value.editingDraft!!.exercises.first()
        val set = ex.sets.first()
        viewModel.onDraftSetChange(ex.localId, set.localId, 75f, 8)

        // 1. 模拟保存失败抛出异常
        fakeWorkoutParseRepo.resolveHandler = { throw RuntimeException("网络异常") }
        viewModel.onSaveEdit()
        advanceUntilIdle()

        // 断言：保存失败后草稿保留、弹层保持打开、isSavingDraft 恢复为 false、给出错误提示
        val stateFailed = viewModel.uiState.value
        assertEquals("2026-05-07.md", stateFailed.editingSourceKey)
        assertNotNull(stateFailed.editingDraft)
        assertFalse(stateFailed.isSavingDraft)
        assertEquals(75f, stateFailed.editingDraft!!.exercises.first().sets.first().weightKg)
        assertNotNull(stateFailed.message)
        assertTrue(stateFailed.message!!.contains("保存编辑失败：网络异常"))

        // 2. 失败后用户仍然可以继续编辑（因为已恢复可编辑状态）
        viewModel.onDraftFeelingsChange("重试成功")

        // 3. 恢复正常并重试保存
        fakeWorkoutParseRepo.resolveHandler = { "barbell-bench-press" }
        viewModel.onSaveEdit()
        advanceUntilIdle()

        // 断言：重试成功，弹层关闭，原条目草稿正确更新
        val stateSuccess = viewModel.uiState.value
        assertNull(stateSuccess.editingSourceKey)
        assertNull(stateSuccess.editingDraft)
        assertFalse(stateSuccess.isSavingDraft)
        val finalDraft = stateSuccess.items.first().draft!!
        assertEquals(75f, finalDraft.exercises.first().sets.first().weightKg)
        assertEquals("重试成功", finalDraft.feelings)
    }

    /**
     * 测试改为空动作名：清空旧 exerciseKey，保存后为 null，确认导入时不能入库并反馈无效。
     */
    @Test
    fun testEdit_emptyExerciseName_clearsExerciseKeyAndCannotBePersisted() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val exerciseId = viewModel.uiState.value.editingDraft!!.exercises.first().localId

        // 将动作名改为空串
        viewModel.onDraftExerciseNameChange(exerciseId, "")

        // 断言：编辑缓冲中旧 exerciseKey 已被立即清空
        assertNull(viewModel.uiState.value.editingDraft!!.exercises.first().exerciseKey)

        // 保存编辑
        viewModel.onSaveEdit()
        val stateSaved = viewModel.uiState.first { it.editingSourceKey == null }

        // 断言：保存后动作名为空，exerciseKey 为 null（绝不保留旧 key）
        val savedDraft = stateSaved.items.first().draft!!
        assertEquals("", savedDraft.exercises.first().name)
        assertNull(savedDraft.exercises.first().exerciseKey)
        assertEquals(0, savedDraft.validExerciseCount)

        // 确认条目默认已勾选，直接确认导入
        assertTrue(viewModel.uiState.value.items.first().checked)
        viewModel.onConfirmImport()
        val stateImported = viewModel.uiState.first { !it.isImporting && it.lastResultSummary != null }

        // 断言：未入库，记录为 invalid，且给出明确反馈（不误报成功）
        assertTrue(workoutRepository.getWorkouts().first().isEmpty())
        val summary = stateImported.lastResultSummary!!
        assertEquals(0, summary.imported)
        assertEquals(1, summary.invalid)
        assertTrue(stateImported.message!!.contains("所选条目清洗后无有效动作，未能导入"))
        assertEquals(ImportItemStatus.PARSED, stateImported.items.first().status)
    }

    /**
     * 测试改为全空白动作名：trim 后为空，清空旧 exerciseKey，确认导入时不能入库。
     */
    @Test
    fun testEdit_whitespaceExerciseName_clearsExerciseKeyAndCannotBePersisted() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        viewModel.onStartEdit("2026-05-07.md")
        val exerciseId = viewModel.uiState.value.editingDraft!!.exercises.first().localId

        // 改为纯空白字符
        viewModel.onDraftExerciseNameChange(exerciseId, "   \t  ")

        // 断言：编辑缓冲中旧 exerciseKey 已被清空
        assertNull(viewModel.uiState.value.editingDraft!!.exercises.first().exerciseKey)

        viewModel.onSaveEdit()
        val stateSaved = viewModel.uiState.first { it.editingSourceKey == null }

        val savedDraft = stateSaved.items.first().draft!!
        assertEquals("", savedDraft.exercises.first().name)
        assertNull(savedDraft.exercises.first().exerciseKey)
        assertEquals(0, savedDraft.validExerciseCount)

        assertTrue(viewModel.uiState.value.items.first().checked)
        viewModel.onConfirmImport()
        val stateImported = viewModel.uiState.first { !it.isImporting && it.lastResultSummary != null }

        assertTrue(workoutRepository.getWorkouts().first().isEmpty())
        val summary = stateImported.lastResultSummary!!
        assertEquals(0, summary.imported)
        assertEquals(1, summary.invalid)
    }

    /**
     * 测试改名重匹配：改名匹配动作库新 key，改为未知动作重置 key 为 null（不保留旧 key）。
     */
    @Test
    fun testEdit_renameExercise_rematchesAndClearsOldKey() = runTest(main.scheduler) {
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        // 1. 改为库内存在的「引体向上」
        viewModel.onStartEdit("2026-05-07.md")
        var exerciseId = viewModel.uiState.value.editingDraft!!.exercises.first().localId
        viewModel.onDraftExerciseNameChange(exerciseId, "引体向上")
        viewModel.onSaveEdit()
        val stateRematched = viewModel.uiState.first { it.editingSourceKey == null }

        val rematchedDraft = stateRematched.items.first().draft!!
        assertEquals("引体向上", rematchedDraft.exercises.first().name)
        assertEquals("pull-up", rematchedDraft.exercises.first().exerciseKey)

        // 2. 改为库内不存在的「自定义自由动作」
        viewModel.onStartEdit("2026-05-07.md")
        exerciseId = viewModel.uiState.value.editingDraft!!.exercises.first().localId
        viewModel.onDraftExerciseNameChange(exerciseId, "自定义自由动作")
        viewModel.onSaveEdit()
        val stateCustom = viewModel.uiState.first { it.editingSourceKey == null }

        val customDraft = stateCustom.items.first().draft!!
        assertEquals("自定义自由动作", customDraft.exercises.first().name)
        // 关键断言：旧 key "pull-up" 绝未被保留，变为 null 自由文本
        assertNull(customDraft.exercises.first().exerciseKey)
        assertEquals(1, customDraft.validExerciseCount)
    }

    /**
     * 测试所有动作被清洗场景：确认导入不落库、记录为 invalid，且给出明确反馈不误报导入成功。
     */
    @Test
    fun testImport_allExercisesCleaned_givesClearFeedbackAndDoesNotFalselyReportSuccess() = runTest(main.scheduler) {
        // 包含 2 个清洗后都会被剔除的动作：1 个全空白动作名、1 个仅有占位组（reps=0）
        val initialWorkout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 7),
            feelings = "",
            exercises = listOf(
                ExerciseLog(
                    name = "   ",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 60f, reps = 10, setType = SetType.WORKING)),
                ),
                ExerciseLog(
                    name = "深蹲",
                    exerciseKey = "barbell-squat",
                    sets = listOf(SetLog(weightKg = 100f, reps = 0, setType = SetType.WORKING)),
                ),
            ),
            sourceFileName = "2026-05-07.md",
        )
        setupBatchWithWorkout(workout = initialWorkout)

        val draft = viewModel.uiState.value.items.first().draft!!
        assertEquals(0, draft.validExerciseCount)

        // 确认条目默认已勾选，直接确认导入
        assertTrue(viewModel.uiState.value.items.first().checked)
        viewModel.onConfirmImport()
        val stateImported = viewModel.uiState.first { !it.isImporting && it.lastResultSummary != null }

        // 断言：数据库完全未写入任何 Workout
        assertTrue(workoutRepository.getWorkouts().first().isEmpty())

        // 断言：结果统计中 imported=0，invalid=1
        val summary = stateImported.lastResultSummary!!
        assertEquals(0, summary.imported)
        assertEquals(0, summary.upgraded)
        assertEquals(0, summary.archived)
        assertEquals(1, summary.invalid)

        // 断言：给出明确反馈文案，绝不误报「导入完成：新增 1 条」
        assertNotNull(stateImported.message)
        assertTrue(stateImported.message!!.contains("所选条目清洗后无有效动作，未能导入"))
        // 确认条目状态依然为 PARSED，保留供用户编辑
        assertEquals(ImportItemStatus.PARSED, stateImported.items.first().status)
    }
}

