package com.example.fitlog.feature.today

import com.example.fitlog.model.Exercise
import com.example.fitlog.model.PlannedExerciseItem
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.resolvedId
import com.example.fitlog.util.ExerciseDisplayName
import com.example.fitlog.util.VolumeAggregator
import com.example.fitlog.util.VolumeFormatter

/**
 * Today 训练主卡的纯状态组装器。
 *
 * 进行中会话优先于日期与计划状态，避免跨过零点或自由训练时主卡丢失当前会话。
 */
object TodayPlanAssembler {

    fun assemble(
        activePlan: WorkoutPlan?,
        nextSession: PlannedSession?,
        todayWorkouts: List<Workout>,
        allWorkouts: List<Workout> = emptyList(),
        catalog: List<Exercise> = emptyList(),
        checkedExerciseKeys: Set<String> = emptySet(),
    ): TodayPlanState {
        val inProgressWorkout = (todayWorkouts + allWorkouts)
            .asSequence()
            .distinctBy { it.id }
            .filter { it.startedAt != null && it.endedAt == null }
            .maxByOrNull { it.startedAt ?: Long.MIN_VALUE }

        if (inProgressWorkout != null) {
            val sourceSession = activePlan?.sessions?.firstOrNull {
                it.id == inProgressWorkout.planSessionId
            } ?: nextSession

            return if (activePlan != null && sourceSession != null) {
                buildPlannedInProgress(
                    plan = activePlan,
                    session = sourceSession,
                    workout = inProgressWorkout,
                    allWorkouts = allWorkouts,
                    checkedExerciseKeys = checkedExerciseKeys,
                )
            } else {
                buildFreeInProgress(inProgressWorkout)
            }
        }

        if (activePlan == null) {
            return TodayPlanState(
                tagText = "无计划",
                title = "还没有训练计划",
                subtitle = "选择一套计划开始系统训练",
                status = PlanStatus.NO_PLAN,
            )
        }

        val todayWorkoutIds = todayWorkouts.mapTo(mutableSetOf()) { it.id }
        val completedToday = activePlan.sessions.firstOrNull {
            it.completedWorkoutId != null && it.completedWorkoutId in todayWorkoutIds
        }
        val completedTodayWorkout = completedToday?.completedWorkoutId?.let { id ->
            todayWorkouts.firstOrNull { it.id == id }
        }
        if (completedToday != null && completedTodayWorkout?.endedAt != null) {
            val exercises = buildExerciseStates(
                session = completedToday,
                allWorkouts = allWorkouts,
                checkedKeys = checkedExerciseKeys,
                isAllCompleted = true,
                inProgressWorkout = null,
            )
            return TodayPlanState(
                planId = activePlan.id,
                sessionId = completedToday.id,
                tagText = formatPlanTag(activePlan, completedToday),
                title = completedToday.name,
                subtitle = sessionSubtitle(completedToday),
                progress = 1f,
                workoutId = completedTodayWorkout.id,
                status = PlanStatus.COMPLETED,
                exercises = exercises,
                completedWorkingSets = VolumeAggregator.workingSetCountOf(completedTodayWorkout),
                targetWorkingSets = exercises.sumOf { it.targetSets },
            )
        }

        val allPlanSessionsLinkedAsCompleted = activePlan.sessions.isNotEmpty() &&
            activePlan.sessions.all { it.completedWorkoutId != null }
        if (nextSession == null && allPlanSessionsLinkedAsCompleted) {
            return TodayPlanState(
                planId = activePlan.id,
                tagText = formatPlanTag(activePlan, null),
                title = activePlan.name,
                subtitle = "全部训练日已完成",
                progress = 1f,
                status = PlanStatus.COMPLETED,
            )
        }

        if (nextSession == null) {
            return TodayPlanState(
                planId = activePlan.id,
                tagText = formatPlanTag(activePlan, null),
                title = activePlan.name,
                subtitle = "暂无待执行训练日",
                status = PlanStatus.NOT_STARTED,
            )
        }

        val exercises = buildExerciseStates(
            session = nextSession,
            allWorkouts = allWorkouts,
            checkedKeys = checkedExerciseKeys,
            isAllCompleted = false,
            inProgressWorkout = null,
        )
        return TodayPlanState(
            planId = activePlan.id,
            sessionId = nextSession.id,
            tagText = formatPlanTag(activePlan, nextSession),
            title = nextSession.name,
            subtitle = sessionSubtitle(nextSession),
            status = PlanStatus.NOT_STARTED,
            exercises = exercises,
            targetWorkingSets = exercises.sumOf { it.targetSets },
        )
    }

    private fun buildPlannedInProgress(
        plan: WorkoutPlan,
        session: PlannedSession,
        workout: Workout,
        allWorkouts: List<Workout>,
        checkedExerciseKeys: Set<String>,
    ): TodayPlanState {
        val exercises = buildExerciseStates(
            session = session,
            allWorkouts = allWorkouts,
            checkedKeys = checkedExerciseKeys,
            isAllCompleted = false,
            inProgressWorkout = workout,
        )
        val targetSets = exercises.sumOf { it.targetSets }
        val cappedCompletedSets = exercises.sumOf { minOf(it.loggedWorkingSets, it.targetSets) }
        return TodayPlanState(
            planId = plan.id,
            sessionId = session.id,
            tagText = formatPlanTag(plan, session),
            title = session.name,
            subtitle = sessionSubtitle(session),
            progress = if (targetSets > 0) cappedCompletedSets.toFloat() / targetSets else 0f,
            workoutId = workout.id,
            status = PlanStatus.IN_PROGRESS,
            exercises = exercises,
            startedAtMs = workout.startedAt,
            completedWorkingSets = VolumeAggregator.workingSetCountOf(workout),
            targetWorkingSets = targetSets,
            nextSetText = nextSetText(workout),
        )
    }

    private fun buildFreeInProgress(workout: Workout): TodayPlanState = TodayPlanState(
        tagText = "自由训练 · 进行中",
        title = "自由训练",
        subtitle = "${workout.exercises.size} 个动作",
        workoutId = workout.id,
        status = PlanStatus.IN_PROGRESS,
        startedAtMs = workout.startedAt,
        completedWorkingSets = VolumeAggregator.workingSetCountOf(workout),
        nextSetText = nextSetText(workout),
    )

    private fun nextSetText(workout: Workout): String {
        workout.exercises.forEach { exercise ->
            val pendingSet = exercise.sets.firstOrNull { !it.isCompleted }
            if (pendingSet != null) {
                val name = ExerciseDisplayName.getDisplayName(exercise.exerciseKey, exercise.name)
                return if (pendingSet.reps > 0 && pendingSet.weightKg.isFinite() && pendingSet.weightKg >= 0f) {
                    "$name · 下一组 ${VolumeFormatter.formatWeightKg(pendingSet.weightKg)} kg × ${pendingSet.reps} 次"
                } else {
                    "$name · 下一组待填写"
                }
            }
        }
        return "所有已填写组均已完成"
    }

    private fun formatPlanTag(plan: WorkoutPlan, session: PlannedSession?): String {
        val cleanPlanName = plan.name.substringBefore(" · ").substringBefore(" PPL").trim()
        return if (session != null) "$cleanPlanName · 第${session.dayNumber}天" else cleanPlanName
    }

    private fun sessionSubtitle(session: PlannedSession): String {
        val base = "${session.exercises.size} 个动作"
        return session.targetDurationMinutes?.let { "$base · $it 分钟" } ?: base
    }

    private fun buildExerciseStates(
        session: PlannedSession,
        allWorkouts: List<Workout>,
        checkedKeys: Set<String>,
        isAllCompleted: Boolean,
        inProgressWorkout: Workout?,
    ): List<TodayPlanExerciseState> = session.exercises
        .sortedBy { it.order }
        .map { item ->
            val rowKey = exerciseRowKey(item)
            val loggedWorkingSets = if (isAllCompleted) {
                item.targetSets
            } else {
                countLoggedWorkingSets(inProgressWorkout, session, item)
            }
            TodayPlanExerciseState(
                id = rowKey,
                exerciseKey = item.exerciseKey,
                name = ExerciseDisplayName.getDisplayName(item.exerciseKey, item.exerciseName),
                setsRepsText = formatSetsReps(item),
                weightText = findRecentWeight(allWorkouts, item.exerciseKey, item.exerciseName),
                loggedWorkingSets = loggedWorkingSets,
                targetSets = item.targetSets,
                targetReached = isAllCompleted || (item.targetSets > 0 && loggedWorkingSets >= item.targetSets),
                manuallyChecked = rowKey in checkedKeys,
            )
        }

    private fun exerciseRowKey(item: PlannedExerciseItem): String = "${item.exerciseKey}#${item.order}"

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

    private fun countLoggedWorkingSets(
        workout: Workout?,
        session: PlannedSession,
        item: PlannedExerciseItem,
    ): Int {
        if (workout == null) return 0
        val resolvedId = item.resolvedId(session.id)
        return workout.exercises
            .filter { it.plannedExerciseId == resolvedId }
            .flatMap { it.sets }
            .count { it.isCompleted && it.setType == SetType.WORKING && it.reps > 0 }
    }

    private fun findRecentWeight(
        allWorkouts: List<Workout>,
        exerciseKey: String,
        exerciseName: String?,
    ): String? {
        val maxWeight = allWorkouts.asSequence()
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.endedAt ?: 0L })
            .flatMap { it.exercises }
            .firstOrNull {
                it.exerciseKey == exerciseKey ||
                    (exerciseName != null && it.name.equals(exerciseName, ignoreCase = true))
            }
            ?.sets
            ?.filter { it.isCompleted && it.setType == SetType.WORKING && it.weightKg > 0f }
            ?.maxOfOrNull { it.weightKg }
        return maxWeight?.let { "${VolumeFormatter.formatWeightKg(it)} kg" }
    }
}