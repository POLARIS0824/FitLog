package com.example.fitlog.feature.today

import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan

/**
 * 今日训练计划卡片（[TodayPlanState]）的组装器。
 *
 * 纯函数对象：由激活计划、下一个未完成训练日与今日训练记录推导卡片状态机。
 * 状态优先级：NO_PLAN →（计划全完成）COMPLETED →（今日已关联）COMPLETED
 * → IN_PROGRESS → NOT_STARTED。
 *
 * IN_PROGRESS 分支由训练执行流驱动：进行中的会话以 workouts 行
 * （startedAt 已写、endedAt 空）存在于 DB，见 WorkoutViewModel。
 */
object TodayPlanAssembler {

    /**
     * 组装今日训练计划卡片状态。
     *
     * @param activePlan 当前激活计划（无则为 null）
     * @param nextSession 激活计划的下一个未完成训练日（全部完成为 null）
     * @param todayWorkouts 今日训练记录
     */
    fun assemble(
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession?,
        todayWorkouts: List<Workout>,
    ): TodayPlanState {
        // 1. 无激活计划
        if (activePlan == null) {
            return TodayPlanState(
                title = "还没有训练计划",
                subtitle = "选择一套计划开始系统训练",
                progress = 0f,
                status = PlanStatus.NO_PLAN,
            )
        }

        // 2. 计划全部训练日已完成
        if (nextSession == null) {
            return TodayPlanState(
                planId = activePlan.id,
                title = activePlan.name,
                subtitle = "全部训练日已完成",
                progress = 1f,
                status = PlanStatus.COMPLETED,
            )
        }

        // 3. 今日已完成（某训练日关联的 workout 落在今天）
        val todayWorkoutIds = todayWorkouts.map { it.id }.toSet()
        val completedToday = activePlan.sessions.firstOrNull {
            it.completedWorkoutId != null && it.completedWorkoutId in todayWorkoutIds
        }
        if (completedToday != null) {
            return TodayPlanState(
                planId = activePlan.id,
                sessionId = completedToday.id,
                title = completedToday.name,
                subtitle = sessionSubtitle(completedToday),
                progress = 1f,
                workoutId = completedToday.completedWorkoutId,
                status = PlanStatus.COMPLETED,
            )
        }

        // 4. 进行中（训练执行流以 DB 为状态源，此分支可达）
        val inProgress = todayWorkouts.firstOrNull { it.startedAt != null && it.endedAt == null }
        if (inProgress != null) {
            // 只计已录入的正式组（reps>0）：占位组（0 次，与手动添加动作一致）
            // 不计入，否则刚开练进度就虚高（每个动作预置一组）
            val loggedSets = inProgress.exercises.sumOf { log ->
                log.sets.count { it.setType == SetType.WORKING && it.reps > 0 }
            }
            val targetSets = nextSession.exercises.sumOf { it.targetSets }.coerceAtLeast(1)
            return TodayPlanState(
                planId = activePlan.id,
                sessionId = nextSession.id,
                title = nextSession.name,
                subtitle = sessionSubtitle(nextSession),
                progress = (loggedSets.toFloat() / targetSets).coerceIn(0.01f, 0.99f),
                workoutId = inProgress.id,
                status = PlanStatus.IN_PROGRESS,
            )
        }

        // 5. 未开始
        return TodayPlanState(
            planId = activePlan.id,
            sessionId = nextSession.id,
            title = nextSession.name,
            subtitle = sessionSubtitle(nextSession),
            progress = 0f,
            status = PlanStatus.NOT_STARTED,
        )
    }

    /** 训练日副标题："6 个动作 · 60 分钟"（无目标时长时省略时长段）。 */
    private fun sessionSubtitle(session: PlannedSession): String {
        val base = "${session.exercises.size} 个动作"
        return session.targetDurationMinutes?.let { "$base · $it 分钟" } ?: base
    }
}
