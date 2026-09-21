package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 训练日志仓库 [WorkoutRepository] 的单元测试。
 *
 * 使用 Robolectric 在 JVM 环境下验证 3 层级联（训练日 -> 动作记录 -> 组记录）
 * 的事务级联写入、更新与查询聚合。
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository

    /**
     * 初始化内存 Room 数据库和 WorkoutRepository 实例。
     */
    @Before
    fun createDb() {
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
    }

    /**
     * 测试后关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试 [WorkoutRepository.getWorkouts]。
     * 验证插入的 3 层级联结构（训练日 -> 动作记录 -> 组记录）被正确查询、组装并返回。
     */
    @Test
    fun testGetWorkouts_whenDataInserted_shouldReturnCompleteNestedHierarchy() = runTest {
        val workoutId = db.workoutDao().insert(
            WorkoutEntity(date = LocalDate.of(2026, 5, 20), sourceFileName = "2026-05-20.md", rawContent = null)
        )

        val exerciseLogId = db.exerciseLogDao().insert(
            ExerciseLogEntity(workoutId = workoutId, name = "杠铃卧推", sortOrder = 1)
        )

        db.setLogDao().insertAll(
            listOf(
                SetLogEntity(exerciseLogId = exerciseLogId, setNumber = 1, weightKg = 80f, reps = 10),
                SetLogEntity(exerciseLogId = exerciseLogId, setNumber = 2, weightKg = 85f, reps = 8)
            )
        )

        val result = repository.getWorkouts().first()

        assertEquals(1, result.size)

        val workout = result[0]
        assertEquals(LocalDate.of(2026, 5, 20), workout.date)
        assertEquals(1, workout.exercises.size)

        val exercise = workout.exercises[0]
        assertEquals("杠铃卧推", exercise.name)
        assertEquals(2, exercise.sets.size)

        assertEquals(80f, exercise.sets[0].weightKg)
        assertEquals(10, exercise.sets[0].reps)

        assertEquals(85f, exercise.sets[1].weightKg)
        assertEquals(8, exercise.sets[1].reps)
    }

    /**
     * 测试级联插入：含动作与组的完整训练日志一次落库，
     * 父行自增主键正确传递为子行外键。
     */
    @Test
    fun testInsert_cascadesExercisesAndSets() = runTest {
        // exerciseKey 有指向 exercises 的外键，先插入被引用的动作
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )

        val workout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 20),
            feelings = "状态不错",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(
                        SetLog(weightKg = 80f, reps = 10),
                        SetLog(weightKg = 85f, reps = 8),
                    ),
                ),
                ExerciseLog(
                    name = "哑铃飞鸟",
                    exerciseKey = null,
                    sets = listOf(SetLog(weightKg = 20f, reps = 12)),
                ),
            ),
        )

        val workoutId = repository.insert(workout)
        assertTrue(workoutId > 0)

        // 动作层：2 条，sortOrder 按列表顺序 0、1
        val logs = db.exerciseLogDao().getByWorkoutId(workoutId)
        assertEquals(2, logs.size)
        assertEquals("杠铃卧推", logs[0].name)
        assertEquals(0, logs[0].sortOrder)
        assertEquals("哑铃飞鸟", logs[1].name)
        assertEquals(1, logs[1].sortOrder)

        // 组层：外键正确关联，setNumber 从 1 开始递增
        val benchSets = db.setLogDao().getByExerciseLogId(logs[0].id)
        assertEquals(2, benchSets.size)
        assertEquals(1, benchSets[0].setNumber)
        assertEquals(80f, benchSets[0].weightKg)
        assertEquals(2, benchSets[1].setNumber)
        assertEquals(85f, benchSets[1].weightKg)

        val flySets = db.setLogDao().getByExerciseLogId(logs[1].id)
        assertEquals(1, flySets.size)
        assertEquals(20f, flySets[0].weightKg)
    }

    /**
     * 测试级联更新：旧子行被删除并替换为新子行（set_logs 由 FK CASCADE 连带清理）。
     */
    @Test
    fun testUpdate_replacesChildrenCascade() = runTest {
        val workout = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 20),
            feelings = null,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    sets = listOf(SetLog(weightKg = 80f, reps = 10)),
                ),
            ),
        )
        val workoutId = repository.insert(workout)

        val updated = workout.copy(
            id = workoutId,
            feelings = "更新感受",
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃深蹲",
                    sets = listOf(
                        SetLog(weightKg = 100f, reps = 5),
                        SetLog(weightKg = 105f, reps = 3),
                    ),
                ),
            ),
        )
        repository.update(updated)

        // 旧动作已删除，新动作就位
        val logs = db.exerciseLogDao().getByWorkoutId(workoutId)
        assertEquals(1, logs.size)
        assertEquals("杠铃深蹲", logs[0].name)

        // 旧组记录已被 CASCADE 清理，新组记录就位
        val sets = db.setLogDao().getByExerciseLogId(logs[0].id)
        assertEquals(2, sets.size)
        assertEquals(100f, sets[0].weightKg)
        assertEquals(105f, sets[1].weightKg)

        // 父行字段已更新
        val fetched = repository.getWorkouts().first()[0]
        assertEquals("更新感受", fetched.feelings)
        assertEquals(1, fetched.exercises.size)
        assertEquals("杠铃深蹲", fetched.exercises[0].name)
    }

    /**
     * 测试空动作列表的训练日志插入（导入流程的空壳记录场景）。
     */
    @Test
    fun testInsert_emptyExercises_insertsShellOnly() = runTest {
        val shell = Workout(
            id = 0,
            userId = 0,
            date = LocalDate.of(2026, 5, 21),
            feelings = null,
            exercises = emptyList(),
            sourceFileName = "2026-05-21.md",
            rawContent = "原始文本",
        )

        val workoutId = repository.insert(shell)
        assertTrue(workoutId > 0)
        assertTrue(db.exerciseLogDao().getByWorkoutId(workoutId).isEmpty())
        assertNotNull(db.workoutDao().getBySourceFileName("2026-05-21.md"))
    }

    /**
     * 测试 [WorkoutRepository.finishSession] 的课次完成回写：planSessionId
     * 传入时，训练落库与 planned_sessions.completedWorkoutId 回写必须在
     * 同一事务语义下同时生效（此前是两步写，进程死亡落在中间会留下
     * "训练已保存、计划进度未推进"的永久缺口）。
     */
    @Test
    fun testFinishSession_withPlanSessionId_marksSessionCompleted() = runTest {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        db.workoutPlanDao().insertPlanIgnore(
            WorkoutPlanEntity(
                id = "plan-1", name = "PPL", description = null, goal = null,
                durationWeeks = 4, sessionsPerWeek = 3, isCustom = false,
                createdAt = LocalDate.of(2026, 5, 1), rawPlanText = null,
            ),
        )
        db.workoutPlanDao().insertSessions(
            listOf(
                PlannedSessionEntity(
                    id = "session-1", planId = "plan-1", name = "Day 1", description = null,
                    dayNumber = 1, weekNumber = 1, targetDurationMinutes = null,
                    exercises = emptyList(), completedWorkoutId = null,
                ),
            ),
        )

        val workoutId = repository.createSessionWorkout(
            PlannedSession(
                id = "session-1", name = "Day 1", description = null, dayNumber = 1, weekNumber = 1,
                targetDurationMinutes = null, exercises = emptyList(),
            ),
        )
        assertTrue(workoutId > 0)
        repository.addExerciseWithPlaceholderSet(workoutId, "barbell-bench-press", "杠铃卧推", 0)
        val log = db.exerciseLogDao().getByWorkoutId(workoutId).first()
        db.setLogDao().insert(
            SetLogEntity(exerciseLogId = log.id, setNumber = 2, weightKg = 80f, reps = 10),
        )

        val ended = repository.finishSession(
            workoutId = workoutId, feelings = null, endedAt = 1_000L,
        )
        assertTrue(ended)
        assertEquals(
            workoutId,
            db.workoutPlanDao().getSessionById("session-1")?.completedWorkoutId,
        )
    }

    /**
     * 测试 [WorkoutRepository.addExerciseWithPlaceholderSet]：动作与占位组单事务落库，
     * 成功时动作行自带 1 条占位组（不存在无组动作行的中间态）。
     */
    @Test
    fun testAddExerciseWithPlaceholderSet_insertsExerciseAndPlaceholder() = runTest {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val workoutId = repository.createSessionWorkout(null)
        assertTrue(workoutId > 0)

        val logId = repository.addExerciseWithPlaceholderSet(
            workoutId, "barbell-bench-press", "杠铃卧推", 0,
        )
        assertTrue(logId > 0)

        val sets = db.setLogDao().getByExerciseLogId(logId)
        assertEquals(1, sets.size)
        assertEquals(0f, sets[0].weightKg)
        assertEquals(0, sets[0].reps)
    }

    /**
     * 测试预填计划课次时，[PlannedExerciseItem.resolvedId] 作为 plannedExerciseId 落库，
     * 且在 [WorkoutRepository.finishSession] 清洗重插时完整保留。
     */
    @Test
    fun testCreateSessionWorkout_withPlanExercises_persistsPlannedExerciseId() = runTest {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = "barbell-bench-press", name = "Barbell bench press")),
        )
        val plannedExercises = listOf(
            com.example.fitlog.model.PlannedExerciseItem(
                id = "plan-ex-bench-1",
                exerciseKey = "barbell-bench-press",
                exerciseName = "杠铃卧推",
                targetSets = 4,
                order = 0,
            ),
        )
        val session = PlannedSession(
            id = "session-test-1",
            name = "Day 1",
            description = null,
            dayNumber = 1,
            weekNumber = 1,
            targetDurationMinutes = 60,
            exercises = plannedExercises,
        )

        val workoutId = repository.createSessionWorkout(session)
        assertTrue(workoutId > 0)

        val logs = db.exerciseLogDao().getByWorkoutId(workoutId)
        assertEquals(1, logs.size)
        assertEquals("plan-ex-bench-1", logs[0].plannedExerciseId)

        // 验证 finishSession 清洗重排后仍完整保留 plannedExerciseId
        db.setLogDao().insert(
            SetLogEntity(exerciseLogId = logs[0].id, setNumber = 2, weightKg = 80f, reps = 10),
        )
        val ended = repository.finishSession(workoutId = workoutId, feelings = null, endedAt = 1_000L)
        assertTrue(ended)

        val logsAfterFinish = db.exerciseLogDao().getByWorkoutId(workoutId)
        assertEquals(1, logsAfterFinish.size)
        assertEquals("plan-ex-bench-1", logsAfterFinish[0].plannedExerciseId)
    }
}
