package com.example.fitlog.data.agent.tools

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.data.local.entity.UserProfileEntity
import com.example.fitlog.data.local.entity.plan.PlannedExerciseEntity
import com.example.fitlog.data.local.entity.plan.PlannedSessionEntity
import com.example.fitlog.data.local.entity.plan.WorkoutPlanEntity
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Difficulty
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.PrimaryMuscle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 7 个只读 tool 的集成测试：Robolectric + 内存 Room，真实 DAO/Repository 灌数据。
 */
@RunWith(RobolectricTestRunner::class)
class ReadOnlyToolsTest {

    private lateinit var db: AppDatabase
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var workoutPlanRepository: WorkoutPlanRepository
    private val json = Json

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutRepository = WorkoutRepository(db.workoutDao(), db.exerciseLogDao(), db.setLogDao())
        userProfileRepository = UserProfileRepository(db.userProfileDao())
        exerciseRepository = ExerciseRepository(db.exerciseDao())
        workoutPlanRepository = WorkoutPlanRepository(db.workoutPlanDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * 灌入测试数据：
     * - 动作库：杠铃卧推（exercise_logs 的 exerciseKey 外键引用它）
     * - 训练 1（2026-07-20）：杠铃卧推 3 组（80x5, 80x5, 85x3）
     * - 训练 2（2026-07-21）：深蹲 1 组（100x5），exerciseKey 为空的纯名称记录
     */
    private suspend fun seedWorkoutData() {
        db.exerciseDao().insert(
            ExerciseEntity(
                id = "barbell-bench-press",
                name = "杠铃卧推",
                primaryMuscle = PrimaryMuscle.CHEST,
                difficulty = Difficulty.INTERMEDIATE,
                isCompound = true,
                equipment = Equipment.BARBELL,
            )
        )
        val w1 = db.workoutDao().insert(
            WorkoutEntity(
                date = LocalDate.of(2026, 7, 20),
                feelings = "状态不错",
                sourceFileName = null,
                rawContent = null,
            )
        )
        val e1 = db.exerciseLogDao().insert(
            ExerciseLogEntity(workoutId = w1, exerciseKey = "barbell-bench-press", name = "杠铃卧推", sortOrder = 0)
        )
        db.setLogDao().insertAll(
            listOf(
                SetLogEntity(exerciseLogId = e1, setNumber = 1, weightKg = 80f, reps = 5),
                SetLogEntity(exerciseLogId = e1, setNumber = 2, weightKg = 80f, reps = 5),
                SetLogEntity(exerciseLogId = e1, setNumber = 3, weightKg = 85f, reps = 3),
            )
        )
        val w2 = db.workoutDao().insert(
            WorkoutEntity(
                date = LocalDate.of(2026, 7, 21),
                feelings = null,
                sourceFileName = "2026-07-21.md",
                rawContent = null,
            )
        )
        val e2 = db.exerciseLogDao().insert(
            ExerciseLogEntity(workoutId = w2, exerciseKey = null, name = "深蹲", sortOrder = 0)
        )
        db.setLogDao().insertAll(
            listOf(SetLogEntity(exerciseLogId = e2, setNumber = 1, weightKg = 100f, reps = 5))
        )
    }

    // ── get_user_profile ──

    @Test
    fun `get_user_profile returns profile json`() = runTest {
        db.userProfileDao().insert(
            UserProfileEntity(
                name = "小明", age = 25, gender = "MALE",
                height = 175f, weight = 70f, trainingGoal = "HYPERTROPHY",
            )
        )

        val result = json.parseToJsonElement(
            GetUserProfileTool(userProfileRepository).execute(buildJsonObject {})
        ).jsonObject

        assertEquals("小明", result["name"]!!.jsonPrimitive.content)
        assertEquals("70.0", result["weightKg"]!!.jsonPrimitive.content)
        assertEquals("HYPERTROPHY", result["trainingGoal"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get_user_profile returns note when absent`() = runTest {
        val result = GetUserProfileTool(userProfileRepository).execute(buildJsonObject {})
        assertTrue(result.contains("用户未填写资料"))
    }

    // ── list_recent_workouts ──

    @Test
    fun `list_recent_workouts returns summaries ordered by date desc`() = runTest {
        seedWorkoutData()

        val result = json.parseToJsonElement(
            ListRecentWorkoutsTool(workoutRepository).execute(buildJsonObject {})
        ).jsonObject

        val results = result["results"]!!.jsonArray
        assertEquals(2, results.size)
        assertEquals("2026-07-21", results[0].jsonObject["date"]!!.jsonPrimitive.content)
        val benchPress = results[1].jsonObject["exercises"]!!.jsonArray[0].jsonObject
        assertEquals("杠铃卧推", benchPress["name"]!!.jsonPrimitive.content)
        assertEquals("85.0", benchPress["topWeightKg"]!!.jsonPrimitive.content)
    }

    @Test
    fun `list_recent_workouts respects limit`() = runTest {
        seedWorkoutData()

        val result = json.parseToJsonElement(
            ListRecentWorkoutsTool(workoutRepository).execute(buildJsonObject { put("limit", 1) })
        ).jsonObject

        assertEquals(1, result["results"]!!.jsonArray.size)
    }

    @Test
    fun `list_recent_workouts empty db returns note`() = runTest {
        val result = ListRecentWorkoutsTool(workoutRepository).execute(buildJsonObject {})
        assertTrue(result.contains("暂无训练记录"))
    }

    // ── get_workout_detail ──

    @Test
    fun `get_workout_detail by date returns set details`() = runTest {
        seedWorkoutData()

        val result = json.parseToJsonElement(
            GetWorkoutDetailTool(workoutRepository).execute(buildJsonObject { put("date", "2026-07-20") })
        ).jsonObject

        val sets = result["results"]!!.jsonArray[0].jsonObject["exercises"]!!
            .jsonArray[0].jsonObject["sets"]!!.jsonArray
        assertEquals(3, sets.size)
        assertEquals("85.0", sets[2].jsonObject["weightKg"]!!.jsonPrimitive.content)
        assertEquals("3", sets[2].jsonObject["reps"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get_workout_detail invalid date returns error`() = runTest {
        val result = GetWorkoutDetailTool(workoutRepository)
            .execute(buildJsonObject { put("date", "昨天") })
        assertTrue(result.contains("error"))
    }

    @Test
    fun `get_workout_detail without params returns error`() = runTest {
        val result = GetWorkoutDetailTool(workoutRepository).execute(buildJsonObject {})
        assertTrue(result.contains("error"))
    }

    // ── get_exercise_history ──

    @Test
    fun `get_exercise_history matches chinese name via LIKE fallback`() = runTest {
        seedWorkoutData()

        val result = json.parseToJsonElement(
            GetExerciseHistoryTool(workoutRepository).execute(buildJsonObject { put("exercise", "卧推") })
        ).jsonObject

        val results = result["results"]!!.jsonArray
        assertEquals(1, results.size)
        val bestSet = results[0].jsonObject["bestSet"]!!.jsonObject
        assertEquals("85.0", bestSet["weightKg"]!!.jsonPrimitive.content)
        assertTrue(result["note"]!!.jsonPrimitive.content.contains("85.0"))
    }

    @Test
    fun `get_exercise_history matches exercise key`() = runTest {
        seedWorkoutData()

        val result = json.parseToJsonElement(
            GetExerciseHistoryTool(workoutRepository)
                .execute(buildJsonObject { put("exercise", "barbell-bench-press") })
        ).jsonObject

        assertEquals(1, result["results"]!!.jsonArray.size)
    }

    @Test
    fun `get_exercise_history no match returns note`() = runTest {
        seedWorkoutData()

        val result = GetExerciseHistoryTool(workoutRepository)
            .execute(buildJsonObject { put("exercise", "硬拉") })

        assertTrue(result.contains("没有找到该动作的训练记录"))
    }

    // ── search_exercises ──

    @Test
    fun `search_exercises finds seeded exercise by name`() = runTest {
        seedWorkoutData()

        val result = json.parseToJsonElement(
            SearchExercisesTool(exerciseRepository).execute(buildJsonObject { put("query", "卧推") })
        ).jsonObject

        val results = result["results"]!!.jsonArray
        assertEquals(1, results.size)
        assertEquals("barbell-bench-press", results[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("CHEST", results[0].jsonObject["primaryMuscle"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search_exercises empty library returns note`() = runTest {
        val result = SearchExercisesTool(exerciseRepository).execute(buildJsonObject {})
        assertTrue(result.contains("动作库为空"))
    }

    // ── list_workout_plans / get_next_planned_session ──

    @Test
    fun `list_workout_plans empty returns note`() = runTest {
        val result = ListWorkoutPlansTool(workoutPlanRepository).execute(buildJsonObject {})
        assertTrue(result.contains("暂无训练计划"))
    }

    @Test
    fun `get_next_planned_session returns first incomplete session`() = runTest {
        db.workoutPlanDao().savePlanWithSessions(
            plan = WorkoutPlanEntity(
                id = "plan-1", name = "4周增肌计划", description = null,
                goal = "HYPERTROPHY", difficulty = null,
                durationWeeks = 4, sessionsPerWeek = 3,
                isCustom = false, createdAt = LocalDate.of(2026, 7, 1),
            ),
            sessions = listOf(
                PlannedSessionEntity(
                    id = "s1", planId = "plan-1", name = "Day 1 - 推日", description = null,
                    dayNumber = 1, weekNumber = 1, targetDurationMinutes = 60, completedWorkoutId = null,
                ),
                PlannedSessionEntity(
                    id = "s2", planId = "plan-1", name = "Day 2 - 拉日", description = null,
                    dayNumber = 2, weekNumber = 1, targetDurationMinutes = null, completedWorkoutId = null,
                ),
            ),
            exercises = listOf(
                PlannedExerciseEntity(
                    id = "pe1", sessionId = "s1", exerciseKey = "barbell-bench-press",
                    exerciseName = "杠铃卧推", targetSets = 4, targetRepsMin = 5, targetRepsMax = 8,
                    targetWeightKg = 80f, targetRpe = null, restSeconds = null, notes = null, order = 0,
                ),
            ),
        )

        val result = json.parseToJsonElement(
            GetNextPlannedSessionTool(workoutPlanRepository)
                .execute(buildJsonObject { put("planId", "plan-1") })
        ).jsonObject

        assertEquals("Day 1 - 推日", result["sessionName"]!!.jsonPrimitive.content)
        val exercises = result["exercises"]!!.jsonArray
        assertEquals("杠铃卧推", exercises[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("4", exercises[0].jsonObject["targetSets"]!!.jsonPrimitive.content)
        assertEquals("5-8", exercises[0].jsonObject["targetReps"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get_next_planned_session unknown plan returns error`() = runTest {
        val result = GetNextPlannedSessionTool(workoutPlanRepository)
            .execute(buildJsonObject { put("planId", "ghost") })
        assertTrue(result.contains("error"))
    }
}
