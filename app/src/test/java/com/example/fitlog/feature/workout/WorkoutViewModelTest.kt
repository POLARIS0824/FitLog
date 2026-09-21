package com.example.fitlog.feature.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.Muscle
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.testing.createTestPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/**
 * [WorkoutViewModel] 的单元测试。
 * 使用 Robolectric 和内存 Room 数据库验证 ViewModel 状态流转（Loading -> Success）与
 * 训练执行流（会话启动/计划预填/组录入/结束清洗/放弃）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkoutViewModelTest {

    /**
     * 每个测试方法使用独立的临时目录存放 DataStore 文件。
     */
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var planRepository: WorkoutPlanRepository
    private lateinit var viewModel: WorkoutViewModel

    /**
     * 测试调度器：与 DataStore scope、Main dispatcher 及 `runTest` 共享同一实例。
     */
    private val testScheduler = TestCoroutineScheduler()

    /**
     * DataStore 内部协程的作用域，测试结束时在 [tearDown] 中取消。
     */
    private lateinit var dataStoreScope: TestScope

    /**
     * 设置单元测试的协程主调度器，并初始化数据库 and ViewModel 实例。
     */
    @Before
    fun setUp() = runTest(testScheduler) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseLogDao = db.exerciseLogDao(),
            setLogDao = db.setLogDao(),
            workoutPlanDao = db.workoutPlanDao(),
            db = db,
        )
        dataStoreScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = createTestPreferencesDataStore(
            tmpFolder.newFile("workout_vm_prefs.preferences_pb"),
            dataStoreScope,
        )
        planRepository = WorkoutPlanRepository(db.workoutPlanDao(), dataStore)

        viewModel = WorkoutViewModel(
            workoutRepository = repository,
            workoutPlanRepository = planRepository,
            exerciseRepository = ExerciseRepository(db.exerciseDao()),
            savedStateHandle = SavedStateHandle(),
        )
    }

    /**
     * 取消 DataStore 作用域，重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
        db.close()
    }

    /**
     * 测试刚初始化时的初始状态。
     */
    @Test
    fun testInitialState_isSuccessWithEmptyList() = runTest(testScheduler) {
        // 使用 first { ... } 等待 Flow 监听到数据库的首次空列表 Success 状态
        val state = viewModel.uiState.first { it is WorkoutUiState.Success }
        assertTrue(state is WorkoutUiState.Success)
        assertEquals(0, (state as WorkoutUiState.Success).workouts.size)
    }

    /**
     * 测试删除日志时，UI 状态能够即时同步更新。
     */
    @Test
    fun testDeleteWorkout_updatesUiStateToEmpty() = runTest(testScheduler) {
        val workout = Workout(
            id = 100L,
            userId = 0L,
            date = LocalDate.of(2026, 5, 20),
            exercises = emptyList(),
            feelings = "好极了",
            sourceFileName = "2026-05-20.md"
        )

        // 先经仓库直接插入作夹具（页面只暴露删除，ViewModel 无 insert 入口）
        repository.insert(workout)
        val stateAfterInsert = viewModel.uiState.first {
            it is WorkoutUiState.Success && it.workouts.size == 1
        } as WorkoutUiState.Success
        assertEquals(1, stateAfterInsert.workouts.size)

        // 后删除并等待数据重新变空
        viewModel.deleteWorkout(workout)
        val stateAfterDelete = viewModel.uiState.first {
            it is WorkoutUiState.Success && it.workouts.isEmpty()
        } as WorkoutUiState.Success
        assertEquals(0, stateAfterDelete.workouts.size)
    }

    /**
     * 测试自由训练会话：启动 → 加动作（附占位组）→ 录入一组 → 结束落库。
     * 结束后无进行中会话，历史列表出现一条已结束的结构化记录。
     */
    @Test
    fun testSession_freeWorkout_startLogSetAndFinish() = runTest(testScheduler) {
        insertLibraryExercise()
        viewModel.startSession()

        val session = viewModel.activeSession.first { it != null }!!
        assertNull("无计划时为自由训练", session.planSessionId)

        viewModel.addExercise(testExercise())
        // 等待动作 + 其占位组都落库（中间态会先出现无组的动作行）
        val withExercise = viewModel.activeSession.first {
            it != null && it.exercises.isNotEmpty() && it.exercises.first().sets.isNotEmpty()
        }!!
        assertEquals(1, withExercise.exercises.size)

        // 占位组（0 次）先就位，再录入 60kg×8
        val exercise = withExercise.exercises.first()
        assertEquals(1, exercise.sets.size)
        viewModel.updateSet(exercise.sets.first().id, 60f, 8)

        viewModel.finishSession("状态很好")

        assertNull(viewModel.activeSession.first { it == null })
        val finished = repository.getWorkouts().first().single()
        assertNotNull("结束后应写入 endedAt", finished.endedAt)
        assertEquals("状态很好", finished.feelings)
        assertEquals(1, finished.exercises.size)
        assertEquals(1, finished.exercises.first().sets.size)
        assertEquals(60f, finished.exercises.first().sets.first().weightKg)
        assertEquals(8, finished.exercises.first().sets.first().reps)
    }

    /**
     * 测试结束清洗：仅占位组（reps=0）时不能结束——会话保持、提示落位。
     */
    @Test
    fun testSession_finishBlockedWhenOnlyPlaceholderSets() = runTest(testScheduler) {
        insertLibraryExercise()
        viewModel.startSession()
        viewModel.activeSession.first { it != null }
        viewModel.addExercise(testExercise())
        viewModel.activeSession.first {
            it != null && it.exercises.isNotEmpty() && it.exercises.first().sets.isNotEmpty()
        }

        viewModel.finishSession(null)

        // 等待结束协程落地（Room 事务在真实后台线程执行，message 异步到位）
        assertEquals(
            "无有效内容时应给出提示",
            "还没有可保存的训练内容，请至少完成一组",
            viewModel.message.first { it != null },
        )
        assertNotNull("会话应保持进行中", viewModel.activeSession.first { it != null })
    }

    /**
     * 测试放弃会话：训练行删除（级联清空子行），历史不留痕。
     */
    @Test
    fun testSession_discardRemovesWorkoutRow() = runTest(testScheduler) {
        insertLibraryExercise()
        viewModel.startSession()
        viewModel.activeSession.first { it != null }
        viewModel.addExercise(testExercise())
        viewModel.activeSession.first {
            it != null && it.exercises.isNotEmpty() && it.exercises.first().sets.isNotEmpty()
        }

        viewModel.discardSession()

        assertNull(viewModel.activeSession.first { it == null })
        assertTrue(repository.getWorkouts().first().isEmpty())
    }

    /**
     * 测试计划课次预填：激活计划的下一个未完成课次的动作清单
     * 在会话启动时自动建行，结束训练后课次被标记完成。
     */
    @Test
    fun testSession_planPrefillAndCompletionMarking() = runTest(testScheduler) {
        insertLibraryExercise()
        val planId = "plan-test-1"
        planRepository.save(
            WorkoutPlan(
                id = planId,
                name = "Test Plan",
                description = null,
                goal = null,
                durationWeeks = 1,
                sessionsPerWeek = 1,
                isCustom = false,
                createdAt = LocalDate.now(),
                sessions = listOf(
                    PlannedSession(
                        id = "session-1",
                        name = "Day 1 - Push",
                        description = null,
                        dayNumber = 1,
                        weekNumber = 1,
                        targetDurationMinutes = null,
                        exercises = listOf(
                            PlannedExerciseItem(
                                exerciseKey = "barbell-bench-press",
                                exerciseName = "Barbell bench press",
                                targetSets = 4,
                                targetRepsMin = 8,
                                targetRepsMax = 10,
                                order = 0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        planRepository.setActivePlanId(planId)

        viewModel.startSession()

        // 等待课次动作清单（含占位组）预填完成（workout 行先落库，预填行随后到达，
        // 会话投影分多次发射——直接断言会在窗口期读到空清单）
        val session = viewModel.activeSession.first {
            it != null && it.exercises.isNotEmpty() && it.exercises.first().sets.isNotEmpty()
        }!!
        assertEquals("session-1", session.planSessionId)
        assertEquals(1, session.exercises.size)
        // toTargetText 只产出处方本体，"目标" 前缀由 UI 层拼接
        assertEquals("4组 × 8-10 次", session.exercises.first().targetText)

        viewModel.updateSet(session.exercises.first().sets.first().id, 60f, 8)
        viewModel.finishSession(null)

        // 结束协程（含课次完成标记回写）异步落地：订阅计划流等待 DB 事实源更新，
        // 而非直接读库（存在写入在途的时序窗口）
        val marked = planRepository.getPlanByIdFlow(planId)
            .first { plan -> plan?.sessions?.firstOrNull()?.completedWorkoutId != null }
            ?.sessions?.first()!!
        assertEquals("session-1", marked.id)
        val workoutId = repository.getWorkouts().first().single().id
        assertEquals(workoutId, marked.completedWorkoutId)
    }

    /**
     * 测试同一计划课次中包含同动作多次时，依据 [PlannedExerciseItem.resolvedId] 精确匹配，
     * 且结构化目标字段（targetSets, targetRepsMin, targetRepsMax）正确透传。
     */
    @Test
    fun testBuildActiveSession_duplicateExercises_matchedByStableId() = runTest(testScheduler) {
        insertLibraryExercise()
        val planId = "plan-dup-1"
        planRepository.save(
            WorkoutPlan(
                id = planId,
                name = "双重卧推测试",
                description = null,
                goal = null,
                durationWeeks = 1,
                sessionsPerWeek = 1,
                isCustom = false,
                createdAt = LocalDate.now(),
                sessions = listOf(
                    PlannedSession(
                        id = "session-dup",
                        name = "Day 1",
                        description = null,
                        dayNumber = 1,
                        weekNumber = 1,
                        targetDurationMinutes = null,
                        exercises = listOf(
                            PlannedExerciseItem(
                                id = "bench-heavy",
                                exerciseKey = "barbell-bench-press",
                                exerciseName = "重重量卧推",
                                targetSets = 4,
                                targetRepsMin = 3,
                                targetRepsMax = 5,
                                order = 0,
                            ),
                            PlannedExerciseItem(
                                id = "bench-volume",
                                exerciseKey = "barbell-bench-press",
                                exerciseName = "容量卧推",
                                targetSets = 3,
                                targetRepsMin = 10,
                                targetRepsMax = 12,
                                order = 1,
                            ),
                        ),
                    ),
                ),
            ),
        )
        planRepository.setActivePlanId(planId)

        viewModel.startSession()

        val session = viewModel.activeSession.first {
            it != null && it.exercises.size == 2 && it.exercises.all { ex -> ex.sets.isNotEmpty() }
        }!!
        assertEquals(2, session.exercises.size)

        // 第一个动作匹配 bench-heavy
        val firstEx = session.exercises[0]
        assertEquals("4组 × 3-5 次", firstEx.targetText)
        assertEquals(4, firstEx.targetSets)
        assertEquals(3, firstEx.targetRepsMin)
        assertEquals(5, firstEx.targetRepsMax)

        // 第二个动作匹配 bench-volume
        val secondEx = session.exercises[1]
        assertEquals("3组 × 10-12 次", secondEx.targetText)
        assertEquals(3, secondEx.targetSets)
        assertEquals(10, secondEx.targetRepsMin)
        assertEquals(12, secondEx.targetRepsMax)
    }

    /**
     * 测试历史旧数据（plannedExerciseId == null）包含同动作多次时，
     * 按动作出现序号（occurrence index）安全回退匹配。
     */
    @Test
    fun testBuildActiveSession_duplicateExercises_legacyFallbackByOccurrence() = runTest(testScheduler) {
        insertLibraryExercise()
        val planId = "plan-legacy-dup"
        planRepository.save(
            WorkoutPlan(
                id = planId,
                name = "旧数据回退测试",
                description = null,
                goal = null,
                durationWeeks = 1,
                sessionsPerWeek = 1,
                isCustom = false,
                createdAt = LocalDate.now(),
                sessions = listOf(
                    PlannedSession(
                        id = "session-legacy",
                        name = "Day 1",
                        description = null,
                        dayNumber = 1,
                        weekNumber = 1,
                        targetDurationMinutes = null,
                        exercises = listOf(
                            PlannedExerciseItem(
                                id = null, // 无显式 id
                                exerciseKey = "barbell-bench-press",
                                exerciseName = "卧推 1",
                                targetSets = 4,
                                targetRepsMin = 6,
                                targetRepsMax = 8,
                                order = 0,
                            ),
                            PlannedExerciseItem(
                                id = null,
                                exerciseKey = "barbell-bench-press",
                                exerciseName = "卧推 2",
                                targetSets = 3,
                                targetRepsMin = 12,
                                targetRepsMax = 15,
                                order = 1,
                            ),
                        ),
                    ),
                ),
            ),
        )

        // 手动构造没有 plannedExerciseId 的进行中会话数据
        val workoutId = db.workoutDao().insert(
            com.example.fitlog.data.local.entity.workout.WorkoutEntity(
                date = LocalDate.now(),
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                planSessionId = "session-legacy",
                sourceFileName = null,
                rawContent = null,
            ),
        )
        val log1 = db.exerciseLogDao().insert(
            com.example.fitlog.data.local.entity.workout.ExerciseLogEntity(
                workoutId = workoutId,
                exerciseKey = "barbell-bench-press",
                name = "杠铃卧推 1",
                sortOrder = 0,
                plannedExerciseId = null, // 模拟旧数据
            ),
        )
        db.setLogDao().insert(
            com.example.fitlog.data.local.entity.workout.SetLogEntity(
                exerciseLogId = log1,
                setNumber = 1,
                weightKg = 80f,
                reps = 8,
            ),
        )
        val log2 = db.exerciseLogDao().insert(
            com.example.fitlog.data.local.entity.workout.ExerciseLogEntity(
                workoutId = workoutId,
                exerciseKey = "barbell-bench-press",
                name = "杠铃卧推 2",
                sortOrder = 1,
                plannedExerciseId = null, // 模拟旧数据
            ),
        )
        db.setLogDao().insert(
            com.example.fitlog.data.local.entity.workout.SetLogEntity(
                exerciseLogId = log2,
                setNumber = 1,
                weightKg = 60f,
                reps = 12,
            ),
        )

        val session = viewModel.activeSession.first {
            it != null && it.exercises.size == 2 && it.exercises.all { ex -> ex.sets.isNotEmpty() }
        }!!

        assertEquals(4, session.exercises[0].targetSets)
        assertEquals(6, session.exercises[0].targetRepsMin)
        assertEquals(8, session.exercises[0].targetRepsMax)

        assertEquals(3, session.exercises[1].targetSets)
        assertEquals(12, session.exercises[1].targetRepsMin)
        assertEquals(15, session.exercises[1].targetRepsMax)
    }

    /**
     * 测试在已有计划动作项的会话中，手动追加同动作时，
     * 手动追加动作（plannedExerciseId == null）不会错误继承该计划动作项的身份与处方目标。
     */
    @Test
    fun testBuildActiveSession_manuallyAddedExercise_doesNotInheritPlannedIdentity() = runTest(testScheduler) {
        insertLibraryExercise()
        val planId = "plan-manual-add"
        planRepository.save(
            WorkoutPlan(
                id = planId,
                name = "手动加动作测试",
                description = null,
                goal = null,
                durationWeeks = 1,
                sessionsPerWeek = 1,
                isCustom = false,
                createdAt = LocalDate.now(),
                sessions = listOf(
                    PlannedSession(
                        id = "session-plan",
                        name = "Day 1",
                        description = null,
                        dayNumber = 1,
                        weekNumber = 1,
                        targetDurationMinutes = null,
                        exercises = listOf(
                            PlannedExerciseItem(
                                id = "bench-planned",
                                exerciseKey = "barbell-bench-press",
                                exerciseName = "计划卧推",
                                targetSets = 4,
                                targetRepsMin = 8,
                                targetRepsMax = 10,
                                order = 0,
                            ),
                        ),
                    ),
                ),
            ),
        )
        planRepository.setActivePlanId(planId)

        viewModel.startSession()

        val initialSession = viewModel.activeSession.first {
            it != null && it.exercises.size == 1 && it.exercises.all { ex -> ex.sets.isNotEmpty() }
        }!!
        assertEquals(1, initialSession.exercises.size)
        assertEquals(4, initialSession.exercises[0].targetSets)

        // 手动向进行中训练追加一个同库动作（无 plannedExerciseId）
        repository.addExerciseWithPlaceholderSet(
            workoutId = initialSession.workoutId,
            exerciseKey = "barbell-bench-press",
            name = "手动追加卧推",
            sortOrder = 1,
        )

        val updatedSession = viewModel.activeSession.first {
            it != null && it.exercises.size == 2 && it.exercises.all { ex -> ex.sets.isNotEmpty() }
        }!!

        // 第一个动作依然正确关联计划处方
        val plannedEx = updatedSession.exercises[0]
        assertEquals("4组 × 8-10 次", plannedEx.targetText)
        assertEquals(4, plannedEx.targetSets)
        assertEquals(8, plannedEx.targetRepsMin)
        assertEquals(10, plannedEx.targetRepsMax)

        // 手动添加的动作不得继承计划处方
        val manualEx = updatedSession.exercises[1]
        assertNull(manualEx.targetText)
        assertNull(manualEx.targetSets)
        assertNull(manualEx.targetRepsMin)
        assertNull(manualEx.targetRepsMax)
    }

    /**
     * 向动作库插入测试动作（最小实体，满足 exercise_logs 外键）。
     */
    private suspend fun insertLibraryExercise() {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
    }

    /** 选择器场景的域模型动作。 */
    private fun testExercise(): Exercise = Exercise(
        id = "barbell-bench-press",
        name = "Barbell bench press",
        primaryMuscles = listOf(Muscle.CHEST),
        bodyPart = BodyPart.CHEST,
    )
}
