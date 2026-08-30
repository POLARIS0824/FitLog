package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.ExerciseLogWithSets
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout

// 注意：刻意不提供 WorkoutEntity → Workout 的单实体映射——它会静默丢弃
// exercises（信息损失在类型上不可见），曾被 getLatest 误用导致「最近训练」
// 部位推导永久失效。单实体数据一律经 WorkoutWithExerciseLogs（可空列表）映射。

fun WorkoutWithExerciseLogs.toModel(): Workout {
    return Workout(
        id = workout.id,
        userId = workout.userId,
        date = workout.date,
        // @Relation 不支持 orderBy，Room 内部查询序非 API 契约：
        // 映射时按持久化的 sortOrder/setNumber 显式排序，保证读出顺序稳定
        exercises = exerciseLogs
            .sortedBy { it.exerciseLog.sortOrder }
            .map { it.toModel() },
        feelings = workout.feelings,
        startedAt = workout.startedAt,
        endedAt = workout.endedAt,
        sourceFileName = workout.sourceFileName,
        rawContent = workout.rawContent,
    )
}

fun ExerciseLogWithSets.toModel(): ExerciseLog {
    return ExerciseLog(
        name = exerciseLog.name,
        exerciseKey = exerciseLog.exerciseKey,
        sets = sets.sortedBy { it.setNumber }.map {
            SetLog(
                weightKg = it.weightKg,
                reps = it.reps,
                // 容错：库中非法枚举字符串按正式组处理（同 ThemeMode 容错风格）
                setType = runCatching { SetType.valueOf(it.setType) }
                    .getOrDefault(SetType.WORKING),
            )
        },
    )
}

fun Workout.toEntity(): WorkoutEntity {
    return WorkoutEntity(
        id = id,
        userId = userId,
        date = date,
        feelings = feelings,
        startedAt = startedAt,
        endedAt = endedAt,
        sourceFileName = sourceFileName,
        rawContent = rawContent,
    )
}

/**
 * 领域模型 [ExerciseLog] → 数据库实体。
 *
 * @param workoutId 所属训练日的数据库主键（级联插入时由 workout 插入返回值提供）
 * @param sortOrder 动作在当天的排序序号
 */
fun ExerciseLog.toEntity(workoutId: Long, sortOrder: Int): ExerciseLogEntity {
    return ExerciseLogEntity(
        workoutId = workoutId,
        exerciseKey = exerciseKey,
        name = name,
        sortOrder = sortOrder,
    )
}

/**
 * 领域模型 [SetLog] → 数据库实体。
 *
 * @param exerciseLogId 所属动作记录的数据库主键（级联插入时由 exerciseLog 插入返回值提供）
 * @param setNumber 组号（1-based）
 */
fun SetLog.toEntity(exerciseLogId: Long, setNumber: Int): SetLogEntity {
    return SetLogEntity(
        exerciseLogId = exerciseLogId,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        setType = setType.name,
    )
}