package com.example.fitlog.data.agent.tools

import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Workout
import com.example.fitlog.model.ai.AgentTool
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

// ───────────────────────────── 共享的 JSON 构造辅助 ─────────────────────────────

/** 无参数 tool 的空 JSON Schema。 */
private fun emptyParamsSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

/**
 * 训练记录 → 摘要 JSON（list 场景）：日期、动作数、各动作组数与最高重量。
 */
private fun Workout.toSummaryJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("date", date.toString())
    put("exerciseCount", exercises.size)
    putJsonArray("exercises") {
        exercises.forEach { log ->
            add(buildJsonObject {
                put("name", log.name)
                put("sets", log.sets.size)
                log.sets.maxOfOrNull { set -> set.weightKg }?.let { put("topWeightKg", it) }
            })
        }
    }
    feelings?.let { put("feelings", it) }
}

/**
 * 训练记录 → 明细 JSON（detail 场景）：每个动作的每组重量与次数。
 *
 * 未结构化的导入记录（exercises 为空）附带截断的 rawContent，
 * 让模型仍能从原始 Markdown 中读取信息。
 */
private fun Workout.toDetailJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("date", date.toString())
    putJsonArray("exercises") {
        exercises.forEach { log ->
            add(buildJsonObject {
                put("name", log.name)
                putJsonArray("sets") {
                    log.sets.forEach { set ->
                        add(buildJsonObject {
                            put("weightKg", set.weightKg)
                            put("reps", set.reps)
                        })
                    }
                }
            })
        }
    }
    feelings?.let { put("feelings", it) }
    rawContent?.let { put("rawContent", it.take(800)) }
}

// ───────────────────────────── 1. 用户档案 ─────────────────────────────

/**
 * 查询用户个人资料（姓名、年龄、性别、身高、体重、训练目标）。
 */
@Singleton
class GetUserProfileTool @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
) : AgentTool {

    override val name = "get_user_profile"

    override val description =
        "获取用户的个人资料（姓名、年龄、性别、身高、体重、训练目标）。回答涉及用户身体数据或训练目标的个性化问题前调用。"

    override val parametersSchema = emptyParamsSchema()

    override suspend fun execute(arguments: JsonObject): String {
        val profile = userProfileRepository.getFirst()
            ?: return """{"note": "用户未填写资料"}"""
        return buildJsonObject {
            put("name", profile.name)
            profile.age?.let { put("age", it) }
            profile.gender?.let { put("gender", it.name) }
            profile.height?.let { put("heightCm", it) }
            profile.weight?.let { put("weightKg", it) }
            profile.trainingGoal?.let { put("trainingGoal", it.name) }
        }.toString()
    }
}

// ───────────────────────────── 2. 最近训练摘要 ─────────────────────────────

/**
 * 查询最近 N 次训练的摘要列表。
 */
@Singleton
class ListRecentWorkoutsTool @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : AgentTool {

    override val name = "list_recent_workouts"

    override val description =
        "查询用户最近的训练记录摘要（日期、动作列表、各动作组数与最高重量）。用户问最近练了什么、训练频率、近期训练情况时使用。"

    override val parametersSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "返回最近几次训练，1-10，默认 5")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 10) ?: 5
        val workouts = workoutRepository.getRecentWorkouts(limit)
        if (workouts.isEmpty()) return """{"results": [], "note": "暂无训练记录"}"""
        return buildToolListResult(workouts.map { it.toSummaryJson() })
    }
}

// ───────────────────────────── 3. 单次训练明细 ─────────────────────────────

/**
 * 按日期或 ID 查询单次训练的组级明细。
 */
@Singleton
class GetWorkoutDetailTool @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : AgentTool {

    override val name = "get_workout_detail"

    override val description =
        "查询某一次训练的完整明细（每个动作的每组重量和次数）。需要精确分析单次训练时使用。参数 date（YYYY-MM-DD）与 workoutId 二选一。"

    override val parametersSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("date") {
                put("type", "string")
                put("description", "训练日期，格式 YYYY-MM-DD")
            }
            putJsonObject("workoutId") {
                put("type", "integer")
                put("description", "训练记录 ID（可由 list_recent_workouts 获得）")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val workoutId = arguments["workoutId"]?.jsonPrimitive?.longOrNull
        val dateStr = arguments["date"]?.jsonPrimitive?.contentOrNull
        val workouts: List<Workout> = when {
            workoutId != null -> listOfNotNull(workoutRepository.getWorkoutById(workoutId))
            dateStr != null -> {
                val date = try {
                    LocalDate.parse(dateStr)
                } catch (e: DateTimeParseException) {
                    return """{"error": "date 格式应为 YYYY-MM-DD"}"""
                }
                workoutRepository.getByDate(date).first()
            }

            else -> return """{"error": "需要提供 date 或 workoutId 参数"}"""
        }
        if (workouts.isEmpty()) return """{"results": [], "note": "没有找到对应的训练记录"}"""
        return buildToolListResult(workouts.map { it.toDetailJson() })
    }
}

// ───────────────────────────── 4. 动作历史表现 ─────────────────────────────

/**
 * 查询某个动作的历史表现（每次训练的组明细与历史最佳重量）。
 */
@Singleton
class GetExerciseHistoryTool @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : AgentTool {

    override val name = "get_exercise_history"

    override val description =
        "查询某个动作（如卧推、深蹲）的历史表现：每次训练的组数、重量、次数及历史最佳重量（PR）。用户问某个动作有没有进步、最高重量是多少时使用。exercise 参数支持动作名称或动作库 ID。"

    override val parametersSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("exercise") {
                put("type", "string")
                put("description", "动作名称（如「卧推」）或动作库 ID（如 barbell-bench-press）")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "返回最近几次包含该动作的训练，1-8，默认 5")
            }
        }
        putJsonArray("required") { add("exercise") }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val exercise = arguments["exercise"]?.jsonPrimitive?.contentOrNull?.trim()
        if (exercise.isNullOrEmpty()) return """{"error": "缺少必填参数 exercise"}"""
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 8) ?: 5

        val workouts = workoutRepository.getWorkoutsByExercise(exercise, limit)
        // 内部过滤：只保留匹配的 ExerciseLog，避免把整次训练的其他动作喂给模型
        val history = workouts.mapNotNull { workout ->
            val matched = workout.exercises.filter {
                it.exerciseKey == exercise || it.name.contains(exercise, ignoreCase = true)
            }
            if (matched.isEmpty()) null else (workout to matched)
        }
        if (history.isEmpty()) return """{"results": [], "note": "没有找到该动作的训练记录"}"""

        val allSets = history.flatMap { (_, logs) -> logs.flatMap { it.sets } }
        val pr = allSets.maxOfOrNull { it.weightKg }

        val results = history.map { (workout, logs) ->
            buildJsonObject {
                put("date", workout.date.toString())
                val sets = logs.flatMap { it.sets }
                putJsonArray("sets") {
                    sets.forEach { set ->
                        add(buildJsonObject {
                            put("weightKg", set.weightKg)
                            put("reps", set.reps)
                        })
                    }
                }
                sets.maxByOrNull { it.weightKg }?.let { best ->
                    putJsonObject("bestSet") {
                        put("weightKg", best.weightKg)
                        put("reps", best.reps)
                    }
                }
            }
        }
        return buildToolListResult(results, note = pr?.let { "历史最佳重量: ${it}kg" })
    }
}

// ───────────────────────────── 5. 动作库搜索 ─────────────────────────────

/**
 * 在动作库中搜索标准动作。
 */
@Singleton
class SearchExercisesTool @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) : AgentTool {

    override val name = "search_exercises"

    override val description =
        "在动作库中搜索标准动作，返回动作 ID、名称、主要肌群、器械、难度。推荐动作或解释动作前使用。可按名称模糊搜索或按主要肌群筛选。"

    override val parametersSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "动作名称关键词（模糊搜索）")
            }
            putJsonObject("primaryMuscle") {
                put("type", "string")
                put("description", "主要肌群筛选")
                putJsonArray("enum") {
                    listOf("CHEST", "BACK", "SHOULDERS", "ARMS", "LEGS", "CORE", "FULL_BODY")
                        .forEach { add(it) }
                }
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "返回数量上限，1-20，默认 10")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()
        val muscle = arguments["primaryMuscle"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 20) ?: 10

        val exercises = when {
            !query.isNullOrEmpty() -> exerciseRepository.searchByName(query)
            !muscle.isNullOrEmpty() -> exerciseRepository.getByPrimaryMuscle(muscle)
            else -> exerciseRepository.getAll()
        }.take(limit)

        // 动作库当前为空表（尚未播种），必须容忍并如实告知模型
        if (exercises.isEmpty()) return """{"results": [], "note": "动作库为空或未找到匹配动作"}"""
        return buildToolListResult(exercises.map { ex ->
            buildJsonObject {
                put("id", ex.id)
                put("name", ex.name)
                ex.primaryMuscle?.let { put("primaryMuscle", it.name) }
                ex.equipment?.let { put("equipment", it.name) }
                ex.difficulty?.let { put("difficulty", it.name) }
            }
        })
    }
}

// ───────────────────────────── 6. 训练计划列表 ─────────────────────────────

/**
 * 列出训练计划及完成进度。
 */
@Singleton
class ListWorkoutPlansTool @Inject constructor(
    private val workoutPlanRepository: WorkoutPlanRepository,
) : AgentTool {

    override val name = "list_workout_plans"

    override val description =
        "列出用户的训练计划及完成进度（总训练日数、已完成数）。用户问有什么训练计划、计划执行情况时使用。"

    override val parametersSchema = emptyParamsSchema()

    override suspend fun execute(arguments: JsonObject): String {
        val plans = workoutPlanRepository.getAllPlans().take(5)
        if (plans.isEmpty()) return """{"results": [], "note": "暂无训练计划"}"""
        return buildToolListResult(plans.map { plan ->
            buildJsonObject {
                put("id", plan.id)
                put("name", plan.name)
                plan.goal?.let { put("goal", it.name) }
                put("durationWeeks", plan.durationWeeks)
                put("sessionsPerWeek", plan.sessionsPerWeek)
                put("totalSessions", plan.sessions.size)
                put("completedSessions", plan.sessions.count { it.completedWorkoutId != null })
            }
        })
    }
}

// ───────────────────────────── 7. 下一个待完成训练日 ─────────────────────────────

/**
 * 查询计划中下一个待完成的训练日。
 */
@Singleton
class GetNextPlannedSessionTool @Inject constructor(
    private val workoutPlanRepository: WorkoutPlanRepository,
) : AgentTool {

    override val name = "get_next_planned_session"

    override val description =
        "查询训练计划中下一个待完成的训练日（含动作、目标组数、次数、重量）。用户问今天或下次该练什么时使用。planId 可通过 list_workout_plans 获得。"

    override val parametersSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("planId") {
                put("type", "string")
                put("description", "训练计划 ID（由 list_workout_plans 获得）")
            }
        }
        putJsonArray("required") { add("planId") }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val planId = arguments["planId"]?.jsonPrimitive?.contentOrNull
            ?: return """{"error": "缺少必填参数 planId"}"""
        val plan = workoutPlanRepository.getPlanById(planId)
            ?: return """{"error": "计划不存在: $planId"}"""
        val session = plan.sessions
            .sortedWith(compareBy({ it.weekNumber }, { it.dayNumber }))
            .firstOrNull { it.completedWorkoutId == null }
            ?: return """{"note": "计划「${plan.name}」已全部完成"}"""

        return buildJsonObject {
            put("planName", plan.name)
            put("sessionName", session.name)
            put("week", session.weekNumber)
            put("day", session.dayNumber)
            session.targetDurationMinutes?.let { put("targetDurationMinutes", it) }
            putJsonArray("exercises") {
                session.exercises.sortedBy { it.order }.forEach { ex ->
                    add(buildJsonObject {
                        put("name", ex.exerciseName ?: ex.exerciseKey)
                        put("targetSets", ex.targetSets)
                        if (ex.targetRepsMin != null || ex.targetRepsMax != null) {
                            put("targetReps", "${ex.targetRepsMin ?: ""}-${ex.targetRepsMax ?: ""}")
                        }
                        ex.targetWeightKg?.let { put("targetWeightKg", it) }
                        ex.notes?.let { put("notes", it) }
                    })
                }
            }
        }.toString()
    }
}
