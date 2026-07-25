package com.example.fitlog.data.seed

import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.TrainingGoal
import java.time.LocalDate

/**
 * 预置训练计划（`isCustom = false`）。
 *
 * 以 Kotlin 代码构建而非 JSON asset：仅 2 套计划，无运行时编辑需求，
 * 享受编译期类型检查，省去 seed DTO + mapper 一整套。
 *
 * 所有 exerciseKey 必须存在于动作库种子（`res/raw/exercises.json`）中，
 * 由 `PresetPlansTest`（单测断言）与 [WorkoutPlanSeeder]（写入前校验）双层保证。
 * `exerciseName` 填动作库英文名做缓存，免 join 即可显示。
 */
object PresetPlans {

    /**
     * 预置计划的创建日期。
     *
     * 种子写入时刻不确定，固定一个发布日期即可（仅用于计划列表排序）。
     * 计划内容更新时同步更新此日期与 [WorkoutPlanSeeder] 的版本号。
     */
    private val PRESET_CREATED_AT: LocalDate = LocalDate.of(2026, 7, 24)

    /**
     * 返回全部预置训练计划。
     */
    fun all(): List<WorkoutPlan> = listOf(fullBody4Weeks(), ppl4Weeks())

    // ──────────────────────────────────────
    // 计划 A：新手全身分化 · 4 周
    // ──────────────────────────────────────

    /** 课 A：下肢 + 推。 */
    private val fullBodyWorkoutA = listOf(
        ex("barbell-full-squat", "Barbell full squat", sets = 4, min = 6, max = 8, order = 0),
        ex("barbell-bench-press", "Barbell bench press", sets = 4, min = 6, max = 8, order = 1),
        ex("barbell-seated-overhead-press", "Barbell seated overhead press", sets = 3, min = 8, max = 10, order = 2),
        ex("lever-leg-extension", "Lever leg extension", sets = 3, min = 10, max = 12, order = 3),
        ex("weighted-front-plank", "Weighted front plank", sets = 3, min = null, max = null, order = 4, notes = "每组 30-60 秒"),
    )

    /** 课 B：拉 + 铰链。 */
    private val fullBodyWorkoutB = listOf(
        ex("barbell-deadlift", "Barbell deadlift", sets = 3, min = 5, max = 5, order = 0),
        ex("barbell-bent-over-row", "Barbell bent over row", sets = 4, min = 6, max = 8, order = 1),
        ex("assisted-pull-up", "Assisted pull-up", sets = 3, min = 6, max = 10, order = 2),
        ex("dumbbell-lateral-raise", "Dumbbell lateral raise", sets = 3, min = 12, max = 15, order = 3),
        ex("barbell-curl", "Barbell curl", sets = 3, min = 10, max = 12, order = 4),
    )

    /**
     * 新手全身分化：A/B 两课交替，每周 3 练（奇数周 A/B/A，偶数周 B/A/B），共 12 个训练日。
     */
    private fun fullBody4Weeks(): WorkoutPlan {
        val planId = "plan-preset-fullbody-4wk"
        val sessions = (1..4).flatMap { week ->
            val sequence = if (week % 2 == 1) listOf('A', 'B', 'A') else listOf('B', 'A', 'B')
            sequence.mapIndexed { index, type ->
                val day = index + 1
                if (type == 'A') {
                    session(planId, week, day, "课 A · 下肢 + 推", fullBodyWorkoutA)
                } else {
                    session(planId, week, day, "课 B · 拉 + 铰链", fullBodyWorkoutB)
                }
            }
        }
        return WorkoutPlan(
            id = planId,
            name = "新手全身分化 · 4 周",
            description = "A/B 两课交替的全身训练，每周 3 练，覆盖全部主要肌群，适合建立动作模式与训练习惯。",
            goal = TrainingGoal.HYPERTROPHY,
            durationWeeks = 4,
            sessionsPerWeek = 3,
            isCustom = false,
            createdAt = PRESET_CREATED_AT,
            rawPlanText = null,
            sessions = sessions,
        )
    }

    // ──────────────────────────────────────
    // 计划 B：推拉腿 PPL · 4 周
    // ──────────────────────────────────────

    /** 推日：胸 + 肩 + 三头。 */
    private val pplPushDay = listOf(
        ex("barbell-bench-press", "Barbell bench press", sets = 4, min = 6, max = 8, order = 0),
        ex("dumbbell-incline-bench-press", "Dumbbell incline bench press", sets = 3, min = 8, max = 10, order = 1),
        ex("dumbbell-seated-shoulder-press", "Dumbbell seated shoulder press", sets = 3, min = 8, max = 10, order = 2),
        ex("cable-lateral-raise", "Cable lateral raise", sets = 3, min = 12, max = 15, order = 3),
        ex("cable-pushdown", "Cable pushdown", sets = 3, min = 10, max = 12, order = 4),
    )

    /** 拉日：背 + 二头。 */
    private val pplPullDay = listOf(
        ex("pull-up", "Pull-up", sets = 4, min = null, max = null, order = 0, notes = "自重，力竭前 1-2 次停"),
        ex("barbell-bent-over-row", "Barbell bent over row", sets = 4, min = 6, max = 8, order = 1),
        ex("cable-low-seated-row", "Cable low seated row", sets = 3, min = 8, max = 10, order = 2),
        ex("ez-barbell-curl", "Ez barbell curl", sets = 3, min = 10, max = 12, order = 3),
        ex("barbell-lying-triceps-extension-skull-crusher", "Barbell lying triceps extension skull crusher", sets = 3, min = 10, max = 12, order = 4),
    )

    /** 腿日：股四头 + 后侧链 + 小腿。 */
    private val pplLegDay = listOf(
        ex("barbell-full-squat", "Barbell full squat", sets = 4, min = 6, max = 8, order = 0),
        ex("barbell-romanian-deadlift", "Barbell romanian deadlift", sets = 3, min = 8, max = 10, order = 1),
        ex("smith-leg-press", "Smith leg press", sets = 3, min = 10, max = 12, order = 2),
        ex("lever-lying-leg-curl", "Lever lying leg curl", sets = 3, min = 10, max = 12, order = 3),
        ex("lever-standing-calf-raise", "Lever standing calf raise", sets = 4, min = 12, max = 15, order = 4),
        ex("hanging-leg-raise", "Hanging leg raise", sets = 3, min = 10, max = 15, order = 5),
    )

    /**
     * 推拉腿 PPL：推 / 拉 / 腿各一次 × 4 周，共 12 个训练日。
     */
    private fun ppl4Weeks(): WorkoutPlan {
        val planId = "plan-preset-ppl-4wk"
        val days = listOf(
            Triple("推日 · 胸肩三头", pplPushDay, "胸、肩、肱三头肌"),
            Triple("拉日 · 背二头", pplPullDay, "背、肱二头肌"),
            Triple("腿日 · 股四头后侧链", pplLegDay, "股四头肌、后侧链、小腿"),
        )
        val sessions = (1..4).flatMap { week ->
            days.mapIndexed { index, (name, exercises, muscles) ->
                session(
                    planId = planId,
                    week = week,
                    day = index + 1,
                    name = name,
                    exercises = exercises,
                    description = "目标肌群：$muscles",
                )
            }
        }
        return WorkoutPlan(
            id = planId,
            name = "推拉腿 PPL · 4 周",
            description = "推 / 拉 / 腿经典三分化，每周 3 练，单肌群刺激更集中，适合有一定动作基础的训练者。",
            goal = TrainingGoal.HYPERTROPHY,
            durationWeeks = 4,
            sessionsPerWeek = 3,
            isCustom = false,
            createdAt = PRESET_CREATED_AT,
            rawPlanText = null,
            sessions = sessions,
        )
    }

    // ──────────────────────────────────────
    // 构建辅助
    // ──────────────────────────────────────

    private fun ex(
        key: String,
        name: String,
        sets: Int,
        min: Int?,
        max: Int?,
        order: Int,
        notes: String? = null,
    ) = PlannedExerciseItem(
        exerciseKey = key,
        exerciseName = name,
        targetSets = sets,
        targetRepsMin = min,
        targetRepsMax = max,
        notes = notes,
        order = order,
    )

    private fun session(
        planId: String,
        week: Int,
        day: Int,
        name: String,
        exercises: List<PlannedExerciseItem>,
        description: String? = null,
        minutes: Int? = 60,
    ) = PlannedSession(
        id = "$planId-w$week-d$day",
        name = name,
        description = description,
        dayNumber = day,
        weekNumber = week,
        targetDurationMinutes = minutes,
        exercises = exercises,
    )
}
