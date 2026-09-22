package com.example.fitlog.model

/**
 * 进行中训练会话的会话态快照（训练执行流专用域模型）。
 *
 * 之所以不直接用 [Workout]：会话内的组编辑（改重量/删组/翻组类型）需要
 * exercise_logs / set_logs 的数据库主键来定位行，而 [Workout] 的子模型不携带
 * id。仓库以本快照为出口，feature 层无需感知 Room relation 类型。
 *
 * @property workoutId workouts 行主键
 * @property startedAtMs 开始时间（epoch millis；进行中行必非空）
 * @property planSessionId 来源计划课次 id（自由训练为 null）
 * @property exercises 动作清单（按 sortOrder 升序）
 */
data class WorkoutSessionSnapshot(
    val workoutId: Long,
    val startedAtMs: Long,
    val planSessionId: String?,
    val exercises: List<ExerciseSnapshot>,
) {
    /**
     * 会话中的单个动作。
     *
     * @property logId exercise_logs 主键（组编辑/删除按此寻址）
     * @property exerciseKey 动作库 id（kebab-case），计划动作必有
     * @property name 动作展示名
     * @property sortOrder 动作排序序号（新增动作取 max+1，删除中间动作后不重复）
     * @property plannedExerciseId 关联计划动作项稳定标识，自由训练或手动追加为 null
     * @property sets 已录入的组（按组号升序）
     */
    data class ExerciseSnapshot(
        val logId: Long,
        val exerciseKey: String?,
        val name: String,
        val sortOrder: Int,
        val plannedExerciseId: String? = null,
        val sets: List<SetSnapshot>,
    )

    /**
     * 会话中的一组。
     *
     * @property id set_logs 主键（编辑/删除按此寻址）
     * @property setNumber 组号（新增组取 max+1，删除中间组后不重复）
     * @property weightKg 重量（kg）
     * @property reps 次数（0 = 尚未录入的占位行，结束训练时剔除）
     * @property setType 组类型（容量统计只计 WORKING 正式组）
     */
    data class SetSnapshot(
        val id: Long,
        val setNumber: Int,
        val weightKg: Float,
        val reps: Int,
        val setType: SetType,
        val isCompleted: Boolean,
    )
}
