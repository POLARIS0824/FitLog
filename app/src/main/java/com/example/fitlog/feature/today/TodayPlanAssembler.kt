package com.example.fitlog.feature.today

import com.example.fitlog.model.Exercise
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.util.ExerciseDisplayName
import com.example.fitlog.util.VolumeFormatter

/**
 * 今日训练计划卡片（[TodayPlanState]）的组装器。
 *
 * 纯函数对象：由激活计划、下一个未完成训练日与今日训练记录推导卡片状态机。
 * 状态优先级：NO_PLAN →（计划全完成）COMPLETED →（今日已关联）COMPLETED
 * → IN_PROGRESS → NOT_STARTED。
 *
 * IN_PROGRESS 分支由训练执行流或本地打卡状态驱动。
 */
object TodayPlanAssembler {

    /**
     * 组装今日训练计划卡片状态。
     *
     * @param activePlan 当前激活计划（无则为 null）
     * @param nextSession 激活计划的下一个未完成训练日（全部完成为 null）
     * @param todayWorkouts 今日训练记录
     * @param allWorkouts 全部训练历史（用于提取动作最近使用重量）
     * @param catalog 动作库目录
     * @param checkedExerciseKeys 手动打卡选中的动作行唯一 key 集合
     *   （exerciseKey#order，见 [buildExerciseStates]）
     */
    fun assemble(
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession?,
        todayWorkouts: List<Workout>,
        allWorkouts: List<Workout> = emptyList(),
        catalog: List<Exercise> = emptyList(),
        checkedExerciseKeys: Set<String> = emptySet(),
    ): TodayPlanState {
        // 1. 无激活计划
        if (activePlan == null) {
            return TodayPlanState(
                tagText = "无计划",
                title = "还没有训练计划",
                subtitle = "选择一套计划开始系统训练",
                progress = 0f,
                status = PlanStatus.NO_PLAN,
                exercises = emptyList(),
            )
        }

        // 2. 计划全部训练日已完成
        if (nextSession == null) {
            return TodayPlanState(
                planId = activePlan.id,
                tagText = formatPlanTag(activePlan, null),
                title = activePlan.name,
                subtitle = "全部训练日已完成",
                progress = 1f,
                status = PlanStatus.COMPLETED,
                exercises = emptyList(),
            )
        }

        val tagText = formatPlanTag(activePlan, nextSession)
        val todayWorkoutIds = todayWorkouts.map { it.id }.toSet()
        val completedToday = activePlan.sessions.firstOrNull {
            it.completedWorkoutId != null && it.completedWorkoutId in todayWorkoutIds
        }

        // 3. 今日已关联完成
        if (completedToday != null) {
            val exerciseStates = buildExerciseStates(
                session = completedToday,
                allWorkouts = allWorkouts,
                checkedKeys = checkedExerciseKeys,
                isAllCompleted = true,
                inProgressWorkout = null,
            )
            return TodayPlanState(
                planId = activePlan.id,
                sessionId = completedToday.id,
                tagText = tagText,
                title = completedToday.name,
                subtitle = sessionSubtitle(completedToday),
                progress = 1f,
                workoutId = completedToday.completedWorkoutId,
                status = PlanStatus.COMPLETED,
                exercises = exerciseStates,
            )
        }

        // 4. 进行中 / 未开始
        val inProgressWorkout = todayWorkouts.firstOrNull { it.startedAt != null && it.endedAt == null }
        val exerciseStates = buildExerciseStates(
            session = nextSession,
            allWorkouts = allWorkouts,
            checkedKeys = checkedExerciseKeys,
            isAllCompleted = false,
            inProgressWorkout = inProgressWorkout,
        )

        val totalExercises = exerciseStates.size
        val completedExercises = exerciseStates.count { it.isCompleted }

        // 如果全部打卡完成
        if (totalExercises > 0 && completedExercises == totalExercises) {
            return TodayPlanState(
                planId = activePlan.id,
                sessionId = nextSession.id,
                tagText = tagText,
                title = nextSession.name,
                subtitle = sessionSubtitle(nextSession),
                progress = 1f,
                workoutId = inProgressWorkout?.id,
                status = PlanStatus.COMPLETED,
                exercises = exerciseStates,
            )
        }

        // 进行中状态（有部分打卡或已有进行中会话）
        if (inProgressWorkout != null || completedExercises > 0) {
            val progress = if (totalExercises > 0) {
                (completedExercises.toFloat() / totalExercises).coerceIn(0.01f, 0.99f)
            } else {
                0.01f
            }
            return TodayPlanState(
                planId = activePlan.id,
                sessionId = nextSession.id,
                tagText = tagText,
                title = nextSession.name,
                subtitle = sessionSubtitle(nextSession),
                progress = progress,
                workoutId = inProgressWorkout?.id,
                status = PlanStatus.IN_PROGRESS,
                exercises = exerciseStates,
            )
        }

        // 5. 未开始
        return TodayPlanState(
            planId = activePlan.id,
            sessionId = nextSession.id,
            tagText = tagText,
            title = nextSession.name,
            subtitle = sessionSubtitle(nextSession),
            progress = 0f,
            status = PlanStatus.NOT_STARTED,
            exercises = exerciseStates,
        )
    }

    /** 格式化训练分化/计划标签，如 "推拉腿 · 第3天"。 */
    private fun formatPlanTag(plan: WorkoutPlan, session: PlannedSession?): String {
        val cleanPlanName = plan.name
            .substringBefore(" · ")
            .substringBefore(" PPL")
            .trim()
        return if (session != null) {
            "$cleanPlanName · 第${session.dayNumber}天"
        } else {
            cleanPlanName
        }
    }

    /** 训练日副标题："6 个动作 · 60 分钟"（无目标时长时省略时长段）。 */
    private fun sessionSubtitle(session: PlannedSession): String {
        val base = "${session.exercises.size} 个动作"
        return session.targetDurationMinutes?.let { "$base · $it 分钟" } ?: base
    }

    /** 构建动作渲染项列表。 */
    private fun buildExerciseStates(
        session: PlannedSession,
        allWorkouts: List<Workout>,
        checkedKeys: Set<String>,
        isAllCompleted: Boolean,
        inProgressWorkout: Workout?,
    ): List<TodayPlanExerciseState> {
        return session.exercises
            .sortedBy { it.order }
            .map { item ->
                // 行唯一 key：计划课次允许同一动作出现多次（如同动作两个不同处方），
                // 以 exerciseKey 作行 id/打卡 key 会让一次点击同时勾掉两行
                val rowKey = exerciseRowKey(item)
                val isChecked = isAllCompleted ||
                    rowKey in checkedKeys ||
                    hasCompletedSetsInWorkout(inProgressWorkout, item.exerciseKey)

                val weightText = findRecentWeight(allWorkouts, item.exerciseKey, item.exerciseName)

                TodayPlanExerciseState(
                    id = rowKey,
                    exerciseKey = item.exerciseKey,
                    name = ExerciseDisplayName.getDisplayName(item.exerciseKey, item.exerciseName),
                    setsRepsText = formatSetsReps(item),
                    weightText = weightText,
                    isCompleted = isChecked,
                )
            }
    }

    /**
     * 动作行唯一 key（exerciseKey + 行序）：打卡集合与渲染 id 的统一寻址键。
     * exerciseKey 只用于动作匹配（进行中会话完成推导、历史重量查找、点击跳转）。
     */
    private fun exerciseRowKey(item: PlannedExerciseItem): String =
        "${item.exerciseKey}#${item.order}"

    /** 格式化组数与次数处方。 */
    private fun formatSetsReps(item: PlannedExerciseItem): String {
        val sets = item.targetSets
        val min = item.targetRepsMin
        val max = item.targetRepsMax
        return when {
            min != null && max != null && min == max -> "$sets 组 × $min"
            min != null && max != null -> "$sets 组 × $min-$max"
            min != null -> "$sets 组 × $min"
            max != null -> "$sets 组 × $max"
            else -> "$sets 组"
        }
    }

    /** 检查在进行中会话中该动作是否已有已录入正式组。 */
    private fun hasCompletedSetsInWorkout(workout: Workout?, exerciseKey: String): Boolean {
        if (workout == null) return false
        return workout.exercises.any { log ->
            log.exerciseKey == exerciseKey && log.sets.any { it.setType == SetType.WORKING && it.reps > 0 }
        }
    }

    /** 从历史记录中查找该动作最近使用的最大重量。 */
    private fun findRecentWeight(
        allWorkouts: List<Workout>,
        exerciseKey: String,
        exerciseName: String?,
    ): String? {
        val maxWeight = allWorkouts
            .asSequence()
            .sortedByDescending { it.date }
            .flatMap { it.exercises }
            .firstOrNull { it.exerciseKey == exerciseKey || (exerciseName != null && it.name.equals(exerciseName, ignoreCase = true)) }
            ?.sets
            ?.filter { it.setType == SetType.WORKING && it.weightKg > 0f }
            ?.maxOfOrNull { it.weightKg }

        return maxWeight?.let { "${VolumeFormatter.formatWeightKg(it)} kg" }
    }
}

