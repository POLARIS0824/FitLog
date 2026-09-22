package com.example.fitlog.feature.workout

import com.example.fitlog.model.SetType

/**
 * 进行中训练会话的 UI 投影模型。
 *
 * 会话的**状态源是 DB**（workouts 行 startedAt 已写、endedAt 为空）：
 * [com.example.fitlog.data.repository.WorkoutRepository.getInProgressSessionSnapshot]
 * 流式给出包含主键的会话快照，进程死亡/页面销毁后重启仍可恢复；
 * 计划目标元数据（targetSets 等）不在 workouts 表中，由 ViewModel 按
 * planSessionId 从计划库补齐后合并进此投影。
 */

/**
 * 会话中的单个动作。
 *
 * @property logId exercise_logs 主键（组编辑操作按此寻址）
 * @property exerciseKey 动作库 id（kebab-case），计划动作必有
 * @property name 动作展示名
 * @property sortOrder 动作排序序号（新增动作取 max+1 用）
 * @property targetText 目标处方摘要（如 "4 组 × 8-10 次"），自由训练为 null
 * @property targetSets 计划目标组数，自由训练为 null
 * @property targetRepsMin 计划目标次数下限，自由训练为 null
 * @property targetRepsMax 计划目标次数上限，自由训练为 null
 * @property sets 已录入的组（按组号升序）
 */
data class ActiveSessionExercise(
    val logId: Long,
    val exerciseKey: String?,
    val name: String,
    val sortOrder: Int,
    val targetText: String?,
    val targetSets: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val sets: List<ActiveSessionSet>,
)

/**
 * 会话中的一组。
 *
 * @property id set_logs 主键（编辑/删除按此寻址）
 * @property setNumber 组号（新增组取 max+1 用）
 * @property weightKg 重量（kg）
 * @property reps 次数（0 = 尚未录入的占位行，结束训练时剔除）
 * @property setType 组类型（容量统计只计 WORKING 正式组）
 */
data class ActiveSessionSet(
    val id: Long,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val setType: SetType,
    val isCompleted: Boolean,
)

/**
 * 进行中的训练会话。
 *
 * @property workoutId workouts 行主键
 * @property startedAtMs 开始时间（epoch millis，UI 据此计算已练时长）
 * @property planSessionId 来源计划课次 id（自由训练为 null）
 * @property planSessionName 来源计划课次名（自由训练为 null）
 * @property exercises 动作清单（按 sortOrder 升序）
 */
data class ActiveSession(
    val workoutId: Long,
    val startedAtMs: Long,
    val planSessionId: String?,
    val planSessionName: String?,
    val exercises: List<ActiveSessionExercise>,
) {
    /** 已完成的有效正式组数。 */
    val loggedWorkingSets: Int
        get() = exercises.sumOf { exercise ->
            exercise.sets.count { it.isValidCompletedWorkingSet }
        }

    /** 已完成的正式组容量（kg），与全 App 容量口径一致。 */
    val loggedVolumeKg: Double
        get() = exercises.sumOf { exercise ->
            exercise.sets
                .filter { it.isValidCompletedWorkingSet }
                .sumOf { (it.weightKg * it.reps).toDouble() }
        }

    /** 是否存在可落库的有效训练内容（无则不能结束训练）。
     *
     * 仅热身组不算有效内容：纯热身会话保存后是一条 0 容量的可计数训练
     * （isCountable 只看 exercises 非空 + 已结束），会虚增 Today/Stats 的
     * 训练次数口径——至少要有一个已录次数的正式组。 */
    val hasLoggableContent: Boolean
        get() = exercises.any { exercise ->
            exercise.sets.any { it.isValidCompletedWorkingSet }
        }
}

private val ActiveSessionSet.isValidCompletedWorkingSet: Boolean
    get() = isCompleted && setType == SetType.WORKING && reps > 0 &&
        weightKg.isFinite() && weightKg >= 0f
