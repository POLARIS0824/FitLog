package com.example.fitlog.ui.settings.dataimport

import com.example.fitlog.model.Exercise
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import java.time.LocalDate

/**
 * 导入确认环节的可编辑明细模型（AI 解析结果的编辑缓冲）。
 *
 * 编辑期间不落库：所有改动只写回 UI 状态，确认导入时经
 * [ImportDraftWorkout.toWorkout] 转回 domain [Workout]（丢弃 reps≤0 占位组、
 * 剔除清洗后无组的动作，口径与 finishSession 清洗一致）。
 *
 * localId 为会话内自增标识（由 ViewModel 分配），供 Compose 文本框
 * `remember(localId)` 隔离输入，与数据库主键无关。
 */

/**
 * 单条导入项的可编辑草稿。
 *
 * 日期不在此列——由扫描器按文件名/节标题确定，不可编辑（写入时取自扫描项）。
 *
 * @param feelings 训练感受/备注（编辑框直接绑定，空串视为无）
 * @param startedAt 训练开始时间（epoch millis）；原文没有则 null
 * @param endedAt 训练结束时间（epoch millis）；解析缺省时已由解析层填当日 23:59
 * @param exercises 动作明细
 */
data class ImportDraftWorkout(
    val feelings: String = "",
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val exercises: List<ImportDraftExercise> = emptyList(),
) {
    /**
     * 有效动作数（含 ≥1 个 reps>0 的组）——确认导入的完整性要求：
     * 0 表示清洗后没有任何可导入的明细，该条只能走仅存档兜底。
     */
    val validExerciseCount: Int
        get() = exercises.count { it.hasValidSet }

    /**
     * 转回 domain [Workout]（确认导入时调用）。
     *
     * 清洗规则：reps≤0 的占位组丢弃；清洗后无组的动作剔除。结果可能为空动作
     * 列表，调用方须先校验 [validExerciseCount]（与仅存档兜底的分支互斥）。
     *
     * @param date 训练日期（取自扫描项，编辑不可改）
     * @param sourceKey 入库唯一键（workouts.sourceFileName）
     * @param rawContent 预处理后的原文（存档与 Agent 下游消费用）
     */
    fun toWorkout(date: LocalDate, sourceKey: String, rawContent: String): Workout = Workout(
        id = 0,
        userId = 0,
        date = date,
        feelings = feelings.takeIf { it.isNotBlank() },
        startedAt = startedAt,
        endedAt = endedAt,
        exercises = exercises.mapNotNull { exercise ->
            val sets = exercise.sets.filter { it.reps > 0 }
            if (sets.isEmpty()) return@mapNotNull null
            ExerciseLog(
                name = exercise.name,
                exerciseKey = exercise.exerciseKey,
                sets = sets.map { SetLog(it.weightKg, it.reps, it.setType) },
            )
        },
        sourceFileName = sourceKey,
        rawContent = rawContent,
    )

    companion object {
        /**
         * 由 AI 解析结果构造初始草稿。
         *
         * @param workout 解析层的输出（domain Workout，id=0）
         * @param newId 会话内自增 id 发生器（[ImportDraftExercise.localId] 等使用）
         */
        fun from(workout: Workout, newId: () -> Long): ImportDraftWorkout = ImportDraftWorkout(
            feelings = workout.feelings.orEmpty(),
            startedAt = workout.startedAt,
            endedAt = workout.endedAt,
            exercises = workout.exercises.map { log ->
                ImportDraftExercise(
                    localId = newId(),
                    name = log.name,
                    exerciseKey = log.exerciseKey,
                    sets = log.sets.map { set ->
                        ImportDraftSet(
                            localId = newId(),
                            weightKg = set.weightKg,
                            reps = set.reps,
                            setType = set.setType,
                        )
                    },
                )
            },
        )
    }
}

/**
 * 草稿中的单个动作。
 *
 * @param localId 会话内标识（文本框 remember 隔离）
 * @param name 动作名（可编辑；保存时全量重跑动作库匹配）
 * @param exerciseKey 匹配到的动作库 key；null = 未关联（自由文本入库，合法）
 * @param sets 组列表
 */
data class ImportDraftExercise(
    val localId: Long,
    val name: String,
    val exerciseKey: String? = null,
    val sets: List<ImportDraftSet> = emptyList(),
) {
    /** 是否含 ≥1 个 reps>0 的有效组（占位组不计，与容量统计口径一致）。 */
    val hasValidSet: Boolean
        get() = sets.any { it.reps > 0 }

    companion object {
        /**
         * 编辑弹层「添加动作」后从动作库构造新动作行（带一个待录入的占位组，
         * 与训练会话页 addExerciseWithPlaceholderSet 的交互一致）。
         */
        fun fromCatalog(exercise: Exercise, newId: () -> Long): ImportDraftExercise =
            ImportDraftExercise(
                localId = newId(),
                name = exercise.name,
                exerciseKey = exercise.id,
                sets = listOf(
                    ImportDraftSet(localId = newId(), weightKg = 0f, reps = 0, setType = SetType.WORKING),
                ),
            )
    }
}

/**
 * 草稿中的单组训练。
 *
 * @param localId 会话内标识（文本框 remember 隔离）
 * @param weightKg 重量 kg（自重为 0）
 * @param reps 次数；0 = 占位组（导入清洗时丢弃）
 * @param setType 组类型
 */
data class ImportDraftSet(
    val localId: Long,
    val weightKg: Float,
    val reps: Int,
    val setType: SetType,
)

/**
 * 草稿动作明细 → domain [ExerciseLog] 列表（不做清洗）。
 *
 * 供确认列表的摘要/明细展示统一走 [com.example.fitlog.util.VolumeAggregator]
 * 口径（正式组数/容量），避免为草稿单独手写第二份统计逻辑。
 */
fun ImportDraftWorkout.toExerciseLogs(): List<ExerciseLog> =
    exercises.map { exercise ->
        ExerciseLog(
            name = exercise.name,
            exerciseKey = exercise.exerciseKey,
            sets = exercise.sets.map { SetLog(it.weightKg, it.reps, it.setType) },
        )
    }
